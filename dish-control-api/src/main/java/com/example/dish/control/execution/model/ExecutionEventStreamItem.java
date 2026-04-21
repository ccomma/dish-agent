package com.example.dish.control.execution.model;

import com.example.dish.common.runtime.ExecutionNodeStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record ExecutionEventStreamItem(
        String eventId,
        String executionId,
        String planId,
        String nodeId,
        ExecutionNodeStatus status,
        Instant occurredAt,
        Map<String, Object> payload,
        Map<String, Object> metadata
) implements Serializable {
}
