package com.example.dish.memory.model;

import com.example.dish.control.memory.model.MemoryLayer;

import java.time.Instant;
import java.util.Map;

/**
 * 长期知识在向量库中的标准文档模型。
 */
public record LongTermMemoryDocument(
        String id,
        String tenantId,
        String sessionId,
        String memoryType,
        MemoryLayer memoryLayer,
        String content,
        Map<String, Object> metadata,
        String traceId,
        Instant createdAt,
        long sequence
) {
}
