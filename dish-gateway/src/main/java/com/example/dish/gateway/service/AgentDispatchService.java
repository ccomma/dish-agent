package com.example.dish.gateway.service;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;

import java.util.List;

/**
 * Agent 分发服务
 * 封装对各 Agent 服务的 Dubbo RPC 调用
 */
public interface AgentDispatchService {

    /**
     * 根据路由决策分发请求到对应的Agent（兼容单步骤调用）。
     */
    AgentResponse dispatch(RoutingDecision routing);

    /**
     * 执行完整步骤列表（Phase B：串行执行）。
     */
    List<AgentResponse> dispatchAll(RoutingDecision routing, List<AgentExecutionStep> steps);
}
