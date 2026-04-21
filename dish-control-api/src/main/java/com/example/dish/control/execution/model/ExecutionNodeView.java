package com.example.dish.control.execution.model;

import com.example.dish.common.runtime.ExecutionNodeStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record ExecutionNodeView(
        String nodeId,
        String targetAgent,
        String nodeType,
        ExecutionNodeStatus status,
        boolean requiresApproval,
        String riskLevel,
        long latencyMs,
        String statusReason,
        String approvalId,
        Instant updatedAt,
        Map<String, Object> metadata
) implements Serializable {
}
