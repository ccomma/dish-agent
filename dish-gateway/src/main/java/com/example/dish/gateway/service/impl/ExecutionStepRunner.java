package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.gateway.observability.GatewayExecutionTracing;
import com.example.dish.gateway.service.AgentDispatchService;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * execution 单步骤运行器。
 * 统一封装节点 RUNNING 事件、Gateway span、Agent dispatch 和耗时计算，
 * 避免首次执行与审批恢复执行维护两套 step 生命周期模板。
 */
@Component
public class ExecutionStepRunner {

    @Resource
    private AgentDispatchService agentDispatchService;
    @Resource
    private ExecutionEventPublisher executionEventPublisher;

    public StepRunResult run(ExecutionGraphViewResult graph,
                             RoutingDecision routing,
                             AgentExecutionStep step,
                             int stepIndex,
                             int stepCount,
                             String traceId,
                             String spanName,
                             String runningReason) {
        // 1. 先写 RUNNING 事件，保证控制台能立即看到当前节点开始执行。
        executionEventPublisher.publishNodeStatus(
                graph,
                step,
                ExecutionNodeStatus.RUNNING,
                stepIndex,
                stepCount,
                traceId,
                runningReason,
                0L,
                null,
                null
        );

        // 2. 在统一 span 内执行 Agent RPC，并记录耗时和响应摘要。
        long startedAt = System.currentTimeMillis();
        AgentResponse response;
        long latencyMs;
        try (GatewayExecutionTracing.SpanScope spanScope =
                     GatewayExecutionTracing.openExecutionSpan(spanName, routing, graph, step, traceId)) {
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
            if (response.getContent() != null) {
                spanScope.span().setAttribute("app.response_preview", abbreviate(response.getContent()));
            }
        }
        return new StepRunResult(response, latencyMs);
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 160) {
            return value;
        }
        return value.substring(0, 157) + "...";
    }

    public record StepRunResult(AgentResponse response, long latencyMs) {
    }
}
