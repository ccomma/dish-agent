package com.example.dish.control.approval.model;

import java.io.Serializable;

public record ApprovalTicketDecisionRequest(
        String approvalId,
        String tenantId,
        String sessionId,
        String traceId,
        ApprovalDecisionAction action,
        String decidedBy,
        String decisionReason
) implements Serializable {
}
