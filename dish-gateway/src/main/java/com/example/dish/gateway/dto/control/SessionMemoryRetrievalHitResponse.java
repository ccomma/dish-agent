package com.example.dish.gateway.dto.control;

import java.time.Instant;
import java.util.Map;

public record SessionMemoryRetrievalHitResponse(
        String memoryType,
        String memoryLayer,
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
) {
}
