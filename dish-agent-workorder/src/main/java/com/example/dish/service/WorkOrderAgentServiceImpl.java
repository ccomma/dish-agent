package com.example.dish.service;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.WorkOrderAgentService;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 工单处理 Agent Dubbo 门面。
 * 对外暴露统一 process 入口和几个常见快捷入口，内部统一委托给 WorkOrderReActAgent。
 */
@Component
@DubboService(
    interfaceClass = WorkOrderAgentService.class,
    timeout = 30000,
    retries = 0
)
public class WorkOrderAgentServiceImpl implements WorkOrderAgentService {
    @Resource
    private WorkOrderReActAgent workOrderReActAgent;

    @Override
    @DubboProviderSpan("work-order.process")
    public AgentResponse process(AgentContext context) {
        // 通用入口：由门面上下文决定走库存、订单还是退款流程。
        return workOrderReActAgent.process(context);
    }

    @Override
    @DubboProviderSpan("work-order.query_inventory")
    public AgentResponse queryInventory(String storeId, String dishName, String sessionId) {
        // 快捷库存入口：补齐上下文后复用统一 process 流程。
        return workOrderReActAgent.queryInventory(storeId, dishName, sessionId);
    }

    @Override
    @DubboProviderSpan("work-order.query_order")
    public AgentResponse queryOrder(String orderId, String sessionId) {
        return workOrderReActAgent.queryOrder(orderId, sessionId);
    }

    @Override
    @DubboProviderSpan("work-order.create_refund")
    public AgentResponse createRefund(String orderId, String reason, String sessionId) {
        return workOrderReActAgent.createRefund(orderId, reason, sessionId);
    }
}
