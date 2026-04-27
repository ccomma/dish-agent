package com.example.dish.service.support;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工单 Agent 支撑类。
 * 负责快捷入口上下文构造和统一响应组装，避免门面类同时承担编排和对象构造细节。
 */
@Component
public class WorkOrderAgentSupport {

    public AgentResponse toResponse(ReActEngine.ReActResult result, AgentContext context, List<String> followUpHints) {
        return AgentResponse.fromReActResult(result, "WorkOrderAgent", context, followUpHints);
    }

    public List<String> hintsByIntent(IntentType intent,
                                      List<String> inventoryHints,
                                      List<String> orderHints,
                                      List<String> refundHints) {
        return switch (intent) {
            case QUERY_INVENTORY -> inventoryHints;
            case QUERY_ORDER -> orderHints;
            case CREATE_REFUND -> refundHints;
            default -> List.of("还有其他问题吗？");
        };
    }

    public AgentContext buildContext(String sessionId,
                                     IntentType intent,
                                     String storeId,
                                     String orderId,
                                     String dishName,
                                     String refundReason,
                                     String userInput) {
        return AgentContext.fromRequest(sessionId, intent, storeId, orderId, dishName, refundReason, userInput);
    }
}
