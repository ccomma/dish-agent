package com.example.dish.control.execution.model;

import java.io.Serializable;

public record ExecutionLatestQueryRequest(
        String tenantId,
        String sessionId,
        String traceId
) implements Serializable {
}
