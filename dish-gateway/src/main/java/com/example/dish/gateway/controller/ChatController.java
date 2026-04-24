package com.example.dish.gateway.controller;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.observability.GatewayExecutionTracing;
import com.example.dish.gateway.service.AgentDispatchService;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.service.ResponseAggregator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.example.dish.gateway.config.TraceIdFilter.TRACE_HEADER;

/**
 * 聊天主链路控制器。
 * 负责接收用户 HTTP 请求，并串起 routing、planner、policy、agent dispatch、memory summary 和 execution runtime 事件写入。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String STORE_HEADER = "X-Store-Id";

    @Resource
    private RoutingAgent routingAgent;
    @Resource
    private AgentDispatchService agentDispatchService;
    @Resource
    private ResponseAggregator responseAggregator;
    @Resource
    private OrchestrationControlService orchestrationControlService;
    @Resource
    private ExecutionEventPublisher executionEventPublisher;

    @PostMapping("/process")
    public GatewayResponse process(@RequestBody ChatRequest request, HttpServletRequest httpServletRequest) {
        // 1. 先校验请求消息，并归一化 sessionId / storeId / traceId。
        String userInput = request.getMessage();
        if (userInput == null || userInput.trim().isEmpty()) {
            GatewayResponse response = new GatewayResponse();
            response.setSuccess(false);
            response.setContent("消息不能为空");
            return response;
        }

        String sessionId = request.getSessionId() != null && !request.getSessionId().isEmpty()
                ? request.getSessionId()
                : generateSessionId();
        String requestStoreId = httpServletRequest.getHeader(STORE_HEADER);
        String traceId = httpServletRequest.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = "TRC-GW-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // 2. 通过 RoutingAgent 生成路由决策，再由控制面生成执行步骤和初始 graph。
        RoutingDecision routing = routingAgent.route(userInput, sessionId, requestStoreId, null);
        log.info("gateway dispatch: sessionId={}, targetAgent={}, intent={}",
                routing.context().getSessionId(), routing.targetAgent(), routing.intent());
        List<AgentExecutionStep> plannedSteps = orchestrationControlService.planSteps(routing, traceId);
        ExecutionGraphViewResult graph = executionEventPublisher.startExecution(routing, plannedSteps, traceId);
        Instant executionStartedAt = graph != null && graph.startedAt() != null ? graph.startedAt() : Instant.now();

        // 3. 按步骤串行执行：逐步处理审批、策略阻断、RPC 派发和 runtime 事件写入。
        List<AgentResponse> responses = new ArrayList<>();
        int executedSteps = 0;
        int stepCount = plannedSteps.size();

        for (int i = 0; i < plannedSteps.size(); i++) {
            AgentExecutionStep step = plannedSteps.get(i);
            int stepIndex = i + 1;

            AgentExecutionStep approvalStep = orchestrationControlService.findFirstApprovalRequiredStep(List.of(step), routing, traceId);
            if (approvalStep != null) {
                String approvalId = orchestrationControlService.createApprovalTicket(routing, graph.executionId(), approvalStep, traceId);
                executionEventPublisher.publishNodeStatus(
                        graph,
                        approvalStep,
                        ExecutionNodeStatus.WAITING_APPROVAL,
                        stepIndex,
                        stepCount,
                        traceId,
                        "manual approval required before execution",
                        elapsedSince(executionStartedAt),
                        null,
                        approvalId
                );
                GatewayResponse pendingResponse = orchestrationControlService.buildApprovalPendingResponse(routing, traceId, approvalId);
                orchestrationControlService.writeExecutionSummary(routing, executedSteps, false, traceId);
                executionEventPublisher.publishExecutionSummary(
                        graph,
                        ExecutionNodeStatus.WAITING_APPROVAL,
                        traceId,
                        "execution paused waiting for approval",
                        elapsedSince(executionStartedAt),
                        executedSteps
                );
                return pendingResponse;
            }

            List<AgentExecutionStep> allowed = orchestrationControlService.filterAllowedSteps(List.of(step), routing, traceId);
            if (allowed.isEmpty()) {
                executionEventPublisher.publishNodeStatus(
                        graph,
                        step,
                        ExecutionNodeStatus.CANCELLED,
                        stepIndex,
                        stepCount,
                        traceId,
                        "policy engine blocked this step",
                        0L,
                        null,
                        null
                );
                cancelPendingSteps(graph, plannedSteps, i + 1, traceId, "cancelled after policy block");
                GatewayResponse blockedResponse = orchestrationControlService.buildPolicyBlockedResponse(routing, traceId);
                orchestrationControlService.writeExecutionSummary(routing, executedSteps, false, traceId);
                executionEventPublisher.publishExecutionSummary(
                        graph,
                        ExecutionNodeStatus.CANCELLED,
                        traceId,
                        "execution blocked by policy",
                        elapsedSince(executionStartedAt),
                        executedSteps
                );
                return blockedResponse;
            }

            executionEventPublisher.publishNodeStatus(
                    graph,
                    step,
                    ExecutionNodeStatus.RUNNING,
                    stepIndex,
                    stepCount,
                    traceId,
                    "step execution started",
                    0L,
                    null,
                    null
            );

            long stepStartedAt = System.currentTimeMillis();
            AgentResponse response;
            long latencyMs;
            try (GatewayExecutionTracing.SpanScope spanScope =
                         GatewayExecutionTracing.openExecutionSpan("gateway.step.dispatch", routing, graph, step, traceId)) {
                try {
                    response = agentDispatchService.dispatchStep(routing, step);
                } catch (RuntimeException ex) {
                    spanScope.recordFailure(ex);
                    throw ex;
                }
                latencyMs = System.currentTimeMillis() - stepStartedAt;
                spanScope.span().setAttribute("app.step_index", stepIndex);
                spanScope.span().setAttribute("app.step_count", stepCount);
                spanScope.span().setAttribute("app.latency_ms", latencyMs);
                spanScope.span().setAttribute("app.agent_success", response.isSuccess());
                if (response.getContent() != null) {
                    spanScope.span().setAttribute("app.response_preview", abbreviate(response.getContent()));
                }
            }
            responses.add(response);
            executedSteps++;

            if (response.isSuccess()) {
                executionEventPublisher.publishNodeStatus(
                        graph,
                        step,
                        ExecutionNodeStatus.SUCCEEDED,
                        stepIndex,
                        stepCount,
                        traceId,
                        "step completed successfully",
                        latencyMs,
                        response,
                        null
                );
                continue;
            }

            executionEventPublisher.publishNodeStatus(
                    graph,
                    step,
                    ExecutionNodeStatus.FAILED,
                    stepIndex,
                    stepCount,
                    traceId,
                    "agent degraded or execution failed",
                    latencyMs,
                    response,
                    null
            );
            cancelPendingSteps(graph, plannedSteps, i + 1, traceId, "cancelled after upstream failure");

            GatewayResponse failedResponse = responseAggregator.aggregate(responses, routing);
            orchestrationControlService.writeExecutionSummary(routing, executedSteps, false, traceId);
            executionEventPublisher.publishExecutionSummary(
                    graph,
                    ExecutionNodeStatus.FAILED,
                    traceId,
                    "execution failed during step dispatch",
                    elapsedSince(executionStartedAt),
                    executedSteps
            );
            return failedResponse;
        }

        // 4. 所有步骤完成后聚合响应，并写执行摘要/summary 事件。
        GatewayResponse finalResponse = responseAggregator.aggregate(responses, routing);
        orchestrationControlService.writeExecutionSummary(
                routing,
                executedSteps,
                finalResponse.isSuccess(),
                traceId
        );
        executionEventPublisher.publishExecutionSummary(
                graph,
                finalResponse.isSuccess() ? ExecutionNodeStatus.SUCCEEDED : ExecutionNodeStatus.FAILED,
                traceId,
                finalResponse.getContent(),
                elapsedSince(executionStartedAt),
                executedSteps
        );

        return finalResponse;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "dish-gateway");
    }


    private String generateSessionId() {
        return "SESSION_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void cancelPendingSteps(ExecutionGraphViewResult graph,
                                    List<AgentExecutionStep> steps,
                                    int startIndex,
                                    String traceId,
                                    String reason) {
        for (int i = startIndex; i < steps.size(); i++) {
            AgentExecutionStep pending = steps.get(i);
            executionEventPublisher.publishNodeStatus(
                    graph,
                    pending,
                    ExecutionNodeStatus.CANCELLED,
                    i + 1,
                    steps.size(),
                    traceId,
                    reason,
                    0L,
                    null,
                    null
            );
        }
    }

    private long elapsedSince(Instant startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt.toEpochMilli());
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 160) {
            return value;
        }
        return value.substring(0, 157) + "...";
    }

    /**
     * 聊天请求体。
     */
    public static class ChatRequest {
        private String message;
        private String sessionId;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    /**
     * 健康检查响应体。
     */
    public static class HealthResponse {
        private final String status;
        private final String service;

        public HealthResponse(String status, String service) {
            this.status = status;
            this.service = service;
        }

        public String getStatus() { return status; }
        public String getService() { return service; }
    }
}
