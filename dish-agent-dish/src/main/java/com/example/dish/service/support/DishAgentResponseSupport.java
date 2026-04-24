package com.example.dish.service.support;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品 Agent 响应支撑。
 * 负责补齐 reflection 标记并把 ReAct 结果转换成统一 AgentResponse，避免门面类同时处理上下文复制和响应组装。
 */
@Component
public class DishAgentResponseSupport {

    public AgentContext buildExecutionContext(AgentContext context, boolean reflectionEnabled) {
        // 1. 克隆 metadata，避免直接污染外部传入上下文。
        Map<String, Object> metadata = new HashMap<>();
        if (context.getMetadata() != null) {
            metadata.putAll(context.getMetadata());
        }

        // 2. 写入本次执行是否开启 reflection。
        metadata.put("enableReflection", reflectionEnabled);

        // 3. 返回一份新的运行态上下文，供引擎内部安全修改。
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

    public AgentResponse toResponse(ReActEngine.ReActResult result, AgentContext context) {
        return AgentResponse.builder()
                .success(result.success())
                .content(result.finalResponse())
                .agentName("DishAgent")
                .context(context)
                .followUpHints(List.of("需要帮您下单吗？", "想了解其他菜品吗？", "有其他问题想咨询吗？"))
                .build();
    }
}
