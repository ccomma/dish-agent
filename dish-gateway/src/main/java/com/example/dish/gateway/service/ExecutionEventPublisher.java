package com.example.dish.gateway.service;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * execution runtime 事件发布接口。
 */
public interface ExecutionEventPublisher {

    ExecutionGraphViewResult startExecution(RoutingDecision routing, List<AgentExecutionStep> steps, String traceId);

    void publishNodeStatus(ExecutionGraphViewResult graph,
                           AgentExecutionStep step,
                           ExecutionNodeStatus status,
                           int stepIndex,
                           int stepCount,
                           String traceId,
                           String statusReason,
                           long latencyMs,
                           AgentResponse response,
                           String approvalId);

    void publishExecutionSummary(ExecutionGraphViewResult graph,
                                 ExecutionNodeStatus status,
                                 String traceId,
                                 String statusReason,
                                 long durationMs,
                                 int executedSteps);

    SseEmitter subscribe(String executionId);
}
