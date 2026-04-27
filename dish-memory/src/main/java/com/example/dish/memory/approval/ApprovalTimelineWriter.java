package com.example.dish.memory.approval;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.util.MetadataSupport;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.memory.storage.MemoryEntryStorage;

/**
 * 审批时间线写入器。
 * 负责把审批票据以稳定文本格式写入 APPROVAL 层时间线，供控制台查询和回放复用。
 */
public class ApprovalTimelineWriter {

    public void write(MemoryEntryStorage memoryEntryStorage,
                      String tenantId,
                      String sessionId,
                      String traceId,
                      ApprovalTicket ticket) {
        if (memoryEntryStorage == null || ticket == null) {
            return;
        }

        memoryEntryStorage.append(
                tenantId,
                sessionId,
                MemoryLayer.APPROVAL,
                "approval_ticket",
                ApprovalTicketCodec.encode(ticket),
                MetadataSupport.mapOfNonNull(
                        "approvalId", ticket.ticketId(),
                        "status", ticket.status().name(),
                        "targetAgent", ticket.payload().get("targetAgent"),
                        "sessionId", sessionId,
                        "storeId", tenantId
                ),
                traceId
        );
    }
}
