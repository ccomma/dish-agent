package com.example.dish.control.execution.model;

import java.io.Serializable;

public record ExecutionReplayQueryRequest(
        String tenantId,
        String executionId,
        String traceId
) implements Serializable {
}
