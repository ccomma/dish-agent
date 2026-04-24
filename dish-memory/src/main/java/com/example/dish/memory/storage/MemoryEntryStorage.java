package com.example.dish.memory.storage;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.memory.model.MemoryEntry;
import com.example.dish.memory.support.MemoryKeySupport;
import com.example.dish.memory.support.MemoryStorageCodec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记忆写入主存储。
 *
 * <p>该类当前以“写入职责”为主，负责把 memory entry 写入内存或 Redis，
 * 并维护审批票据相关的基础持久化入口。读侧查询和向量打分已经拆到独立组件。</p>
 */
@Component
public class MemoryEntryStorage {

    private static final int DEFAULT_LIMIT = 5;
    private static final int REDIS_FETCH_MULTIPLIER = 20;
    private static final Map<String, CopyOnWriteArrayList<MemoryEntry>> STORE = new ConcurrentHashMap<>();
    private static final AtomicLong SEQ = new AtomicLong(0);

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MemoryVectorIndexStorage memoryVectorIndexStorage;

    public void append(String tenantId,
                       String sessionId,
                       MemoryLayer memoryLayer,
                       String memoryType,
                       String content,
                       Map<String, Object> metadata,
                       String traceId) {
        // 1. Redis 模式优先写入 Redis 时间线，并同步维护向量索引。
        if (usesRedis()) {
            appendRedis(tenantId, sessionId, memoryLayer, memoryType, content, metadata, traceId);
            return;
        }

        // 2. 本地模式写入内存时间线，供测试与 bootstrap 模式读取。
        long sequence = SEQ.incrementAndGet();
        STORE.computeIfAbsent(memoryKey(tenantId, sessionId), ignored -> new CopyOnWriteArrayList<>())
                .add(new MemoryEntry(
                        entryId(tenantId, sessionId, sequence),
                        memoryLayer,
                        memoryType,
                        content,
                        metadata == null ? Map.of() : Map.copyOf(metadata),
                        traceId,
                        Instant.now(),
                        sequence,
                        storageSource(memoryLayer, false)
                ));
    }

    public void saveApproval(String tenantId, String sessionId, String approvalId, String encodedTicket) {
        // 审批票据只在 Redis 模式下额外持久化；本地模式走 ApprovalTicketStorage 的内存态。
        if (usesRedis()) {
            redisTemplate.opsForValue().set(MemoryKeySupport.approvalKey(tenantId, sessionId, approvalId), encodedTicket);
        }
    }

    public String loadApproval(String tenantId, String sessionId, String approvalId) {
        if (usesRedis()) {
            return redisTemplate.opsForValue().get(MemoryKeySupport.approvalKey(tenantId, sessionId, approvalId));
        }
        return null;
    }

    public String source() {
        return usesRedis() ? "redis" : "in-memory";
    }

    public boolean usesRedis() {
        return "redis".equalsIgnoreCase(memoryMode) && redisTemplate != null;
    }

    public static void clearForTest() {
        STORE.clear();
        SEQ.set(0);
    }

    private void appendRedis(String tenantId,
                             String sessionId,
                             MemoryLayer memoryLayer,
                             String memoryType,
                             String content,
                             Map<String, Object> metadata,
                             String traceId) {
        // 1. 先按租户维度生成全局递增 sequence，保证时间线天然可排序。
        long sequence = redisTemplate.opsForValue().increment(MemoryKeySupport.memorySeqKey(tenantId));
        MemoryEntry entry = new MemoryEntry(
                entryId(tenantId, sessionId, sequence),
                memoryLayer,
                memoryType,
                content,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                traceId,
                Instant.now(),
                sequence,
                storageSource(memoryLayer, true)
        );

        // 2. 同一条 entry 同时写入租户级和会话级时间线，满足聚合查询与会话查询两种入口。
        String encoded = MemoryStorageCodec.encode(entry);
        redisTemplate.opsForZSet().add(MemoryKeySupport.tenantTimelineKey(tenantId), encoded, sequence);
        redisTemplate.opsForZSet().add(MemoryKeySupport.sessionTimelineKey(tenantId, sessionId), encoded, sequence);

        // 3. 长期检索依赖的向量索引统一交给向量存储组件维护。
        memoryVectorIndexStorage.saveVector(entry);
    }

    List<MemoryEntry> queryRedisEntries(MemoryTimelineRequest request, int fetchSize) {
        // 读侧查询组件会在外层做过滤和 limit，这里只负责把原始 entry 列表取出来。
        String key = StringUtils.isBlank(request.sessionId())
                ? MemoryKeySupport.tenantTimelineKey(request.tenantId())
                : MemoryKeySupport.sessionTimelineKey(request.tenantId(), request.sessionId());
        var encodedEntries = redisTemplate.opsForZSet().reverseRange(key, 0, fetchSize - 1);
        if (encodedEntries == null || encodedEntries.isEmpty()) {
            return List.of();
        }

        return encodedEntries.stream()
                .map(MemoryStorageCodec::decode)
                .filter(Objects::nonNull)
                .toList();
    }

    List<MemoryEntry> collectEntries(String tenantId, String sessionId) {
        // sessionId 为空时按租户聚合所有会话 entry，用于 Dashboard 或跨会话检索。
        if (StringUtils.isNotBlank(sessionId)) {
            return STORE.getOrDefault(memoryKey(tenantId, sessionId), new CopyOnWriteArrayList<>());
        }

        String prefix = tenantId + "::";
        return STORE.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    private static String memoryKey(String tenantId, String sessionId) {
        return tenantId + "::" + sessionId;
    }

    private static String entryId(String tenantId, String sessionId, long sequence) {
        return tenantId + "-" + sessionId + "-" + sequence;
    }

    private static String storageSource(MemoryLayer layer, boolean redis) {
        String prefix = redis ? "redis" : "in-memory";
        return switch (layer) {
            case SHORT_TERM_SESSION -> prefix + "-short-term";
            case APPROVAL -> prefix + "-approval";
            case LONG_TERM_KNOWLEDGE -> prefix + "-long-term";
        };
    }
}
