package com.example.dish.memory.support;

/**
 * execution runtime 相关 Redis key 生成器。
 */
public final class ExecutionRuntimeKeySupport {

    private ExecutionRuntimeKeySupport() {
    }

    /**
     * 统一维护 execution runtime 相关 Redis key，便于后续调整命名规则时集中修改。
     */
    public static String stateKey(String executionId) {
        return "dish:runtime:execution:" + executionId + ":state";
    }

    public static String latestSessionKey(String tenantId, String sessionId) {
        return "dish:runtime:" + tenantId + ":session:" + sessionId + ":latest";
    }

    public static String planExecutionsKey(String tenantId, String planId) {
        return "dish:runtime:" + tenantId + ":plan:" + planId + ":executions";
    }
}
