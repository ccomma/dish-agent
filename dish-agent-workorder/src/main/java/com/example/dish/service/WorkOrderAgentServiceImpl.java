package com.example.dish.service;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.WorkOrderAgentService;
import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
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
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("work-order.process", "dish-agent-workorder");
        try (spanScope) {
            return workOrderReActAgent.process(context);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public AgentResponse queryInventory(String storeId, String dishName, String sessionId) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("work-order.query_inventory", "dish-agent-workorder");
        try (spanScope) {
            return workOrderReActAgent.queryInventory(storeId, dishName, sessionId);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public AgentResponse queryOrder(String orderId, String sessionId) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("work-order.query_order", "dish-agent-workorder");
        try (spanScope) {
            return workOrderReActAgent.queryOrder(orderId, sessionId);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public AgentResponse createRefund(String orderId, String reason, String sessionId) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("work-order.create_refund", "dish-agent-workorder");
        try (spanScope) {
            return workOrderReActAgent.createRefund(orderId, reason, sessionId);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }
}
