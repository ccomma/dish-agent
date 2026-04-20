package com.example.dish.gateway.controller;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.AgentDispatchService;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.service.ResponseAggregator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static com.example.dish.gateway.config.TraceIdFilter.TRACE_HEADER;

/**
 * 聊天控制器 - HTTP 入口
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

    /**
     * 处理聊天请求
     *
     * POST /api/chat/process
     * Body: {"message": "宫保鸡丁是什么菜系？", "sessionId": "可选"}
     */
    @PostMapping("/process")
    public GatewayResponse process(@RequestBody ChatRequest request, HttpServletRequest httpServletRequest) {
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

        // 1. 路由决策
        RoutingDecision routing = routingAgent.route(userInput, sessionId, requestStoreId, null);
        log.info("gateway dispatch: sessionId={}, targetAgent={}, intent={}",
                routing.context().getSessionId(), routing.targetAgent(), routing.intent());

        String traceId = httpServletRequest.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = "TRC-GW-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // 2. Control Plane: planner -> policy
        List<AgentExecutionStep> plannedSteps = orchestrationControlService.planSteps(routing, traceId);

        AgentExecutionStep approvalStep = orchestrationControlService.findFirstApprovalRequiredStep(plannedSteps, routing, traceId);
        if (approvalStep != null) {
            String approvalId = orchestrationControlService.createApprovalTicket(routing, approvalStep, traceId);
            GatewayResponse pendingResponse = orchestrationControlService.buildApprovalPendingResponse(routing, traceId, approvalId);
            orchestrationControlService.writeExecutionSummary(routing, 0, false, traceId);
            return pendingResponse;
        }

        List<AgentExecutionStep> allowedSteps = orchestrationControlService.filterAllowedSteps(plannedSteps, routing, traceId);
        if (!plannedSteps.isEmpty() && allowedSteps.isEmpty()) {
            GatewayResponse blockedResponse = orchestrationControlService.buildPolicyBlockedResponse(routing, traceId);
            orchestrationControlService.writeExecutionSummary(routing, 0, false, traceId);
            return blockedResponse;
        }

        // 3. 按步骤执行（Phase B：串行编排）
        List<AgentResponse> responses = agentDispatchService.dispatchAll(routing, allowedSteps);

        // 4. 聚合结果
        GatewayResponse finalResponse = responseAggregator.aggregate(responses, routing);

        // 5. Memory: 写执行摘要
        orchestrationControlService.writeExecutionSummary(
                routing,
                allowedSteps.size(),
                finalResponse.isSuccess(),
                traceId
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

    /**
     * 请求体
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
     * 健康响应
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
