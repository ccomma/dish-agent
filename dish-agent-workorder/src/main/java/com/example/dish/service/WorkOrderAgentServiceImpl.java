package com.example.dish.service;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.WorkOrderAgentService;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.MDC;
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

    private static final String TRACE_ID_KEY = "traceId";

    @Resource
    private WorkOrderReActAgent workOrderReActAgent;

    @Override
    public AgentResponse process(AgentContext context) {
        withTraceFromAttachment();
        try {
            return workOrderReActAgent.process(context);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @Override
    public AgentResponse queryInventory(String storeId, String dishName, String sessionId) {
        withTraceFromAttachment();
        try {
            return workOrderReActAgent.queryInventory(storeId, dishName, sessionId);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @Override
    public AgentResponse queryOrder(String orderId, String sessionId) {
        withTraceFromAttachment();
        try {
            return workOrderReActAgent.queryOrder(orderId, sessionId);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @Override
    public AgentResponse createRefund(String orderId, String reason, String sessionId) {
        withTraceFromAttachment();
        try {
            return workOrderReActAgent.createRefund(orderId, reason, sessionId);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private void withTraceFromAttachment() {
        String traceId = RpcContext.getServiceContext().getAttachment(TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }
}
