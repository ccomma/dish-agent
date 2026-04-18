package com.example.dish.service;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.WorkOrderAgentService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 工单处理Agent Dubbo服务实现
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
    public AgentResponse process(AgentContext context) {
        return workOrderReActAgent.process(context);
    }

    @Override
    public AgentResponse queryInventory(String storeId, String dishName, String sessionId) {
        return workOrderReActAgent.queryInventory(storeId, dishName, sessionId);
    }

    @Override
    public AgentResponse queryOrder(String orderId, String sessionId) {
        return workOrderReActAgent.queryOrder(orderId, sessionId);
    }

    @Override
    public AgentResponse createRefund(String orderId, String reason, String sessionId) {
        return workOrderReActAgent.createRefund(orderId, reason, sessionId);
    }
}
