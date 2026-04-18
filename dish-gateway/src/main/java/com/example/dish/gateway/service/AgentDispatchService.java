package com.example.dish.gateway.service;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;

/**
 * Agent 分发服务
 * 封装对各 Agent 服务的 Dubbo RPC 调用
 */
public interface AgentDispatchService {

    /**
     * 根据路由决策分发请求到对应的Agent
     */
    AgentResponse dispatch(RoutingDecision routing);
}
