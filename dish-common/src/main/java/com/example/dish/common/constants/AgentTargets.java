package com.example.dish.common.constants;

/**
 * Agent 目标标识常量。
 * 统一维护 planner、policy、gateway 等模块共享的 targetAgent 文本，避免各模块散落硬编码。
 */
public final class AgentTargets {

    public static final String DISH_KNOWLEDGE = "dish-knowledge";
    public static final String WORK_ORDER = "work-order";
    public static final String CHAT = "chat";

    private AgentTargets() {
    }
}
