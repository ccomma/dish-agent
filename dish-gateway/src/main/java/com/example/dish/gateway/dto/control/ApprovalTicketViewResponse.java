package com.example.dish.gateway.dto.control;

import java.time.Instant;

public record ApprovalTicketViewResponse(
        String approvalId,
        String sessionId,
        String storeId,
        String traceId,
        String status,
        String targetAgent,
        String requestedBy,
        String decisionReason,
        Instant createdAt,
        boolean found
) {
}
