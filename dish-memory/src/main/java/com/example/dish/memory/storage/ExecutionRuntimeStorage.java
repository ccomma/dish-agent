package com.example.dish.memory.storage;

import com.example.dish.memory.model.ExecutionRuntimeState;
import com.example.dish.memory.support.ExecutionRuntimeKeySupport;
import com.example.dish.memory.support.ExecutionRuntimeStorageCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
/**
 * execution runtime 底层存储。
 * 负责 graph 快照、事件流索引、session 最新 execution 和 plan -> execution 关系的持久化，
 * 上层运行态读写服务只通过这里访问底层介质。
 */
public class ExecutionRuntimeStorage {

    private static final Map<String, ExecutionRuntimeState> STATES = new ConcurrentHashMap<>();
    private static final Map<String, String> SESSION_LATEST = new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArrayList<String>> PLAN_EXECUTIONS = new ConcurrentHashMap<>();

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public void saveState(ExecutionRuntimeState state) {
        // 1. 先更新进程内状态，保证本地模式和测试场景立刻可读。
        STATES.put(state.graph().executionId(), state);
        if (usesRedis()) {
            // 2. Redis 模式再把完整运行态快照序列化后写入远端存储。
            redisTemplate.opsForValue().set(
                    ExecutionRuntimeKeySupport.stateKey(state.graph().executionId()),
                    ExecutionRuntimeStorageCodec.encode(state)
            );
        }
    }

    public ExecutionRuntimeState loadState(String executionId) {
        // 1. Redis 模式优先读取远端快照，保证重启后仍可恢复运行态。
        if (usesRedis()) {
            return ExecutionRuntimeStorageCodec.decode(
                    redisTemplate.opsForValue().get(ExecutionRuntimeKeySupport.stateKey(executionId)),
                    ExecutionRuntimeState.class
            );
        }
        // 2. 本地模式直接读取进程内缓存。
        return STATES.get(executionId);
    }

    public String latestExecutionId(String tenantId, String sessionId) {
        // 1. Redis 模式下从独立 latest key 查询当前会话最后一次 execution。
        if (usesRedis()) {
            return redisTemplate.opsForValue().get(ExecutionRuntimeKeySupport.latestSessionKey(tenantId, sessionId));
        }
        // 2. 本地模式下使用 session 复合 key 命中缓存索引。
        return SESSION_LATEST.get(sessionKey(tenantId, sessionId));
    }

    public void saveLatestExecution(String tenantId, String sessionId, String executionId) {
        // 1. 维护进程内 latest 索引，方便本地模式和单测快速读取。
        SESSION_LATEST.put(sessionKey(tenantId, sessionId), executionId);
        if (usesRedis()) {
            // 2. Redis 模式同步更新远端 latest key。
            redisTemplate.opsForValue().set(ExecutionRuntimeKeySupport.latestSessionKey(tenantId, sessionId), executionId);
        }
    }

    public void savePlanExecution(String tenantId, String planId, String executionId) {
        // 1. 进程内保留 plan -> execution 列表，用于调试和本地查询。
        PLAN_EXECUTIONS.computeIfAbsent(planKey(tenantId, planId), ignored -> new CopyOnWriteArrayList<>()).add(executionId);
        if (usesRedis()) {
            // 2. Redis 模式同步维护执行历史列表，支持多实例共享。
            redisTemplate.opsForList().leftPush(ExecutionRuntimeKeySupport.planExecutionsKey(tenantId, planId), executionId);
        }
    }

    public static void clearForTest() {
        STATES.clear();
        SESSION_LATEST.clear();
        PLAN_EXECUTIONS.clear();
    }

    private boolean usesRedis() {
        return "redis".equalsIgnoreCase(memoryMode) && redisTemplate != null;
    }

    private static String sessionKey(String tenantId, String sessionId) {
        return tenantId + "::" + sessionId;
    }

    private static String planKey(String tenantId, String planId) {
        return tenantId + "::" + planId;
    }
}
