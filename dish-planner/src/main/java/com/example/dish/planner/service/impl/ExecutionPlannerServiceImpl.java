package com.example.dish.planner.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.runtime.ExecutionEdge;
import com.example.dish.common.runtime.ExecutionEdgeCondition;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.ExecutionPlan;
import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;
import com.example.dish.control.planner.service.ExecutionPlannerService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行规划服务实现。
 *
 * @author ccomma
 */
@Service
@DubboService(interfaceClass = ExecutionPlannerService.class, timeout = 15000, retries = 0)
public class ExecutionPlannerServiceImpl implements ExecutionPlannerService {

    @Override
    public PlanningResult plan(PlanningRequest request) {
        IntentType intent = resolveIntent(request);
        String sessionId = request != null && request.context() != null ? request.context().getSessionId() : null;
        String planId = sessionId != null && !sessionId.isBlank() ? "plan-" + sessionId : "plan-bootstrap";

        PlanGraph graph = buildGraph(intent);
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId(planId)
                .intent(intent.name())
                .nodes(graph.nodes)
                .edges(graph.edges)
                .metadata(Map.of("plannerVersion", "planner-v1-rule-graph", "executionMode", graph.executionMode))
                .build();

        return new PlanningResult(
                true,
                "planner-v1-rule-graph",
                "intent-based execution graph generated",
                plan
        );
    }

    private PlanGraph buildGraph(IntentType intent) {
        List<ExecutionNode> nodes = new ArrayList<>();
        List<ExecutionEdge> edges = new ArrayList<>();

        switch (intent) {
            case GREETING, GENERAL_CHAT, UNKNOWN -> {
                nodes.add(agentNode("n-chat-1", "chat", 4000, "single"));
                return new PlanGraph(nodes, edges, "single");
            }
            case DISH_QUESTION, DISH_INGREDIENT, DISH_COOKING_METHOD, POLICY_QUESTION -> {
                nodes.add(agentNode("n-dish-1", "dish-knowledge", 7000, "serial"));
                nodes.add(agentNode("n-chat-2", "chat", 4000, "serial"));
                edges.add(edge("e-dish-chat", "n-dish-1", "n-chat-2", ExecutionEdgeCondition.ON_SUCCESS));
                return new PlanGraph(nodes, edges, "serial");
            }
            case QUERY_INVENTORY, QUERY_ORDER, CREATE_REFUND -> {
                nodes.add(agentNode("n-work-1", "work-order", 7000, "serial"));
                nodes.add(agentNode("n-chat-2", "chat", 4000, "serial"));
                edges.add(edge("e-work-chat", "n-work-1", "n-chat-2", ExecutionEdgeCondition.ON_SUCCESS));
                return new PlanGraph(nodes, edges, "serial");
            }
            default -> {
                nodes.add(agentNode("n-chat-1", "chat", 4000, "single"));
                return new PlanGraph(nodes, edges, "single");
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

    private IntentType resolveIntent(PlanningRequest request) {
        if (request == null || request.context() == null || request.context().getIntent() == null) {
            return IntentType.UNKNOWN;
        }
        return request.context().getIntent();
    }

    private record PlanGraph(List<ExecutionNode> nodes, List<ExecutionEdge> edges, String executionMode) {
    }
}
