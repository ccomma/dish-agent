package com.example.dish.control.approval.model;

import java.io.Serializable;

public record ApprovalTicketQueryRequest(
        String approvalId,
        String tenantId,
        String sessionId,
        String traceId
) implements Serializable {
}
