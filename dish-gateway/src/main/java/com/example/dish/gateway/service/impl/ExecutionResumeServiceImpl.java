package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
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
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    @Override
    public void resumeApprovedExecution(String storeId, String sessionId, String executionId, String traceId) {
        ExecutionGraphViewResult graph = executionRuntimeReadService.graph(new ExecutionGraphQueryRequest(storeId, executionId, traceId));
        if (graph == null) {
            return;
        }

        List<AgentExecutionStep> remaining = reconstructSteps(graph).stream()
                .filter(step -> {
                    var node = findNode(graph, step.stepId());
                    return node != null && (node.status() == ExecutionNodeStatus.WAITING_APPROVAL || node.status() == ExecutionNodeStatus.PENDING);
                })
                .toList();
        if (remaining.isEmpty()) {
            return;
        }

        RoutingDecision routing = reconstructRouting(graph);
        int executed = 0;
        boolean success = true;
        int stepCount = reconstructSteps(graph).size();
        List<AgentExecutionStep> ordered = reconstructSteps(graph);

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
        ExecutionGraphViewResult graph = executionRuntimeReadService.graph(new ExecutionGraphQueryRequest(storeId, executionId, traceId));
        if (graph == null) {
            return;
        }
        List<AgentExecutionStep> steps = reconstructSteps(graph);
        int stepCount = steps.size();
        for (int i = 0; i < steps.size(); i++) {
            AgentExecutionStep step = steps.get(i);
            var node = findNode(graph, step.stepId());
            if (node == null) {
                continue;
            }
            if (node.status() == ExecutionNodeStatus.WAITING_APPROVAL || node.status() == ExecutionNodeStatus.PENDING) {
                executionEventPublisher.publishNodeStatus(graph, step, ExecutionNodeStatus.CANCELLED, i + 1, stepCount, traceId, reason, 0L, null, node.approvalId());
            }
        }
        RoutingDecision routing = reconstructRouting(graph);
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
            var node = findNode(graph, pending.stepId());
            if (node != null && node.status() == ExecutionNodeStatus.PENDING) {
                executionEventPublisher.publishNodeStatus(graph, pending, ExecutionNodeStatus.CANCELLED, i + 1, stepCount, traceId, reason, 0L, null, node.approvalId());
            }
        }
    }

    private RoutingDecision reconstructRouting(ExecutionGraphViewResult graph) {
        IntentType intent = graph.intent() != null ? IntentType.valueOf(graph.intent()) : IntentType.GENERAL_CHAT;
        String userInput = asString(graph.metadata().get("userInput"));
        AgentContext context = AgentContext.builder()
                .sessionId(graph.sessionId())
                .storeId(graph.storeId())
                .userInput(userInput)
                .intent(intent)
                .build();
        context.getMetadata().put("traceId", graph.traceId());

        return RoutingDecision.builder()
                .intent(intent)
                .targetAgent(asString(graph.metadata().get("routingTargetAgent")))
                .reason("resume-execution")
                .context(context)
                .planId(graph.planId())
                .executionMode(graph.executionMode())
                .confidence(asDouble(graph.metadata().get("routingConfidence")))
                .executionSteps(reconstructSteps(graph))
                .build();
    }

    private List<AgentExecutionStep> reconstructSteps(ExecutionGraphViewResult graph) {
        return graph.nodes().stream()
                .sorted(Comparator.comparingInt(node -> asInt(node.metadata().get("stepIndex"))))
                .map(node -> AgentExecutionStep.builder()
                        .stepId(node.nodeId())
                        .targetAgent(node.targetAgent())
                        .nodeType(node.nodeType())
                        .dependsOn(asStringList(node.metadata().get("dependsOn")))
                        .timeoutMs(asLong(node.metadata().get("timeoutMs")))
                        .required(true)
                        .metadata(node.metadata())
                        .build())
                .toList();
    }

    private com.example.dish.control.execution.model.ExecutionNodeView findNode(ExecutionGraphViewResult graph, String nodeId) {
        return graph.nodes().stream().filter(node -> node.nodeId().equals(nodeId)).findFirst().orElse(null);
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
