package com.example.dish.gateway.service;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;

import java.util.List;

/**
 * 控制面编排服务（Planner + Policy + Memory）
 */
public interface OrchestrationControlService {

    /**
     * 基于路由结果生成或修正执行步骤。
     */
    List<AgentExecutionStep> planSteps(RoutingDecision routing, String traceId);

    /**
     * 评估单个步骤的策略结果：允许执行、阻断或需审批。
     */
    StepEvaluation evaluateStep(AgentExecutionStep step, RoutingDecision routing, String traceId);

    /**
     * 写入执行摘要到记忆服务。
     */
    void writeExecutionSummary(RoutingDecision routing, int executedStepCount, boolean success, String traceId);

    /**
     * 创建并写入审批单，返回审批单ID。
     */
    String createApprovalTicket(RoutingDecision routing, String executionId, AgentExecutionStep step, String traceId);

    /**
     * 构建待审批响应。
     */
    GatewayResponse buildApprovalPendingResponse(RoutingDecision routing, String traceId, String approvalId);

    /**
     * 构建被策略阻断的响应。
     */
    GatewayResponse buildPolicyBlockedResponse(RoutingDecision routing, String traceId);

    /**
     * 生成执行预览，用于控制面解释与联调排查。
     */
    PlanPreviewResponse preview(RoutingDecision routing, String traceId);
}
