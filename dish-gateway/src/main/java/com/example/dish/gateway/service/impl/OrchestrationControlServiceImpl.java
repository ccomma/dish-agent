package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionContext;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.PolicyDecisionType;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.memory.service.MemoryWriteService;
import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;
import com.example.dish.control.planner.service.ExecutionPlannerService;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.service.PolicyDecisionService;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;
import com.example.dish.gateway.service.OrchestrationControlService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 控制面编排服务门面。
 * 负责把 routing 决策接入 planner、policy、memory、approval 等控制面服务，
 * 并向聊天主链路或控制台提供可执行步骤、审批响应和预览结果。
 */
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

    private final PlanningStepMapper planningStepMapper = new PlanningStepMapper();
    private final PlanPreviewAssembler planPreviewAssembler = new PlanPreviewAssembler();
    private final ExecutionSummaryWriter executionSummaryWriter = new ExecutionSummaryWriter();

    @Override
    public List<AgentExecutionStep> planSteps(RoutingDecision routing, String traceId) {
        // 1. 缺少路由决策时直接返回空步骤列表。
        if (routing == null) {
            return List.of();
        }

        // 2. 先补齐 memory 召回结果和 trace 元数据，再发给 planner 生成 execution graph。
        enrichRoutingContextWithMemory(routing, traceId);
        attachTraceMetadata(routing, traceId);

        PlanningRequest planningRequest = new PlanningRequest(
                routing.context() != null ? routing.context().getUserInput() : null,
                routing.context(),
                routing.context() != null ? routing.context().getStoreId() : null,
                traceId
        );

        PlanningResult planningResult = executionPlannerService.plan(planningRequest);
        List<AgentExecutionStep> plannedSteps = planningStepMapper.toExecutionSteps(planningResult != null ? planningResult.plan() : null);
        if (!plannedSteps.isEmpty()) {
            return plannedSteps;
        }

        // 3. planner 没给出步骤时，优先回退已有 executionSteps，再退回单目标 fallback 步骤。
        return planningStepMapper.fallbackSteps(routing);
    }

    @Override
    public List<AgentExecutionStep> filterAllowedSteps(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId) {
        // 1. 对每个步骤独立做策略评估，只保留明确允许执行的步骤。
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
        // 顺序扫描步骤，找到第一个要求人工审批的节点。
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
        // 将 approvalId 写回 routing metadata，供后续响应聚合和控制台查询复用。
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
        // 1. 先生成步骤，再逐步评估策略结果。
        List<AgentExecutionStep> steps = planSteps(routing, traceId);
        List<com.example.dish.common.runtime.PolicyDecision> decisions = new ArrayList<>();
        for (AgentExecutionStep step : steps) {
            decisions.add(evaluatePolicy(step, routing, traceId));
        }

        // 2. 把 memory 命中、DAG 预览和每步策略结果统一交给装配器生成控制台 DTO。
        return planPreviewAssembler.assemble(routing, steps, decisions);
    }

    @Override
    public String createApprovalTicket(RoutingDecision routing, String executionId, AgentExecutionStep step, String traceId) {
        // 审批票据由 gateway 统一发起，交给 dish-memory 保存闭环状态。
        String approvalId = "APR-" + UUID.randomUUID().toString().substring(0, 8);
        String storeId = routing != null && routing.context() != null ? routing.context().getStoreId() : null;
        String sessionId = routing != null && routing.context() != null ? routing.context().getSessionId() : null;

        var result = approvalTicketService.create(new ApprovalTicketCreateRequest(
                approvalId,
                executionId,
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
        // 把 gateway step 重新映射成 policy 服务需要的 node/context 结构。
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
        executionSummaryWriter.write(memoryWriteService, routing, executedStepCount, success, traceId);
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
                    List.of(MemoryLayer.SHORT_TERM_SESSION, MemoryLayer.APPROVAL, MemoryLayer.LONG_TERM_KNOWLEDGE),
                    5,
                    traceId
            ));
            routing.context().getMetadata().put("traceId", traceId);
            routing.context().getMetadata().put("memoryHit", result != null && result.hit());
            routing.context().getMetadata().put("memorySource", result != null ? result.source() : "unknown");
            routing.context().getMetadata().put("memorySnippets", result != null ? result.snippets() : List.of());
            routing.context().getMetadata().put("memoryHits", result != null ? result.hits() : List.of());
        } catch (Exception ex) {
            log.warn("memory read failed: sessionId={}, traceId={}, message={}",
                    sessionId, traceId, ex.getMessage());
            routing.context().getMetadata().put("memoryHit", false);
            routing.context().getMetadata().put("memorySource", "unavailable");
            routing.context().getMetadata().put("memorySnippets", List.of());
            routing.context().getMetadata().put("memoryHits", List.of());
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

}
