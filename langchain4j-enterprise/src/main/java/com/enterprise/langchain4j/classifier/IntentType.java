package com.enterprise.langchain4j.classifier;

/**
 * 用户意图类型枚举
 * 用于意图识别路由模块
 */
public enum IntentType {

    /**
     * 问候语（你好、hi、hello等）
     */
    GREETING("问候语"),

    /**
     * 通用闲聊（不在其他分类范围内的问题）
     */
    GENERAL_CHAT("通用闲聊"),

    /**
     * 菜品相关问题（成分、做法、口味、营养等）
     */
    DISH_QUESTION("菜品问题"),

    /**
     * 成分相关问题
     */
    DISH_INGREDIENT("菜品成分"),

    /**
     * 做法相关问题
     */
    DISH_COOKING_METHOD("菜品做法"),

    /**
     * 规则政策问题（退款、优惠、活动、会员等）
     */
    POLICY_QUESTION("政策规则"),

    /**
     * 库存查询
     */
    QUERY_INVENTORY("库存查询"),

    /**
     * 订单查询
     */
    QUERY_ORDER("订单查询"),

    /**
     * 创建退款/售后工单
     */
    CREATE_REFUND("申请退款"),

    /**
     * 无法识别的意图
     */
    UNKNOWN("未知意图");

    private final String description;

    IntentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为闲聊类意图
     */
    public boolean isChat() {
        return this == GREETING || this == GENERAL_CHAT;
    }

    /**
     * 判断是否为RAG检索类意图
     */
    public boolean isRAG() {
        return this == DISH_QUESTION || this == DISH_INGREDIENT
            || this == DISH_COOKING_METHOD || this == POLICY_QUESTION;
    }

    /**
     * 判断是否为工具调用类意图
     */
    public boolean isFunctionCall() {
        return this == QUERY_INVENTORY || this == QUERY_ORDER || this == CREATE_REFUND;
    }
}
