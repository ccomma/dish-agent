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
     * 按策略过滤可执行步骤（拒绝或需审批的步骤在此阶段剔除）。
     */
    List<AgentExecutionStep> filterAllowedSteps(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId);

    /**
     * 返回第一个需要人工审批的步骤，若不存在则返回 null。
     */
    AgentExecutionStep findFirstApprovalRequiredStep(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId);

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
