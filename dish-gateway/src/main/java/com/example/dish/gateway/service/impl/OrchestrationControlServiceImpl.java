package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionContext;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.ExecutionPlan;
import com.example.dish.common.runtime.PolicyDecisionType;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.memory.service.MemoryWriteService;
import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;
import com.example.dish.control.planner.service.ExecutionPlannerService;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.service.PolicyDecisionService;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;
import com.example.dish.gateway.dto.control.StepPolicyPreview;
import com.example.dish.gateway.service.OrchestrationControlService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrchestrationControlServiceImpl implements OrchestrationControlService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationControlServiceImpl.class);

    @DubboReference(timeout = 15000, retries = 0, check = false)
    private ExecutionPlannerService executionPlannerService;

    @DubboReference(timeout = 8000, retries = 0, check = false)
    private PolicyDecisionService policyDecisionService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private MemoryWriteService memoryWriteService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private MemoryReadService memoryReadService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private ApprovalTicketService approvalTicketService;

    @Override
    public List<AgentExecutionStep> planSteps(RoutingDecision routing, String traceId) {
        if (routing == null) {
            return List.of();
        }

        enrichRoutingContextWithMemory(routing, traceId);
        attachTraceMetadata(routing, traceId);

        PlanningRequest planningRequest = new PlanningRequest(
                routing.context() != null ? routing.context().getUserInput() : null,
                routing.context(),
                routing.context() != null ? routing.context().getStoreId() : null,
                traceId
        );

        PlanningResult planningResult = executionPlannerService.plan(planningRequest);
        List<AgentExecutionStep> plannedSteps = toExecutionSteps(planningResult != null ? planningResult.plan() : null);
        if (!plannedSteps.isEmpty()) {
            return plannedSteps;
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

    private List<AgentExecutionStep> toExecutionSteps(ExecutionPlan plan) {
        if (plan == null || plan.nodes() == null || plan.nodes().isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> dependencies = buildDependencies(plan);
        List<AgentExecutionStep> steps = new ArrayList<>();

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

    private Map<String, List<String>> buildDependencies(ExecutionPlan plan) {
        Map<String, List<String>> dependencies = new java.util.HashMap<>();
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

    @Override
    public List<AgentExecutionStep> filterAllowedSteps(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        List<AgentExecutionStep> allowed = new ArrayList<>();
        for (AgentExecutionStep step : steps) {
            var decision = evaluatePolicy(step, routing, traceId);
            if (decision != null && decision.decision() == PolicyDecisionType.ALLOW) {
                allowed.add(step);
            } else {
                log.warn("step blocked by policy: stepId={}, targetAgent={}, decision={}",
                        step.stepId(), step.targetAgent(), decision != null ? decision.decision() : null);
            }
        }
        return allowed;
    }

    @Override
    public AgentExecutionStep findFirstApprovalRequiredStep(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }

        for (AgentExecutionStep step : steps) {
            var decision = evaluatePolicy(step, routing, traceId);
            if (decision != null && decision.decision() == PolicyDecisionType.REQUIRE_APPROVAL) {
                return step;
            }
        }
        return null;
    }

    @Override
    public GatewayResponse buildApprovalPendingResponse(RoutingDecision routing, String traceId, String approvalId) {
        attachApprovalMetadata(routing, approvalId);
        return buildGatewayResponse(
                routing,
                false,
                "Gateway-Policy",
                "请求涉及高风险操作，已转人工审批，请稍后查看处理结果",
                0,
                approvalId,
                List.of(
                        "approvalId=" + approvalId,
                        "可稍后重试查询审批结果",
                        "如需加急请联系人工客服",
                        "traceId=" + traceId
                )
        );
    }

    @Override
    public GatewayResponse buildPolicyBlockedResponse(RoutingDecision routing, String traceId) {
        return buildGatewayResponse(
                routing,
                false,
                "Gateway-Policy",
                "请求被策略引擎阻断，请检查租户权限、风险等级或改为人工处理",
                0,
                null,
                List.of(
                        "请确认门店上下文是否完整",
                        "高风险操作建议走人工审批",
                        "traceId=" + traceId
                )
        );
    }

    @Override
    public PlanPreviewResponse preview(RoutingDecision routing, String traceId) {
        List<AgentExecutionStep> steps = planSteps(routing, traceId);
        List<StepPolicyPreview> previewSteps = new ArrayList<>();
        int approvalRequiredCount = 0;
        int blockedStepCount = 0;

        for (AgentExecutionStep step : steps) {
            var decision = evaluatePolicy(step, routing, traceId);
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

        return new PlanPreviewResponse(
                traceIdFrom(routing),
                routing != null && routing.context() != null ? routing.context().getSessionId() : null,
                routing != null && routing.context() != null ? routing.context().getStoreId() : null,
                routing != null ? routing.planId() : null,
                routing != null && routing.intent() != null ? routing.intent().name() : "UNKNOWN",
                routing != null ? routing.executionMode() : null,
                routing != null ? routing.confidence() : 0,
                memoryHit(routing),
                memorySnippets(routing),
                approvalRequiredCount,
                blockedStepCount,
                previewSteps
        );
    }

    @Override
    public String createApprovalTicket(RoutingDecision routing, AgentExecutionStep step, String traceId) {
        String approvalId = "APR-" + UUID.randomUUID().toString().substring(0, 8);
        String storeId = routing != null && routing.context() != null ? routing.context().getStoreId() : null;
        String sessionId = routing != null && routing.context() != null ? routing.context().getSessionId() : null;

        var result = approvalTicketService.create(new ApprovalTicketCreateRequest(
                approvalId,
                storeId,
                sessionId,
                traceId,
                step != null ? step.stepId() : null,
                step != null ? step.targetAgent() : null,
                routing != null && routing.intent() != null ? routing.intent().name() : null,
                routing != null ? routing.planId() : null,
                "gateway",
                "policy require approval"
        ));
        if (result == null || !result.success()) {
            log.warn("approval ticket create failed: approvalId={}, sessionId={}, traceId={}, message={}",
                    approvalId, sessionId, traceId, result != null ? result.message() : null);
        }
        return approvalId;
    }

    private com.example.dish.common.runtime.PolicyDecision evaluatePolicy(AgentExecutionStep step, RoutingDecision routing, String traceId) {
        ExecutionNode node = new ExecutionNode(
                step.stepId(),
                ExecutionNodeType.AGENT_CALL,
                step.targetAgent(),
                step.timeoutMs(),
                0,
                false,
                Map.of(),
                step.metadata()
        );
        ExecutionContext context = new ExecutionContext(
                traceId,
                routing != null ? routing.planId() : null,
                routing != null ? routing.context() : null,
                Map.of(),
                Map.of("executionMode", routing != null ? routing.executionMode() : null)
        );
        PolicyEvaluationRequest policyRequest = new PolicyEvaluationRequest(
                node,
                context,
                routing != null && routing.context() != null ? routing.context().getStoreId() : null,
                traceId
        );

        var result = policyDecisionService.evaluate(policyRequest);
        return result != null ? result.decision() : null;
    }

    @Override
    public void writeExecutionSummary(RoutingDecision routing, int executedStepCount, boolean success, String traceId) {
        String sessionId = routing != null && routing.context() != null ? routing.context().getSessionId() : null;
        String storeId = routing != null && routing.context() != null ? routing.context().getStoreId() : null;

        String summary = "planId=" + (routing != null ? routing.planId() : null)
                + ", intent=" + (routing != null && routing.intent() != null ? routing.intent().name() : null)
                + ", executionMode=" + (routing != null ? routing.executionMode() : null)
                + ", executedSteps=" + executedStepCount
                + ", success=" + success;

        MemoryWriteRequest writeRequest = new MemoryWriteRequest(
                storeId,
                sessionId,
                "execution_summary",
                summary,
                metadataMap(
                        "traceId", traceId,
                        "planId", routing != null ? routing.planId() : null,
                        "executionMode", routing != null ? routing.executionMode() : null,
                        "success", success,
                        "executedStepCount", executedStepCount,
                        "sessionId", sessionId,
                        "storeId", storeId
                ),
                traceId
        );

        boolean writeOk = memoryWriteService.write(writeRequest);
        if (!writeOk) {
            log.warn("memory write skipped or failed: sessionId={}, traceId={}", sessionId, traceId);
        }
    }

    private void enrichRoutingContextWithMemory(RoutingDecision routing, String traceId) {
        if (routing == null || routing.context() == null || memoryReadService == null) {
            return;
        }

        String storeId = routing.context().getStoreId();
        String sessionId = routing.context().getSessionId();
        if (storeId == null || storeId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        try {
            MemoryReadResult result = memoryReadService.read(new MemoryReadRequest(
                    storeId,
                    sessionId,
                    routing.context().getUserInput(),
                    traceId
            ));
            routing.context().getMetadata().put("traceId", traceId);
            routing.context().getMetadata().put("memoryHit", result != null && result.hit());
            routing.context().getMetadata().put("memorySource", result != null ? result.source() : "unknown");
            routing.context().getMetadata().put("memorySnippets", result != null ? result.snippets() : List.of());
        } catch (Exception ex) {
            log.warn("memory read failed: sessionId={}, traceId={}, message={}",
                    sessionId, traceId, ex.getMessage());
            routing.context().getMetadata().put("memoryHit", false);
            routing.context().getMetadata().put("memorySnippets", List.of());
        }
    }

    private void attachTraceMetadata(RoutingDecision routing, String traceId) {
        if (routing == null || routing.context() == null) {
            return;
        }
        routing.context().getMetadata().put("traceId", traceId);
        routing.context().getMetadata().put("planId", routing.planId());
        routing.context().getMetadata().put("executionMode", routing.executionMode());
    }

    private void attachApprovalMetadata(RoutingDecision routing, String approvalId) {
        if (routing == null || routing.context() == null) {
            return;
        }
        routing.context().getMetadata().put("approvalId", approvalId);
    }

    private GatewayResponse buildGatewayResponse(RoutingDecision routing,
                                                 boolean success,
                                                 String agentName,
                                                 String content,
                                                 int executedStepCount,
                                                 String approvalId,
                                                 List<String> followUpHints) {
        GatewayResponse.GatewayResponseBuilder builder = new GatewayResponse.GatewayResponseBuilder()
                .success(success)
                .agentName(agentName)
                .content(content)
                .intent(routing != null && routing.intent() != null ? routing.intent().name() : "UNKNOWN")
                .sessionId(routing != null && routing.context() != null ? routing.context().getSessionId() : null)
                .traceId(traceIdFrom(routing))
                .planId(routing != null ? routing.planId() : null)
                .executionMode(routing != null ? routing.executionMode() : null)
                .executedStepCount(executedStepCount)
                .memoryHit(memoryHit(routing))
                .memorySnippets(memorySnippets(routing))
                .approvalId(approvalId)
                .followUpHints(followUpHints);
        return builder.build();
    }

    private String traceIdFrom(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return null;
        }
        Object traceId = routing.context().getMetadata().get("traceId");
        return traceId instanceof String value ? value : null;
    }

    private boolean memoryHit(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return false;
        }
        Object value = routing.context().getMetadata().get("memoryHit");
        return value instanceof Boolean bool && bool;
    }

    @SuppressWarnings("unchecked")
    private List<String> memorySnippets(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return List.of();
        }
        Object value = routing.context().getMetadata().get("memorySnippets");
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
    private Map<String, Object> metadataMap(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key instanceof String text && value != null) {
                metadata.put(text, value);
            }
        }
        return metadata;
    }
}
