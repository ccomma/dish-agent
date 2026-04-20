package com.example.dish.gateway.dto.control;

import java.time.Instant;
import java.util.Map;

public record SessionMemoryEntryResponse(
        String memoryType,
        String content,
        Map<String, Object> metadata,
        String traceId,
        Instant createdAt,
        long sequence
) {
}
