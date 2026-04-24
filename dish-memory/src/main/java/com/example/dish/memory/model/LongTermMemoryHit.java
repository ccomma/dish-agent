package com.example.dish.memory.model;

import com.example.dish.control.memory.model.MemoryLayer;

import java.time.Instant;
import java.util.Map;

/**
 * 长期知识向量召回后的统一命中结果。
 */
public record LongTermMemoryHit(
        String entryId,
        String memoryType,
        MemoryLayer memoryLayer,
        String content,
        Map<String, Object> metadata,
        String traceId,
        Instant createdAt,
        long sequence,
        String retrievalSource,
        double vectorScore
) {
}
