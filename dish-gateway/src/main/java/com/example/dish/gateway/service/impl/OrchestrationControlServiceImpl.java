package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;
import com.example.dish.control.planner.service.ExecutionPlannerService;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.service.StepEvaluation;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private MemoryReadService memoryReadService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private ApprovalTicketService approvalTicketService;

    @javax.annotation.Resource
    private PlanningStepMapper planningStepMapper;
    @javax.annotation.Resource
    private PlanPreviewAssembler planPreviewAssembler;
    @javax.annotation.Resource
    private ExecutionSummaryWriter executionSummaryWriter;
    @javax.annotation.Resource
    private PolicyGatekeeper policyGatekeeper;

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
    public StepEvaluation evaluateStep(AgentExecutionStep step, RoutingDecision routing, String traceId) {
        AgentExecutionStep approvalStep = policyGatekeeper.findFirstApprovalRequiredStep(List.of(step), routing, traceId);
        if (approvalStep != null) {
            return StepEvaluation.REQUIRES_APPROVAL;
        }
        List<AgentExecutionStep> allowed = policyGatekeeper.filterAllowedSteps(List.of(step), routing, traceId);
        if (allowed.isEmpty()) {
            return StepEvaluation.BLOCKED;
        }
        return StepEvaluation.ALLOWED;
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
        List<com.example.dish.common.runtime.PolicyDecision> decisions = steps.stream()
                .map(step -> policyGatekeeper.evaluate(step, routing, traceId))
                .toList();

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

    @Override
    public void writeExecutionSummary(RoutingDecision routing, int executedStepCount, boolean success, String traceId) {
        executionSummaryWriter.write(routing, executedStepCount, success, traceId);
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
            routing.context().setTraceId(traceId);
            routing.context().setMemoryHit(result != null && result.hit());
            routing.context().setMemorySource(result != null ? result.source() : "unknown");
            routing.context().setMemorySnippets(result != null ? result.snippets() : List.of());
            routing.context().getMetadata().put("memoryHits", result != null ? result.hits() : List.of());
        } catch (Exception ex) {
            log.warn("memory read failed: sessionId={}, traceId={}, message={}",
                    sessionId, traceId, ex.getMessage());
            routing.context().setMemoryHit(false);
            routing.context().setMemorySource("unavailable");
            routing.context().setMemorySnippets(List.of());
            routing.context().getMetadata().put("memoryHits", List.of());
        }
    }

    private void attachTraceMetadata(RoutingDecision routing, String traceId) {
        if (routing == null || routing.context() == null) {
            return;
        }
        routing.context().setTraceId(traceId);
        routing.context().setPlanId(routing.planId());
        routing.context().setExecutionMode(routing.executionMode());
    }

    private void attachApprovalMetadata(RoutingDecision routing, String approvalId) {
        if (routing == null || routing.context() == null) {
            return;
        }
        routing.context().setApprovalId(approvalId);
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
        return routing.context().getTraceId();
    }

    private boolean memoryHit(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return false;
        }
        Boolean value = routing.context().getMemoryHit();
        return value != null && value;
    }

    private List<String> memorySnippets(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return List.of();
        }
        List<String> value = routing.context().getMemorySnippets();
        return value != null ? value : List.of();
    }

}
