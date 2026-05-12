package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.ChatExecutionService;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.service.ResponseAggregator;
import com.example.dish.gateway.service.StepEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 聊天执行服务实现。
 * 负责编排 gateway 主链路：路由、规划、审批/策略门禁、Agent 调用、响应聚合和 runtime 事件写入。
 */
@Service
public class ChatExecutionServiceImpl implements ChatExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ChatExecutionServiceImpl.class);

    @Resource
    private RoutingAgent routingAgent;
    @Resource
    private ResponseAggregator responseAggregator;
    @Resource
    private OrchestrationControlService orchestrationControlService;
    @Resource
    private ExecutionEventPublisher executionEventPublisher;
    @Resource
    private ExecutionStepRunner executionStepRunner;
    @Resource
    private ExecutionFlowSupport executionFlowSupport;

    @Override
    public GatewayResponse process(String userInput, String requestedSessionId, String requestStoreId, String traceId) {
        // 1. 校验用户消息，并补齐 sessionId。
        if (userInput == null || userInput.trim().isEmpty()) {
            GatewayResponse response = new GatewayResponse();
            response.setSuccess(false);
            response.setContent("消息不能为空");
            return response;
        }

        String sessionId = requestedSessionId != null && !requestedSessionId.isEmpty()
                ? requestedSessionId
                : generateSessionId();

        // 2. 路由并创建 execution graph，之后所有节点状态都围绕该 graph 追加事件。
        RoutingDecision routing = routingAgent.route(userInput, sessionId, requestStoreId, null);
        log.info("gateway dispatch: sessionId={}, targetAgent={}, intent={}",
                routing.context().getSessionId(), routing.targetAgent(), routing.intent());
        List<AgentExecutionStep> plannedSteps = orchestrationControlService.planSteps(routing, traceId);
        ExecutionGraphViewResult graph = executionEventPublisher.startExecution(routing, plannedSteps, traceId);
        Instant executionStartedAt = graph != null && graph.startedAt() != null ? graph.startedAt() : Instant.now();

        // 3. 串行执行步骤，逐步处理审批、策略阻断和 Agent 响应。
        return executeSteps(routing, plannedSteps, graph, executionStartedAt, traceId);
    }

    private GatewayResponse executeSteps(RoutingDecision routing,
                                         List<AgentExecutionStep> plannedSteps,
                                         ExecutionGraphViewResult graph,
                                         Instant executionStartedAt,
                                         String traceId) {
        List<AgentResponse> responses = new ArrayList<>();
        int executedSteps = 0;
        int stepCount = plannedSteps.size();

        for (int i = 0; i < plannedSteps.size(); i++) {
            AgentExecutionStep step = plannedSteps.get(i);
            int stepIndex = i + 1;

            StepEvaluation eval = orchestrationControlService.evaluateStep(step, routing, traceId);
            if (eval == StepEvaluation.REQUIRES_APPROVAL) {
                return pauseForApproval(routing, graph, plannedSteps, step, stepIndex, stepCount, executedSteps, executionStartedAt, traceId);
            }
            if (eval == StepEvaluation.BLOCKED) {
                return blockByPolicy(routing, graph, plannedSteps, step, i, stepIndex, stepCount, executedSteps, executionStartedAt, traceId);
            }

            ExecutionStepRunner.StepRunResult result = executionStepRunner.run(
                    graph,
                    routing,
                    step,
                    stepIndex,
                    stepCount,
                    traceId,
                    "gateway.step.dispatch",
                    "step execution started"
            );
            responses.add(result.response());
            executedSteps++;

            if (result.response().isSuccess()) {
                executionFlowSupport.publishStepResult(
                        executionEventPublisher, graph, step, stepIndex, stepCount, traceId,
                        ExecutionNodeStatus.SUCCEEDED, "step completed successfully", result);
                continue;
            }

            return failAfterDispatch(routing, graph, plannedSteps, step, i, stepIndex, stepCount, executedSteps, responses, result, executionStartedAt, traceId);
        }

        // 4. 所有步骤完成后聚合响应，并写执行摘要/summary 事件。
        GatewayResponse finalResponse = responseAggregator.aggregate(responses, routing);
        executionFlowSupport.finishExecution(
                orchestrationControlService,
                executionEventPublisher,
                routing,
                graph,
                finalResponse.isSuccess() ? ExecutionNodeStatus.SUCCEEDED : ExecutionNodeStatus.FAILED,
                traceId,
                finalResponse.getContent(),
                executionFlowSupport.elapsedSince(executionStartedAt),
                executedSteps,
                finalResponse.isSuccess());
        return finalResponse;
    }

    private GatewayResponse pauseForApproval(RoutingDecision routing,
                                             ExecutionGraphViewResult graph,
                                             List<AgentExecutionStep> plannedSteps,
                                             AgentExecutionStep approvalStep,
                                             int stepIndex,
                                             int stepCount,
                                             int executedSteps,
                                             Instant executionStartedAt,
                                             String traceId) {
        String approvalId = orchestrationControlService.createApprovalTicket(routing, graph.executionId(), approvalStep, traceId);
        executionEventPublisher.publishNodeStatus(
                graph,
                approvalStep,
                ExecutionNodeStatus.WAITING_APPROVAL,
                stepIndex,
                stepCount,
                traceId,
                "manual approval required before execution",
                executionFlowSupport.elapsedSince(executionStartedAt),
                null,
                approvalId
        );
        GatewayResponse pendingResponse = orchestrationControlService.buildApprovalPendingResponse(routing, traceId, approvalId);
        executionFlowSupport.finishExecution(
                orchestrationControlService,
                executionEventPublisher,
                routing,
                graph,
                ExecutionNodeStatus.WAITING_APPROVAL,
                traceId,
                "execution paused waiting for approval",
                executionFlowSupport.elapsedSince(executionStartedAt),
                executedSteps,
                false);
        return pendingResponse;
    }

    private GatewayResponse blockByPolicy(RoutingDecision routing,
                                          ExecutionGraphViewResult graph,
                                          List<AgentExecutionStep> plannedSteps,
                                          AgentExecutionStep step,
                                          int currentIndex,
                                          int stepIndex,
                                          int stepCount,
                                          int executedSteps,
                                          Instant executionStartedAt,
                                          String traceId) {
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
        executionFlowSupport.cancelRemainingSteps(executionEventPublisher, graph, plannedSteps, currentIndex + 1, traceId, "cancelled after policy block");
        GatewayResponse blockedResponse = orchestrationControlService.buildPolicyBlockedResponse(routing, traceId);
        executionFlowSupport.finishExecution(
                orchestrationControlService,
                executionEventPublisher,
                routing,
                graph,
                ExecutionNodeStatus.CANCELLED,
                traceId,
                "execution blocked by policy",
                executionFlowSupport.elapsedSince(executionStartedAt),
                executedSteps,
                false);
        return blockedResponse;
    }

    private GatewayResponse failAfterDispatch(RoutingDecision routing,
                                              ExecutionGraphViewResult graph,
                                              List<AgentExecutionStep> plannedSteps,
                                              AgentExecutionStep step,
                                              int currentIndex,
                                              int stepIndex,
                                              int stepCount,
                                              int executedSteps,
                                              List<AgentResponse> responses,
                                              ExecutionStepRunner.StepRunResult result,
                                              Instant executionStartedAt,
                                              String traceId) {
        executionFlowSupport.publishStepResult(
                executionEventPublisher, graph, step, stepIndex, stepCount, traceId,
                ExecutionNodeStatus.FAILED, "agent degraded or execution failed", result);
        executionFlowSupport.cancelRemainingSteps(executionEventPublisher, graph, plannedSteps, currentIndex + 1, traceId, "cancelled after upstream failure");

        GatewayResponse failedResponse = responseAggregator.aggregate(responses, routing);
        executionFlowSupport.finishExecution(
                orchestrationControlService,
                executionEventPublisher,
                routing,
                graph,
                ExecutionNodeStatus.FAILED,
                traceId,
                "execution failed during step dispatch",
                executionFlowSupport.elapsedSince(executionStartedAt),
                executedSteps,
                false);
        return failedResponse;
    }

    private String generateSessionId() {
        return "SESSION_" + UUID.randomUUID().toString().substring(0, 8);
    }

}
