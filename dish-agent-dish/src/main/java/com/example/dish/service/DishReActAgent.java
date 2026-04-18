package com.example.dish.service;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品知识 Agent 门面，负责将 ReAct 结果转换为统一响应。
 */
@Component
public class DishReActAgent {

    @Resource
    private DishReActEngine dishReActEngine;

    public AgentResponse answer(String userInput, AgentContext context) {
        AgentContext runContext = withReflectionFlag(context, false);
        ReActEngine.ReActResult result = dishReActEngine.execute(userInput, runContext);
        return toResponse(result, runContext);
    }

    public AgentResponse answerWithReflection(String userInput, AgentContext context) {
        AgentContext runContext = withReflectionFlag(context, true);
        ReActEngine.ReActResult result = dishReActEngine.execute(userInput, runContext);
        return toResponse(result, runContext);
    }

    private AgentContext withReflectionFlag(AgentContext context, boolean enabled) {
        Map<String, Object> metadata = new HashMap<>();
        if (context.getMetadata() != null) {
            metadata.putAll(context.getMetadata());
        }
        metadata.put("enableReflection", enabled);
        return AgentContext.builder()
                .sessionId(context.getSessionId())
                .intent(context.getIntent())
                .userInput(context.getUserInput())
                .storeId(context.getStoreId())
                .orderId(context.getOrderId())
                .dishName(context.getDishName())
                .refundReason(context.getRefundReason())
                .metadata(metadata)
                .build();
    }

    private AgentResponse toResponse(ReActEngine.ReActResult result, AgentContext context) {
        return AgentResponse.builder()
                .success(result.success())
                .content(result.finalResponse())
                .agentName("DishAgent")
                .context(context)
                .followUpHints(List.of("需要帮您下单吗？", "想了解其他菜品吗？", "有其他问题想咨询吗？"))
                .build();
    }
}
