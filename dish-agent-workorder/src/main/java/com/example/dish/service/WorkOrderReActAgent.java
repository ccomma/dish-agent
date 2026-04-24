package com.example.dish.service;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import com.example.dish.service.support.WorkOrderAgentSupport;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 工单处理 Agent 门面，负责组织上下文并转换 ReAct 结果。
 */
@Component
public class WorkOrderReActAgent {

    @Resource
    private WorkOrderReActEngine workOrderReActEngine;
    @Resource
    private WorkOrderAgentSupport workOrderAgentSupport;

    public AgentResponse process(AgentContext context) {
        // 1. 统一归一化用户输入，保证 ReAct 引擎始终有可用问题文本。
        String userInput = context.getUserInput() != null ? context.getUserInput() : context.getIntent().name();
        ReActEngine.ReActResult result = workOrderReActEngine.execute(userInput, context);
        // 2. 按意图选择后续提示并包装成统一 AgentResponse。
        return workOrderAgentSupport.toResponse(
                result,
                context,
                workOrderAgentSupport.hintsByIntent(
                        context.getIntent(),
                        workOrderReActEngine.inventoryHints(),
                        workOrderReActEngine.orderHints(),
                        workOrderReActEngine.refundHints()
                )
        );
    }

    public AgentResponse queryInventory(String storeId, String dishName, String sessionId) {
        return process(workOrderAgentSupport.buildContext(
                sessionId, IntentType.QUERY_INVENTORY, storeId, null, dishName, null, "查询库存"));
    }

    public AgentResponse queryOrder(String orderId, String sessionId) {
        return process(workOrderAgentSupport.buildContext(
                sessionId, IntentType.QUERY_ORDER, null, orderId, null, null, "查询订单"));
    }

    public AgentResponse createRefund(String orderId, String reason, String sessionId) {
        return process(workOrderAgentSupport.buildContext(
                sessionId, IntentType.CREATE_REFUND, null, orderId, null, reason, "申请退款"));
    }
}
