package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record MemoryTimelineEntry(
        String memoryType,
        String content,
        Map<String, Object> metadata,
        String traceId,
        Instant createdAt,
        long sequence
) implements Serializable {
}
