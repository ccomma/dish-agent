package com.example.dish.control.execution.model;

import com.example.dish.common.runtime.ExecutionNodeStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ExecutionReplayResult(
        String executionId,
        String planId,
        ExecutionNodeStatus overallStatus,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int totalEvents,
        List<ExecutionEventStreamItem> events
) implements Serializable {
}
