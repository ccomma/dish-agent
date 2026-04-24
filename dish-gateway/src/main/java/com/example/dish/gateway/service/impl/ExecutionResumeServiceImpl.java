package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.service.ExecutionRuntimeReadService;
import com.example.dish.gateway.observability.GatewayExecutionTracing;
import com.example.dish.gateway.service.AgentDispatchService;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import com.example.dish.gateway.service.ExecutionResumeService;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.support.ExecutionGraphSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * execution 恢复服务。
 * 负责在审批通过或拒绝后，根据已持久化的 graph/runtime 状态继续执行或取消剩余步骤。
 */
@Service
public class ExecutionResumeServiceImpl implements ExecutionResumeService {

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private ExecutionRuntimeReadService executionRuntimeReadService;

    @Resource
    private AgentDispatchService agentDispatchService;

    @Resource
    private ExecutionEventPublisher executionEventPublisher;

    @Resource
    private OrchestrationControlService orchestrationControlService;
    @Resource
    private ExecutionGraphSupport executionGraphSupport;

    @Override
    public void resumeApprovedExecution(String storeId, String sessionId, String executionId, String traceId) {
        // 1. 先读取 execution graph，找出当前仍处于 WAITING_APPROVAL/PENDING 的步骤。
        ExecutionGraphViewResult graph = executionRuntimeReadService.graph(new ExecutionGraphQueryRequest(storeId, executionId, traceId));
        if (graph == null) {
            return;
        }

        List<AgentExecutionStep> ordered = executionGraphSupport.reconstructSteps(graph);
        List<AgentExecutionStep> remaining = ordered.stream()
                .filter(step -> {
                    var node = executionGraphSupport.findNode(graph, step.stepId());
                    return node != null && (node.status() == ExecutionNodeStatus.WAITING_APPROVAL || node.status() == ExecutionNodeStatus.PENDING);
                })
                .toList();
        if (remaining.isEmpty()) {
            return;
        }

        RoutingDecision routing = executionGraphSupport.reconstructRouting(graph);
        int executed = 0;
        boolean success = true;
        int stepCount = ordered.size();

        // 2. 对剩余步骤按顺序恢复执行，并持续写入运行态事件。
        for (AgentExecutionStep step : remaining) {
            int stepIndex = ordered.indexOf(step) + 1;
            executionEventPublisher.publishNodeStatus(graph, step, ExecutionNodeStatus.RUNNING, stepIndex, stepCount, traceId, "approval granted, resuming execution", 0L, null, null);

            long startedAt = System.currentTimeMillis();
            AgentResponse response;
            long latencyMs;
            try (GatewayExecutionTracing.SpanScope spanScope =
                         GatewayExecutionTracing.openExecutionSpan("gateway.step.resume", routing, graph, step, traceId)) {
                try {
                    response = agentDispatchService.dispatchStep(routing, step);
                } catch (RuntimeException ex) {
                    spanScope.recordFailure(ex);
                    throw ex;
                }
                latencyMs = System.currentTimeMillis() - startedAt;
                spanScope.span().setAttribute("app.step_index", stepIndex);
                spanScope.span().setAttribute("app.step_count", stepCount);
                spanScope.span().setAttribute("app.latency_ms", latencyMs);
                spanScope.span().setAttribute("app.agent_success", response.isSuccess());
            }

            if (response.isSuccess()) {
                executionEventPublisher.publishNodeStatus(graph, step, ExecutionNodeStatus.SUCCEEDED, stepIndex, stepCount, traceId, "step completed after approval", latencyMs, response, null);
                executed++;
                continue;
            }

            executionEventPublisher.publishNodeStatus(graph, step, ExecutionNodeStatus.FAILED, stepIndex, stepCount, traceId, "step failed after approval", latencyMs, response, null);
            success = false;
            executed++;
            cancelPending(graph, ordered, stepIndex, stepCount, traceId, "downstream cancelled due to upstream failure");
            break;
        }

        // 3. 最后补写 execution summary，保证控制台和指标都能看到恢复后的结果。
        orchestrationControlService.writeExecutionSummary(routing, executed, success, traceId);
        executionEventPublisher.publishExecutionSummary(
                graph,
                success ? ExecutionNodeStatus.SUCCEEDED : ExecutionNodeStatus.FAILED,
                traceId,
                success ? "execution resumed and completed" : "execution resumed but failed",
                graph.durationMs(),
                executed
        );
    }

    @Override
    public void rejectExecution(String storeId, String sessionId, String executionId, String traceId, String reason) {
        // 审批拒绝时，把等待中的步骤统一标记为 CANCELLED，并写入最终 summary。
        ExecutionGraphViewResult graph = executionRuntimeReadService.graph(new ExecutionGraphQueryRequest(storeId, executionId, traceId));
        if (graph == null) {
            return;
        }
        List<AgentExecutionStep> steps = executionGraphSupport.reconstructSteps(graph);
        int stepCount = steps.size();
        for (int i = 0; i < steps.size(); i++) {
            AgentExecutionStep step = steps.get(i);
            var node = executionGraphSupport.findNode(graph, step.stepId());
            if (node == null) {
                continue;
            }
            if (node.status() == ExecutionNodeStatus.WAITING_APPROVAL || node.status() == ExecutionNodeStatus.PENDING) {
                executionEventPublisher.publishNodeStatus(graph, step, ExecutionNodeStatus.CANCELLED, i + 1, stepCount, traceId, reason, 0L, null, node.approvalId());
            }
        }
        RoutingDecision routing = executionGraphSupport.reconstructRouting(graph);
        orchestrationControlService.writeExecutionSummary(routing, 0, false, traceId);
        executionEventPublisher.publishExecutionSummary(graph, ExecutionNodeStatus.CANCELLED, traceId, reason, graph.durationMs(), 0);
    }

    private void cancelPending(ExecutionGraphViewResult graph,
                               List<AgentExecutionStep> steps,
                               int currentStepIndex,
                               int stepCount,
                               String traceId,
                               String reason) {
        for (int i = currentStepIndex; i < steps.size(); i++) {
            AgentExecutionStep pending = steps.get(i);
            var node = executionGraphSupport.findNode(graph, pending.stepId());
            if (node != null && node.status() == ExecutionNodeStatus.PENDING) {
                executionEventPublisher.publishNodeStatus(graph, pending, ExecutionNodeStatus.CANCELLED, i + 1, stepCount, traceId, reason, 0L, null, node.approvalId());
            }
        }
    }
}
