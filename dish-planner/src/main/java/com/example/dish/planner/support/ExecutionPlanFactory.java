package com.example.dish.planner.support;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.constants.AgentTargets;
import com.example.dish.common.constants.ExecutionModes;
import com.example.dish.common.runtime.ExecutionEdge;
import com.example.dish.common.runtime.ExecutionEdgeCondition;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.ExecutionPlan;
import com.example.dish.control.planner.model.PlanningRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行计划工厂。
 * 负责把 planner 的规则图模板转换为统一的 ExecutionPlan，避免服务门面同时承担模板选择和计划组装。
 */
@Component
public class ExecutionPlanFactory {

    public ExecutionPlan create(PlanningRequest request) {
        // 1. 先从请求里归一化意图和 planId，保证空请求时也能得到稳定输出。
        IntentType intent = resolveIntent(request);
        String planId = resolvePlanId(request);

        // 2. 按意图选择最小执行图模板。
        PlanGraph graph = buildGraph(intent);

        // 3. 统一组装成 ExecutionPlan，供 gateway 预览与真实执行链路复用。
        return ExecutionPlan.builder()
                .planId(planId)
                .intent(intent.name())
                .nodes(graph.nodes())
                .edges(graph.edges())
                .metadata(Map.of("plannerVersion", "planner-v1-rule-graph", "executionMode", graph.executionMode()))
                .build();
    }

    private String resolvePlanId(PlanningRequest request) {
        String sessionId = request != null && request.context() != null ? request.context().getSessionId() : null;
        return sessionId != null && !sessionId.isBlank() ? "plan-" + sessionId : "plan-bootstrap";
    }

    private IntentType resolveIntent(PlanningRequest request) {
        if (request == null || request.context() == null || request.context().getIntent() == null) {
            return IntentType.UNKNOWN;
        }
        return request.context().getIntent();
    }

    private PlanGraph buildGraph(IntentType intent) {
        List<ExecutionNode> nodes = new ArrayList<>();
        List<ExecutionEdge> edges = new ArrayList<>();

        switch (intent) {
            case GREETING, GENERAL_CHAT, UNKNOWN -> {
                nodes.add(agentNode("n-chat-1", AgentTargets.CHAT, 4000, ExecutionModes.SINGLE));
                return new PlanGraph(nodes, edges, ExecutionModes.SINGLE);
            }
            case DISH_QUESTION, DISH_INGREDIENT, DISH_COOKING_METHOD, POLICY_QUESTION -> {
                nodes.add(agentNode("n-dish-1", AgentTargets.DISH_KNOWLEDGE, 7000, ExecutionModes.SERIAL));
                nodes.add(agentNode("n-chat-2", AgentTargets.CHAT, 4000, ExecutionModes.SERIAL));
                edges.add(edge("e-dish-chat", "n-dish-1", "n-chat-2", ExecutionEdgeCondition.ON_SUCCESS));
                return new PlanGraph(nodes, edges, ExecutionModes.SERIAL);
            }
            case QUERY_INVENTORY, QUERY_ORDER, CREATE_REFUND -> {
                nodes.add(agentNode("n-work-1", AgentTargets.WORK_ORDER, 7000, ExecutionModes.SERIAL));
                nodes.add(agentNode("n-chat-2", AgentTargets.CHAT, 4000, ExecutionModes.SERIAL));
                edges.add(edge("e-work-chat", "n-work-1", "n-chat-2", ExecutionEdgeCondition.ON_SUCCESS));
                return new PlanGraph(nodes, edges, ExecutionModes.SERIAL);
            }
            default -> {
                nodes.add(agentNode("n-chat-1", AgentTargets.CHAT, 4000, ExecutionModes.SINGLE));
                return new PlanGraph(nodes, edges, ExecutionModes.SINGLE);
            }
        }
    }

    private ExecutionNode agentNode(String nodeId, String target, long timeoutMs, String mode) {
        return new ExecutionNode(
                nodeId,
                ExecutionNodeType.AGENT_CALL,
                target,
                timeoutMs,
                0,
                false,
                Map.of(),
                Map.of("executionMode", mode)
        );
    }

    private ExecutionEdge edge(String edgeId, String fromNodeId, String toNodeId, ExecutionEdgeCondition condition) {
        return new ExecutionEdge(edgeId, fromNodeId, toNodeId, condition, Map.of());
    }

    private record PlanGraph(List<ExecutionNode> nodes, List<ExecutionEdge> edges, String executionMode) {
    }
}
