package com.example.dish.gateway.support;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionNodeView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * execution graph 支撑组件。
 * 负责把持久化后的 graph 视图重新还原成 gateway 可继续执行的步骤和路由决策。
 */
@Component
public class ExecutionGraphSupport {

    /**
     * 从 graph 视图中按 stepIndex 还原步骤顺序。
     */
    public List<AgentExecutionStep> reconstructSteps(ExecutionGraphViewResult graph) {
        if (graph == null || graph.nodes() == null) {
            return List.of();
        }
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

    /**
     * 从 graph metadata 还原最小 RoutingDecision，供恢复执行链路继续复用。
     */
    public RoutingDecision reconstructRouting(ExecutionGraphViewResult graph) {
        if (graph == null) {
            return null;
        }
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

    /**
     * 按 nodeId 查找 graph 中的节点视图。
     */
    public ExecutionNodeView findNode(ExecutionGraphViewResult graph, String nodeId) {
        if (graph == null || graph.nodes() == null || nodeId == null) {
            return null;
        }
        return graph.nodes().stream().filter(node -> nodeId.equals(node.nodeId())).findFirst().orElse(null);
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
