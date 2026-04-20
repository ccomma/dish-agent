package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.util.Map;

public record MemoryWriteRequest(
        String tenantId,
        String sessionId,
        String memoryType,
        String content,
        Map<String, Object> metadata,
        String traceId
) implements Serializable {
}
