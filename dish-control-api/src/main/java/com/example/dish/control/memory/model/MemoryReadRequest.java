package com.example.dish.control.memory.model;

import java.io.Serializable;

public record MemoryReadRequest(
        String tenantId,
        String sessionId,
        String query,
        String traceId
) implements Serializable {
}
