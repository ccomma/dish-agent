package com.example.dish.memory.support;

/**
 * 记忆时间线与审批票据相关 Redis key 生成器。
 */
public final class MemoryKeySupport {

    private MemoryKeySupport() {
    }

    /**
     * 统一维护 memory 模块的 Redis key 规则，避免 key 拼接散落在各个 service/storage 中。
     */
    public static String memorySeqKey(String tenantId) {
        return "dish:memory:" + tenantId + ":seq";
    }

    public static String tenantTimelineKey(String tenantId) {
        return "dish:memory:" + tenantId + ":timeline";
    }

    public static String sessionTimelineKey(String tenantId, String sessionId) {
        return "dish:memory:" + tenantId + ":session:" + sessionId + ":timeline";
    }

    public static String approvalKey(String tenantId, String sessionId, String approvalId) {
        return "dish:memory:" + tenantId + ":session:" + sessionId + ":approval:" + approvalId;
    }

    public static String vectorKey(String entryId) {
        return "dish:memory:vector:" + entryId;
    }
}
