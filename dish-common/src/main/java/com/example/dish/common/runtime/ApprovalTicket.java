package com.example.dish.common.runtime;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 人工审批单。
 */
public record ApprovalTicket(
        String ticketId,
        String executionId,
        String nodeId,
        ApprovalTicketStatus status,
        String requestedBy,
        String decidedBy,
        String decisionReason,
        Instant createdAt,
        Instant decidedAt,
        Map<String, Object> payload,
        Map<String, Object> metadata
) implements Serializable {

    public ApprovalTicket {
        payload = payload == null ? Collections.emptyMap() : Map.copyOf(payload);
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
