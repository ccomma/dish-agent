package com.example.dish.gateway.service;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.GatewayResponse;

/**
 * 结果聚合服务
 * 将多个Agent的响应聚合为最终响应
 */
public interface ResponseAggregator {

    /**
     * 聚合单个Agent的响应
     */
    GatewayResponse aggregate(AgentResponse response, RoutingDecision routing);

    /**
     * 聚合响应（简化版）
     */
    GatewayResponse aggregate(AgentResponse response);
}
