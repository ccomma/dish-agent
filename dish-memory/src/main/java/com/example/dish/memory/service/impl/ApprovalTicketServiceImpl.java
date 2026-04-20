package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketCommandResult;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryResult;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.support.ApprovalTicketCodec;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@DubboService(interfaceClass = ApprovalTicketService.class, timeout = 5000, retries = 0)
public class ApprovalTicketServiceImpl implements ApprovalTicketService {

    private static final Map<String, ApprovalTicket> TICKETS = new ConcurrentHashMap<>();

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Value("${memory.retrieval.vector-dim:128}")
    private int vectorDim = 128;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public ApprovalTicketCommandResult create(ApprovalTicketCreateRequest request) {
        if (request == null || blank(request.tenantId()) || blank(request.sessionId()) || blank(request.approvalId())) {
            return new ApprovalTicketCommandResult(false, null, "invalid approval create request");
        }

        ApprovalTicket ticket = new ApprovalTicket(
                request.approvalId(),
                request.traceId(),
                request.nodeId(),
                ApprovalTicketStatus.PENDING,
                request.requestedBy(),
                null,
                request.decisionReason(),
                Instant.now(),
                null,
                metadataMap(
                        "targetAgent", request.targetAgent(),
                        "intent", request.intent(),
                        "sessionId", request.sessionId(),
                        "storeId", request.tenantId(),
                        "planId", request.planId()
                ),
                metadataMap("traceId", request.traceId())
        );

        saveTicket(request.tenantId(), request.sessionId(), ticket);
        writeApprovalTimeline(request.tenantId(), request.sessionId(), request.traceId(), ticket);
        return new ApprovalTicketCommandResult(true, ticket, "approval ticket created");
    }

    @Override
    public ApprovalTicketCommandResult decide(ApprovalTicketDecisionRequest request) {
        if (request == null || blank(request.tenantId()) || blank(request.sessionId()) || blank(request.approvalId()) || request.action() == null) {
            return new ApprovalTicketCommandResult(false, null, "invalid approval decision request");
        }

        ApprovalTicket current = findTicket(request.tenantId(), request.sessionId(), request.approvalId());
        if (current == null) {
            return new ApprovalTicketCommandResult(false, null, "approval ticket not found");
        }

        ApprovalTicketStatus nextStatus = request.action() == ApprovalDecisionAction.APPROVE
                ? ApprovalTicketStatus.APPROVED
                : ApprovalTicketStatus.REJECTED;

        ApprovalTicket updated = new ApprovalTicket(
                current.ticketId(),
                request.traceId() != null ? request.traceId() : current.executionId(),
                current.nodeId(),
                nextStatus,
                current.requestedBy(),
                request.decidedBy(),
                request.decisionReason(),
                current.createdAt(),
                Instant.now(),
                current.payload(),
                mergeMetadata(current.metadata(), request.traceId())
        );

        saveTicket(request.tenantId(), request.sessionId(), updated);
        writeApprovalTimeline(request.tenantId(), request.sessionId(), request.traceId(), updated);
        return new ApprovalTicketCommandResult(true, updated, nextStatus.name().toLowerCase() + " successfully");
    }

    @Override
    public ApprovalTicketQueryResult get(ApprovalTicketQueryRequest request) {
        if (request == null || blank(request.tenantId()) || blank(request.sessionId()) || blank(request.approvalId())) {
            return new ApprovalTicketQueryResult(false, null, "invalid approval query request");
        }

        ApprovalTicket ticket = findTicket(request.tenantId(), request.sessionId(), request.approvalId());
        if (ticket == null) {
            return new ApprovalTicketQueryResult(false, null, "approval ticket not found");
        }
        return new ApprovalTicketQueryResult(true, ticket, "ok");
    }

    static void clearForTest() {
        TICKETS.clear();
    }

    private void writeApprovalTimeline(String tenantId, String sessionId, String traceId, ApprovalTicket ticket) {
        MemoryReadServiceImpl.append(
                memoryMode,
                redisTemplate,
                vectorDim,
                tenantId,
                sessionId,
                "approval_ticket",
                ApprovalTicketCodec.encode(ticket),
                metadataMap(
                        "approvalId", ticket.ticketId(),
                        "status", ticket.status().name(),
                        "targetAgent", ticket.payload().get("targetAgent"),
                        "sessionId", sessionId,
                        "storeId", tenantId
                ),
                traceId
        );
    }

    private void saveTicket(String tenantId, String sessionId, ApprovalTicket ticket) {
        if (useRedis()) {
            MemoryReadServiceImpl.saveApproval(memoryMode, redisTemplate, tenantId, sessionId, ticket.ticketId(), ApprovalTicketCodec.encode(ticket));
            return;
        }
        TICKETS.put(key(tenantId, sessionId, ticket.ticketId()), ticket);
    }

    private ApprovalTicket findTicket(String tenantId, String sessionId, String approvalId) {
        if (useRedis()) {
            return ApprovalTicketCodec.decode(MemoryReadServiceImpl.loadApproval(memoryMode, redisTemplate, tenantId, sessionId, approvalId));
        }
        return TICKETS.get(key(tenantId, sessionId, approvalId));
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> current, String traceId) {
        Map<String, Object> metadata = new LinkedHashMap<>(current);
        if (!blank(traceId)) {
            metadata.put("traceId", traceId);
        }
        return metadata;
    }

    private Map<String, Object> metadataMap(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key instanceof String text && value != null) {
                metadata.put(text, value);
            }
        }
        return metadata;
    }

    private String key(String tenantId, String sessionId, String approvalId) {
        return tenantId + "::" + sessionId + "::" + approvalId;
    }

    private boolean useRedis() {
        return "redis".equalsIgnoreCase(memoryMode) && redisTemplate != null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
