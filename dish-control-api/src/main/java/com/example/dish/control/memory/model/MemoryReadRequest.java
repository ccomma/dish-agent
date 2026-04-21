package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.util.List;

public record MemoryReadRequest(
        String tenantId,
        String sessionId,
        String query,
        List<MemoryLayer> memoryLayers,
        int limit,
        String traceId
) implements Serializable {
}
