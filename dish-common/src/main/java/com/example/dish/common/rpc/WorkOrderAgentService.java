package com.example.dish.common.rpc;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.context.AgentContext;

/**
 * 工单处理Agent Dubbo服务接口
 */
public interface WorkOrderAgentService {

    /**
     * 处理工单请求
     *
     * @param context Agent上下文（包含意图和参数）
     * @return Agent响应
     */
    AgentResponse process(AgentContext context);

    /**
     * 查询库存
     */
    AgentResponse queryInventory(String storeId, String dishName, String sessionId);

    /**
     * 查询订单
     */
    AgentResponse queryOrder(String orderId, String sessionId);

    /**
     * 创建退款工单
     */
    AgentResponse createRefund(String orderId, String reason, String sessionId);
}
