package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.support.ExecutionGraphSupport;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * execution flow 支撑类。
 * 统一维护节点状态发布、剩余节点取消和执行 summary 收尾模板。
 */
@Component
class ExecutionFlowSupport {

    void publishStepResult(ExecutionEventPublisher eventPublisher,
                           ExecutionGraphViewResult graph,
                           AgentExecutionStep step,
                           int stepIndex,
                           int stepCount,
                           String traceId,
                           ExecutionNodeStatus status,
                           String reason,
                           ExecutionStepRunner.StepRunResult result) {
        eventPublisher.publishNodeStatus(
                graph,
                step,
                status,
                stepIndex,
                stepCount,
                traceId,
                reason,
                result.latencyMs(),
                result.response(),
                null
        );
    }

    void cancelRemainingSteps(ExecutionEventPublisher eventPublisher,
                              ExecutionGraphViewResult graph,
                              List<AgentExecutionStep> steps,
                              int startIndex,
                              String traceId,
                              String reason) {
        cancelRemainingSteps(eventPublisher, null, graph, steps, startIndex, steps.size(), traceId, reason);
    }

    void cancelRemainingSteps(ExecutionEventPublisher eventPublisher,
                              ExecutionGraphSupport graphSupport,
                              ExecutionGraphViewResult graph,
                              List<AgentExecutionStep> steps,
                              int startIndex,
                              int stepCount,
                              String traceId,
                              String reason) {
        for (int i = startIndex; i < steps.size(); i++) {
            AgentExecutionStep step = steps.get(i);
            if (graphSupport != null) {
                var node = graphSupport.findNode(graph, step.stepId());
                if (node == null || node.status() != ExecutionNodeStatus.PENDING) {
                    continue;
                }
                eventPublisher.publishNodeStatus(
                        graph, step, ExecutionNodeStatus.CANCELLED,
                        i + 1, stepCount, traceId, reason,
                        0L, null, node.approvalId());
            } else {
                eventPublisher.publishNodeStatus(
                        graph, step, ExecutionNodeStatus.CANCELLED,
                        i + 1, steps.size(), traceId, reason,
                        0L, null, null);
            }
        }
    }

    void finishExecution(OrchestrationControlService orchestrationControlService,
                         ExecutionEventPublisher eventPublisher,
                         RoutingDecision routing,
                         ExecutionGraphViewResult graph,
                         ExecutionNodeStatus status,
                         String traceId,
                         String reason,
                         long durationMs,
                         int executedSteps,
                         boolean success) {
        orchestrationControlService.writeExecutionSummary(routing, executedSteps, success, traceId);
        eventPublisher.publishExecutionSummary(graph, status, traceId, reason, durationMs, executedSteps);
    }

    long elapsedSince(Instant startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt.toEpochMilli());
    }
}
