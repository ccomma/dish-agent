package com.example.dish.control.approval.model;

import java.io.Serializable;

public record ApprovalTicketCreateRequest(
        String approvalId,
        String executionId,
        String tenantId,
        String sessionId,
        String traceId,
        String nodeId,
        String targetAgent,
        String intent,
        String planId,
        String requestedBy,
        String decisionReason
) implements Serializable {
}
