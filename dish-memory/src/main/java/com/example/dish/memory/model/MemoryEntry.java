package com.example.dish.memory.model;

import com.example.dish.control.memory.model.MemoryLayer;

import java.time.Instant;
import java.util.Map;

/**
 * 记忆时间线中的单条标准化记录。
 */
public record MemoryEntry(
        String entryId,
        MemoryLayer memoryLayer,
        String memoryType,
        String content,
        Map<String, Object> metadata,
        String traceId,
        Instant createdAt,
        long sequence,
        String storageSource
) {
}
