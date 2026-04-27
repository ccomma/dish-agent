package com.example.dish.memory.approval;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.common.util.MetadataSupport;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;

import java.time.Instant;

/**
 * 审批决策应用器。
 * 负责把人工审批动作应用到现有票据，并保留原始执行上下文。
 */
public class ApprovalDecisionApplier {

    public ApprovalTicket apply(ApprovalTicket current, ApprovalTicketDecisionRequest request) {
        ApprovalTicketStatus nextStatus = request.action() == ApprovalDecisionAction.APPROVE
                ? ApprovalTicketStatus.APPROVED
                : ApprovalTicketStatus.REJECTED;

        return new ApprovalTicket(
                current.ticketId(),
                current.executionId(),
                current.nodeId(),
                nextStatus,
                current.requestedBy(),
                request.decidedBy(),
                request.decisionReason(),
                current.createdAt(),
                Instant.now(),
                current.payload(),
                MetadataSupport.mergeTraceId(current.metadata(), request.traceId())
        );
    }
}
