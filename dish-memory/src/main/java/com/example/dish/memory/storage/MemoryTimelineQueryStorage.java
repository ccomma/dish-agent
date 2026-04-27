package com.example.dish.memory.storage;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.memory.model.MemoryEntry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 记忆时间线查询存储。
 *
 * <p>该类负责读侧时间线查询与召回候选收集，
 * 把“怎么查”从 `MemoryEntryStorage` 的写入职责里分离出来。</p>
 */
@Component
public class MemoryTimelineQueryStorage {

    private static final int DEFAULT_LIMIT = 5;
    private static final int REDIS_FETCH_MULTIPLIER = 20;

    @Autowired
    private MemoryEntryStorage memoryEntryStorage;

    public List<MemoryEntry> queryEntries(MemoryTimelineRequest request) {
        // 1. 没有 tenant 范围时直接拒绝查询，避免无边界地扫描所有时间线。
        if (request == null || StringUtils.isBlank(request.tenantId())) {
            return List.of();
        }

        // 2. Redis 模式和内存模式都先拿原始 entry，再走统一过滤逻辑。
        int limit = request.limit() > 0 ? request.limit() : DEFAULT_LIMIT;
        if (memoryEntryStorage.usesRedis()) {
            return filterEntries(memoryEntryStorage.queryRedisEntries(request, Math.max(limit * REDIS_FETCH_MULTIPLIER, 100)), request)
                    .limit(limit)
                    .toList();
        }

        return filterEntries(memoryEntryStorage.collectEntries(request.tenantId(), request.sessionId()), request)
                .sorted(Comparator.comparingLong(MemoryEntry::sequence).reversed())
                .limit(limit)
                .toList();
    }

    public List<MemoryEntry> collectRetrievalCandidates(MemoryTimelineRequest request,
                                                        int fetchSize,
                                                        List<MemoryLayer> allowedLayers) {
        // 1. 召回候选也必须先有 tenant 边界。
        if (request == null || StringUtils.isBlank(request.tenantId())) {
            return List.of();
        }

        // 2. Redis 和内存都按相同的 allowedLayers 过滤，保证 retrieval 层不用关心底层存储模式。
        if (memoryEntryStorage.usesRedis()) {
            return memoryEntryStorage.queryRedisEntries(request, fetchSize).stream()
                    .filter(entry -> allowedLayers == null || allowedLayers.isEmpty() || allowedLayers.contains(entry.memoryLayer()))
                    .toList();
        }

        return memoryEntryStorage.collectEntries(request.tenantId(), request.sessionId()).stream()
                .filter(entry -> allowedLayers == null || allowedLayers.isEmpty() || allowedLayers.contains(entry.memoryLayer()))
                .sorted(Comparator.comparingLong(MemoryEntry::sequence).reversed())
                .limit(fetchSize)
                .toList();
    }

    private Stream<MemoryEntry> filterEntries(List<MemoryEntry> entries, MemoryTimelineRequest request) {
        // 所有时间线过滤条件统一收口到这里，避免 Redis / 内存路径各写一套判断。
        return entries.stream()
                .filter(entry -> request.memoryLayer() == null || request.memoryLayer() == entry.memoryLayer())
                .filter(entry -> StringUtils.isBlank(request.memoryType()) || request.memoryType().equals(entry.memoryType()))
                .filter(entry -> StringUtils.isBlank(request.keyword()) || entry.content().contains(request.keyword()))
                .filter(entry -> matchesMetadata(entry, request.metadataFilters()));
    }

    private boolean matchesMetadata(MemoryEntry entry, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            if (!Objects.equals(entry.metadata().get(filter.getKey()), filter.getValue())) {
                return false;
            }
        }
        return true;
    }
}
