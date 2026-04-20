package com.example.dish.gateway.dto.control;

import java.time.Instant;

public record ApprovalDecisionResponse(
        boolean success,
        String approvalId,
        String status,
        String sessionId,
        String storeId,
        String traceId,
        String decidedBy,
        String decisionReason,
        Instant decidedAt,
        String message
) {
}
