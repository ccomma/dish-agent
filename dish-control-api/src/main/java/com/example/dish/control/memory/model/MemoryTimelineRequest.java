package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.util.Map;

public record MemoryTimelineRequest(
        String tenantId,
        String sessionId,
        String memoryType,
        String keyword,
        Map<String, Object> metadataFilters,
        int limit,
        String traceId
) implements Serializable {
}
