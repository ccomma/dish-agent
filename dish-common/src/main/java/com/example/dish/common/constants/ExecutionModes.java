package com.example.dish.common.constants;

/**
 * 执行模式常量。
 * 统一维护计划元数据和路由决策中使用的 executionMode 文本，保证跨模块语义一致。
 */
public final class ExecutionModes {

    public static final String SINGLE = "single";
    public static final String SERIAL = "serial";

    private ExecutionModes() {
    }
}
