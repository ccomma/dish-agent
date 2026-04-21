package com.example.dish.control.execution.model;

import com.example.dish.common.runtime.ExecutionNodeStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExecutionGraphViewResult(
        String executionId,
        String planId,
        String sessionId,
        String storeId,
        String traceId,
        String intent,
        String executionMode,
        ExecutionNodeStatus overallStatus,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        Map<String, Object> metadata,
        List<ExecutionNodeView> nodes,
        List<ExecutionEdgeView> edges,
        int totalEvents
) implements Serializable {
}
