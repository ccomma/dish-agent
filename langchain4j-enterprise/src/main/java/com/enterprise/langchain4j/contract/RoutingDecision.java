package com.enterprise.langchain4j.contract;

import com.enterprise.langchain4j.IntentType;
import com.enterprise.langchain4j.context.AgentContext;

/**
 * 路由决策 - RoutingAgent的输出
 * 包含识别的意图、目标Agent和更新后的上下文
 */
public record RoutingDecision(
    IntentType intent,
    String targetAgent,
    String reason,
    AgentContext context
) {

    /**
     * 预定义的目标Agent常量
     */
    public static final String TARGET_DISH_KNOWLEDGE = "dish-knowledge";
    public static final String TARGET_WORK_ORDER = "work-order";
    public static final String TARGET_CHAT = "chat";

    /**
     * 判断是否为闲聊类路由
     */
    public boolean isChatRouting() {
        return TARGET_CHAT.equals(targetAgent);
    }

    /**
     * 判断是否为菜品知识路由
     */
    public boolean isDishKnowledgeRouting() {
        return TARGET_DISH_KNOWLEDGE.equals(targetAgent);
    }

    /**
     * 判断是否为工单处理路由
     */
    public boolean isWorkOrderRouting() {
        return TARGET_WORK_ORDER.equals(targetAgent);
    }
}
