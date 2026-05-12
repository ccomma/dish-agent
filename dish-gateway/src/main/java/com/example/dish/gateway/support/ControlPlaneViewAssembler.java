package com.example.dish.gateway.support;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.control.approval.model.ApprovalTicketCommandResult;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryRetrievalHit;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryEntryResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalHitResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 控制面视图装配器。
 * 负责把 memory / approval 查询结果转换成控制台 DTO，查询门面只保留 RPC 编排。
 */
@Component
public class ControlPlaneViewAssembler {

    public SessionMemoryTimelineResponse toTimelineResponse(String storeId,
                                                            String sessionId,
                                                            MemoryTimelineResult result) {
        // 1. 把 memory timeline entry 转成控制台稳定展示结构。
        List<SessionMemoryEntryResponse> entries = result.entries().stream()
                .map(this::toMemoryEntryResponse)
                .toList();
        return new SessionMemoryTimelineResponse(sessionId, storeId, result.source(), result.total(), entries);
    }

    public SessionMemoryRetrievalResponse toRetrievalResponse(String storeId,
                                                              String sessionId,
                                                              MemoryReadResult result) {
        // 1. 保留召回解释分数字段，供 Semantic Recall Explorer 展示。
        List<SessionMemoryRetrievalHitResponse> hits = result.hits().stream()
                .map(this::toRetrievalHitResponse)
                .toList();
        return new SessionMemoryRetrievalResponse(sessionId, storeId, result.source(), hits.size(), hits);
    }

    public ApprovalTicketViewResponse notFoundApproval(String storeId,
                                                       String sessionId,
                                                       String approvalId,
                                                       String traceId) {
        return new ApprovalTicketViewResponse(
                approvalId,
                sessionId,
                storeId,
                traceId,
                "NOT_FOUND",
                null,
                null,
                null,
                null,
                false
        );
    }

    public ApprovalTicketViewResponse toApprovalView(String storeId,
                                                     String sessionId,
                                                     String traceId,
                                                     ApprovalTicket ticket) {
        Object targetAgent = ticket.payload().get("targetAgent");
        Object ticketTraceId = ticket.metadata().get("traceId");
        return new ApprovalTicketViewResponse(
                ticket.ticketId(),
                sessionId,
                storeId,
                ticketTraceId instanceof String text ? text : traceId,
                ticket.status().name(),
                targetAgent instanceof String text ? text : null,
                ticket.requestedBy(),
                ticket.decisionReason(),
                ticket.createdAt(),
                true
        );
    }

    public ApprovalDecisionResponse toApprovalDecisionResponse(String storeId,
                                                               String sessionId,
                                                               String approvalId,
                                                               String traceId,
                                                               String decidedBy,
                                                               String decisionReason,
                                                               ApprovalTicketCommandResult result) {
        ApprovalTicket ticket = result != null ? result.ticket() : null;
        return new ApprovalDecisionResponse(
                result != null && result.success(),
                approvalId,
                ticket != null ? ticket.status().name() : "UNKNOWN",
                sessionId,
                storeId,
                traceId,
                ticket != null ? ticket.decidedBy() : decidedBy,
                ticket != null ? ticket.decisionReason() : decisionReason,
                ticket != null ? ticket.decidedAt() : null,
                result != null ? result.message() : "approval decision service returned empty result"
        );
    }

    private SessionMemoryEntryResponse toMemoryEntryResponse(MemoryTimelineEntry entry) {
        return new SessionMemoryEntryResponse(
                entry.memoryType(),
                entry.memoryLayer() != null ? entry.memoryLayer().name() : null,
                entry.content(),
                entry.metadata(),
                entry.traceId(),
                entry.createdAt(),
                entry.sequence(),
                entry.storageSource()
        );
    }

    private SessionMemoryRetrievalHitResponse toRetrievalHitResponse(MemoryRetrievalHit hit) {
        return new SessionMemoryRetrievalHitResponse(
                hit.memoryType(),
                hit.memoryLayer() != null ? hit.memoryLayer().name() : null,
                hit.content(),
                hit.metadata(),
                hit.traceId(),
                hit.createdAt(),
                hit.sequence(),
                hit.retrievalSource(),
                hit.totalScore(),
                hit.keywordScore(),
                hit.vectorScore(),
                hit.recencyScore(),
                hit.explanation()
        );
    }
}
