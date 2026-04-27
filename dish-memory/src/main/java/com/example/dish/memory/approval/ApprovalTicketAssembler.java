package com.example.dish.memory.approval;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.common.util.MetadataSupport;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;

import java.time.Instant;

/**
 * 审批票据装配器。
 * 负责从创建请求中固化审批票据的 payload / metadata，Provider 门面不直接拼装领域对象。
 */
public class ApprovalTicketAssembler {

    public ApprovalTicket createPending(ApprovalTicketCreateRequest request) {
        return new ApprovalTicket(
                request.approvalId(),
                request.executionId(),
                request.nodeId(),
                ApprovalTicketStatus.PENDING,
                request.requestedBy(),
                null,
                request.decisionReason(),
                Instant.now(),
                null,
                MetadataSupport.mapOfNonNull(
                        "targetAgent", request.targetAgent(),
                        "intent", request.intent(),
                        "sessionId", request.sessionId(),
                        "storeId", request.tenantId(),
                        "planId", request.planId(),
                        "executionId", request.executionId()
                ),
                MetadataSupport.mapOfNonNull("traceId", request.traceId())
        );
    }
}
