package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketCommandResult;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryResult;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.memory.storage.ApprovalTicketStorage;
import com.example.dish.memory.storage.MemoryEntryStorage;
import com.example.dish.common.util.MetadataSupport;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 审批票据服务门面。
 *
 * <p>该类负责审批票据的创建、决策和查询，
 * 并把审批过程同步写入记忆时间线，保证控制台和回放链路都能看到审批状态。</p>
 */
@Service
@DubboService(interfaceClass = ApprovalTicketService.class, timeout = 5000, retries = 0)
public class ApprovalTicketServiceImpl implements ApprovalTicketService {

    @Autowired
    private MemoryEntryStorage memoryEntryStorage;

    @Autowired
    private ApprovalTicketStorage approvalTicketStorage;

    @Override
    @DubboProviderSpan("approval.create")
    public ApprovalTicketCommandResult create(ApprovalTicketCreateRequest request) {
        // 1. 校验审批创建请求的租户、会话和审批编号。
        if (request == null || StringUtils.isBlank(request.tenantId()) || StringUtils.isBlank(request.sessionId()) || StringUtils.isBlank(request.approvalId())) {
            return new ApprovalTicketCommandResult(false, null, "invalid approval create request");
        }

        // 2. 生成 PENDING 状态审批票据，并固化执行上下文元数据。
        ApprovalTicket ticket = new ApprovalTicket(
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

        // 3. 保存票据并追加审批时间线，保证查询和回放都能看到。
        saveTicket(request.tenantId(), request.sessionId(), ticket);
        writeApprovalTimeline(request.tenantId(), request.sessionId(), request.traceId(), ticket);
        return new ApprovalTicketCommandResult(true, ticket, "approval ticket created");
    }

    @Override
    @DubboProviderSpan("approval.decide")
    public ApprovalTicketCommandResult decide(ApprovalTicketDecisionRequest request) {
        // 1. 校验审批决策请求和动作。
        if (request == null || StringUtils.isBlank(request.tenantId()) || StringUtils.isBlank(request.sessionId()) || StringUtils.isBlank(request.approvalId()) || request.action() == null) {
            return new ApprovalTicketCommandResult(false, null, "invalid approval decision request");
        }

        // 2. 读取现有票据，找不到时直接返回失败。
        ApprovalTicket current = findTicket(request.tenantId(), request.sessionId(), request.approvalId());
        if (current == null) {
            return new ApprovalTicketCommandResult(false, null, "approval ticket not found");
        }

        // 3. 根据人工动作生成最终审批状态。
        ApprovalTicketStatus nextStatus = request.action() == ApprovalDecisionAction.APPROVE
                ? ApprovalTicketStatus.APPROVED
                : ApprovalTicketStatus.REJECTED;

        // 4. 保留原始上下文，补齐决策人、原因和 trace 元数据。
        ApprovalTicket updated = new ApprovalTicket(
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

        // 5. 覆盖保存票据并追加决策时间线。
        saveTicket(request.tenantId(), request.sessionId(), updated);
        writeApprovalTimeline(request.tenantId(), request.sessionId(), request.traceId(), updated);
        return new ApprovalTicketCommandResult(true, updated, nextStatus.name().toLowerCase() + " successfully");
    }

    @Override
    @DubboProviderSpan("approval.get")
    public ApprovalTicketQueryResult get(ApprovalTicketQueryRequest request) {
        // 1. 校验查询请求的租户、会话和审批编号。
        if (request == null || StringUtils.isBlank(request.tenantId()) || StringUtils.isBlank(request.sessionId()) || StringUtils.isBlank(request.approvalId())) {
            return new ApprovalTicketQueryResult(false, null, "invalid approval query request");
        }

        // 2. 从当前存储模式读取票据，并转换为查询结果。
        ApprovalTicket ticket = findTicket(request.tenantId(), request.sessionId(), request.approvalId());
        if (ticket == null) {
            return new ApprovalTicketQueryResult(false, null, "approval ticket not found");
        }
        return new ApprovalTicketQueryResult(true, ticket, "ok");
    }

    static void clearForTest() {
        ApprovalTicketStorage.clearForTest();
    }

    private void writeApprovalTimeline(String tenantId, String sessionId, String traceId, ApprovalTicket ticket) {
        // 把审批票据写成 APPROVAL 层时间线，供控制台查询和 execution 回放统一复用。
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

    private void saveTicket(String tenantId, String sessionId, ApprovalTicket ticket) {
        approvalTicketStorage.save(tenantId, sessionId, ticket);
    }

    private ApprovalTicket findTicket(String tenantId, String sessionId, String approvalId) {
        return approvalTicketStorage.find(tenantId, sessionId, approvalId);
    }

}
