package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.service.ExecutionRuntimeReadService;
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
    private ExecutionEventPublisher executionEventPublisher;

    @Resource
    private OrchestrationControlService orchestrationControlService;
    @Resource
    private ExecutionGraphSupport executionGraphSupport;
    @Resource
    private ExecutionStepRunner executionStepRunner;
    @Resource
    private ExecutionFlowSupport executionFlowSupport;

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
            ExecutionStepRunner.StepRunResult result = executionStepRunner.run(
                    graph,
                    routing,
                    step,
                    stepIndex,
                    stepCount,
                    traceId,
                    "gateway.step.resume",
                    "approval granted, resuming execution"
            );

            if (result.response().isSuccess()) {
                executionFlowSupport.publishStepResult(
                        executionEventPublisher, graph, step, stepIndex, stepCount, traceId,
                        ExecutionNodeStatus.SUCCEEDED, "step completed after approval", result);
                executed++;
                continue;
            }

            executionFlowSupport.publishStepResult(
                    executionEventPublisher, graph, step, stepIndex, stepCount, traceId,
                    ExecutionNodeStatus.FAILED, "step failed after approval", result);
            success = false;
            executed++;
            executionFlowSupport.cancelRemainingSteps(
                    executionEventPublisher, executionGraphSupport, graph, ordered, stepIndex, stepCount, traceId,
                    "downstream cancelled due to upstream failure");
            break;
        }

        // 3. 最后补写 execution summary，保证控制台和指标都能看到恢复后的结果。
        executionFlowSupport.finishExecution(
                orchestrationControlService,
                executionEventPublisher,
                routing,
                graph,
                success ? ExecutionNodeStatus.SUCCEEDED : ExecutionNodeStatus.FAILED,
                traceId,
                success ? "execution resumed and completed" : "execution resumed but failed",
                graph.durationMs(),
                executed,
                success);
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
        executionFlowSupport.finishExecution(
                orchestrationControlService,
                executionEventPublisher,
                routing,
                graph,
                ExecutionNodeStatus.CANCELLED,
                traceId,
                reason,
                graph.durationMs(),
                0,
                false);
    }
}
