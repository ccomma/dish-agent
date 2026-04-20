package com.example.dish.common.runtime;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 执行事件（用于审计/回放）。
 */
public record ExecutionEvent(
        String eventId,
        String executionId,
        String planId,
        String nodeId,
        ExecutionNodeStatus status,
        Instant occurredAt,
        Map<String, Object> payload,
        Map<String, Object> metadata
) implements Serializable {

    public ExecutionEvent {
        payload = payload == null ? Collections.emptyMap() : Map.copyOf(payload);
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
