package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.common.runtime.PolicyDecision;
import com.example.dish.common.runtime.PolicyDecisionType;
import com.example.dish.control.execution.model.ExecutionEdgeView;
import com.example.dish.control.execution.model.ExecutionNodeView;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalHitResponse;
import com.example.dish.gateway.dto.control.StepPolicyPreview;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 编排预览响应装配器。
 * 负责把步骤、策略判定和 routing metadata 组装成控制台 DTO，
 * 让控制面门面只保留"先规划、再评估、最后装配"的主流程。
 */
@Component
public class PlanPreviewAssembler {

    public PlanPreviewResponse assemble(RoutingDecision routing,
                                        List<AgentExecutionStep> steps,
                                        List<PolicyDecision> decisions) {
        // 1. 先把每个 step 的策略结果转换成控制台展示项，并统计审批/阻断数量。
        List<StepPolicyPreview> previewSteps = new ArrayList<>();
        int approvalRequiredCount = 0;
        int blockedStepCount = 0;

        for (int i = 0; i < steps.size(); i++) {
            AgentExecutionStep step = steps.get(i);
            PolicyDecision decision = i < decisions.size() ? decisions.get(i) : null;
            boolean approvalRequired = decision != null && decision.decision() == PolicyDecisionType.REQUIRE_APPROVAL;
            boolean executable = decision != null && decision.decision() == PolicyDecisionType.ALLOW;
            boolean blocked = decision == null || decision.decision() == PolicyDecisionType.DENY;
            if (approvalRequired) {
                approvalRequiredCount++;
            }
            if (blocked) {
                blockedStepCount++;
            }

            previewSteps.add(new StepPolicyPreview(
                    step.stepId(),
                    step.targetAgent(),
                    step.nodeType(),
                    step.dependsOn(),
                    decision != null ? decision.decision().name() : "UNKNOWN",
                    decision != null ? decision.riskLevel() : "unknown",
                    approvalRequired,
                    executable,
                    decision != null ? decision.reason() : "policy engine returned empty result"
            ));
        }

        // 2. 再统一组装 memory 命中、DAG 预览和每步策略结果。
        return new PlanPreviewResponse(
                traceIdFrom(routing),
                routing != null && routing.context() != null ? routing.context().getSessionId() : null,
                routing != null && routing.context() != null ? routing.context().getStoreId() : null,
                routing != null ? routing.planId() : null,
                routing != null && routing.intent() != null ? routing.intent().name() : "UNKNOWN",
                routing != null ? routing.executionMode() : null,
                routing != null ? routing.confidence() : 0,
                memoryHit(routing),
                memorySource(routing),
                memorySnippets(routing),
                memoryHits(routing),
                approvalRequiredCount,
                blockedStepCount,
                toPreviewNodes(steps),
                toPreviewEdges(steps),
                previewSteps
        );
    }

    private List<ExecutionNodeView> toPreviewNodes(List<AgentExecutionStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ExecutionNodeView> nodes = new ArrayList<>();
        for (AgentExecutionStep step : steps) {
            nodes.add(new ExecutionNodeView(
                    step.stepId(),
                    step.targetAgent(),
                    step.nodeType(),
                    ExecutionNodeStatus.PENDING,
                    false,
                    asString(step.metadata() != null ? step.metadata().get("riskLevel") : null),
                    0L,
                    null,
                    null,
                    null,
                    step.metadata() != null ? Map.copyOf(step.metadata()) : Map.of()
            ));
        }
        return nodes;
    }

    private List<ExecutionEdgeView> toPreviewEdges(List<AgentExecutionStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ExecutionEdgeView> edges = new ArrayList<>();
        int edgeIndex = 1;
        for (AgentExecutionStep step : steps) {
            if (step.dependsOn() == null) {
                continue;
            }
            for (String dependency : step.dependsOn()) {
                edges.add(new ExecutionEdgeView(
                        "preview-edge-" + edgeIndex++,
                        dependency,
                        step.stepId(),
                        "ON_SUCCESS",
                        Map.of()
                ));
            }
        }
        return edges;
    }

    private String traceIdFrom(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return null;
        }
        return routing.context().getTraceId();
    }

    private boolean memoryHit(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return false;
        }
        Boolean value = routing.context().getMemoryHit();
        return value != null && value;
    }

    private String memorySource(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return "none";
        }
        String value = routing.context().getMemorySource();
        return value != null ? value : "none";
    }

    private List<String> memorySnippets(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return List.of();
        }
        List<String> value = routing.context().getMemorySnippets();
        return value != null ? value : List.of();
    }

    private List<SessionMemoryRetrievalHitResponse> memoryHits(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return List.of();
        }
        Object value = routing.context().getMetadata().get("memoryHits");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(com.example.dish.control.memory.model.MemoryRetrievalHit.class::isInstance)
                .map(com.example.dish.control.memory.model.MemoryRetrievalHit.class::cast)
                .map(hit -> new SessionMemoryRetrievalHitResponse(
                        hit.memoryType(),
                        hit.memoryLayer() != null ? hit.memoryLayer().name() : null,
                        hit.content(),
                        hit.metadata(),
                        hit.traceId(),
                        hit.createdAt(),
                        hit.sequence(),
                        hit.retrievalSource(),
                        hit.totalScore(),
                        hit.keywordScore(),
                        hit.vectorScore(),
                        hit.recencyScore(),
                        hit.explanation()
                ))
                .toList();
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
