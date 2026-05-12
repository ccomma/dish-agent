package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.ExecutionPlan;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * planner graph 到 gateway 执行步骤的映射器。
 * 只负责节点/边结构转换和 fallback step 构造，不参与 RPC 调用或策略判断。
 */
@Component
public class PlanningStepMapper {

    public List<AgentExecutionStep> toExecutionSteps(ExecutionPlan plan) {
        if (plan == null || plan.nodes() == null || plan.nodes().isEmpty()) {
            return List.of();
        }

        // 1. 先根据边构造每个节点的前置依赖列表。
        Map<String, List<String>> dependencies = buildDependencies(plan);
        List<AgentExecutionStep> steps = new ArrayList<>();

        // 2. 再把 AGENT_CALL 节点转换成 gateway 执行器认识的 step。
        for (ExecutionNode node : plan.nodes()) {
            if (node == null || node.nodeType() != ExecutionNodeType.AGENT_CALL || node.target() == null || node.target().isBlank()) {
                continue;
            }

            steps.add(AgentExecutionStep.builder()
                    .stepId(node.nodeId())
                    .targetAgent(node.target())
                    .nodeType(node.nodeType().name())
                    .dependsOn(dependencies.getOrDefault(node.nodeId(), List.of()))
                    .timeoutMs(node.timeoutMs() > 0 ? node.timeoutMs() : 5000)
                    .required(true)
                    .metadata(node.metadata())
                    .build());
        }

        return steps;
    }

    public List<AgentExecutionStep> fallbackSteps(RoutingDecision routing) {
        if (routing == null) {
            return List.of();
        }
        if (routing.executionSteps() != null && !routing.executionSteps().isEmpty()) {
            return routing.executionSteps();
        }
        if (routing.targetAgent() == null) {
            return List.of();
        }
        return List.of(AgentExecutionStep.builder()
                .stepId("step-fallback-1")
                .targetAgent(routing.targetAgent())
                .nodeType("AGENT_CALL")
                .required(true)
                .timeoutMs(5000)
                .build());
    }

    private Map<String, List<String>> buildDependencies(ExecutionPlan plan) {
        Map<String, List<String>> dependencies = new HashMap<>();
        if (plan.edges() == null || plan.edges().isEmpty()) {
            return dependencies;
        }

        for (var edge : plan.edges()) {
            if (edge == null || edge.toNodeId() == null || edge.fromNodeId() == null) {
                continue;
            }
            dependencies.computeIfAbsent(edge.toNodeId(), ignored -> new ArrayList<>()).add(edge.fromNodeId());
        }
        return dependencies;
    }
}
