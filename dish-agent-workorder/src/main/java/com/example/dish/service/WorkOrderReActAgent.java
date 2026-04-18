package com.example.dish.service;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 工单处理 Agent 门面，负责组织上下文并转换 ReAct 结果。
 */
@Component
public class WorkOrderReActAgent {

    @Resource
    private WorkOrderReActEngine workOrderReActEngine;

    public AgentResponse process(AgentContext context) {
        String userInput = context.getUserInput() != null ? context.getUserInput() : context.getIntent().name();
        ReActEngine.ReActResult result = workOrderReActEngine.execute(userInput, context);
        return AgentResponse.builder()
                .success(result.success())
                .content(result.finalResponse())
                .agentName("WorkOrderAgent")
                .context(context)
                .followUpHints(hintsByIntent(context.getIntent()))
                .build();
    }

    public AgentResponse queryInventory(String storeId, String dishName, String sessionId) {
        AgentContext ctx = AgentContext.builder()
                .sessionId(sessionId)
                .intent(IntentType.QUERY_INVENTORY)
                .storeId(storeId)
                .dishName(dishName)
                .userInput("查询库存")
                .build();
        return process(ctx);
    }

    public AgentResponse queryOrder(String orderId, String sessionId) {
        AgentContext ctx = AgentContext.builder()
                .sessionId(sessionId)
                .intent(IntentType.QUERY_ORDER)
                .orderId(orderId)
                .userInput("查询订单")
                .build();
        return process(ctx);
    }

    public AgentResponse createRefund(String orderId, String reason, String sessionId) {
        AgentContext ctx = AgentContext.builder()
                .sessionId(sessionId)
                .intent(IntentType.CREATE_REFUND)
                .orderId(orderId)
                .refundReason(reason)
                .userInput("申请退款")
                .build();
        return process(ctx);
    }

    private List<String> hintsByIntent(IntentType intent) {
        return switch (intent) {
            case QUERY_INVENTORY -> workOrderReActEngine.inventoryHints();
            case QUERY_ORDER -> workOrderReActEngine.orderHints();
            case CREATE_REFUND -> workOrderReActEngine.refundHints();
            default -> List.of("还有其他问题吗？");
        };
    }
}
