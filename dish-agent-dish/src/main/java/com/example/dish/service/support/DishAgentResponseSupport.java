package com.example.dish.service.support;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 菜品 Agent 响应支撑。
 * 负责补齐 reflection 标记并把 ReAct 结果转换成统一 AgentResponse，避免门面类同时处理上下文复制和响应组装。
 */
@Component
public class DishAgentResponseSupport {

    public AgentContext buildExecutionContext(AgentContext context, boolean reflectionEnabled) {
        // 1. 写入本次执行是否开启 reflection，同时避免污染外部传入上下文。
        return context.withMetadataValue("enableReflection", reflectionEnabled);
    }

    public AgentResponse toResponse(ReActEngine.ReActResult result, AgentContext context) {
        return AgentResponse.fromReActResult(
                result,
                "DishAgent",
                context,
                List.of("需要帮您下单吗？", "想了解其他菜品吗？", "有其他问题想咨询吗？")
        );
    }
}
