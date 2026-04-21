package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record MemoryRetrievalHit(
        String memoryType,
        MemoryLayer memoryLayer,
        String content,
        Map<String, Object> metadata,
        String traceId,
        Instant createdAt,
        long sequence,
        String retrievalSource,
        double totalScore,
        double keywordScore,
        double vectorScore,
        double recencyScore,
        String explanation
) implements Serializable {
}
