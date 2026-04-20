package com.example.dish.gateway.dto.control;

import java.time.Instant;

public record DashboardApprovalItemResponse(
        String approvalId,
        String sessionId,
        String status,
        String targetAgent,
        String requestedBy,
        String decidedBy,
        Instant createdAt,
        Instant decidedAt
) {
}
