package com.example.dish.control.execution.model;

import java.io.Serializable;

public record ExecutionGraphSnapshotWriteRequest(
        String tenantId,
        String sessionId,
        ExecutionGraphViewResult graph
) implements Serializable {
}
