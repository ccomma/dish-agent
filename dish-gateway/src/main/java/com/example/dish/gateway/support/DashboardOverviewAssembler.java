package com.example.dish.gateway.support;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.DashboardApprovalItemResponse;
import com.example.dish.gateway.dto.control.DashboardSessionItemResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Dashboard 总览聚合器。
 * 只负责把租户时间线聚合成控制台 DTO，不直接访问 memory 或 execution runtime。
 */
@Component
public class DashboardOverviewAssembler {

    public ControlDashboardOverviewResponse assemble(String storeId,
                                                     String traceId,
                                                     List<MemoryTimelineEntry> entries,
                                                     int limit,
                                                     Function<DashboardSessionItemResponse, DashboardSessionItemResponse> executionEnricher) {
        Map<String, Integer> memoryTypeBreakdown = new LinkedHashMap<>();
        Map<String, Integer> memoryLayerBreakdown = new LinkedHashMap<>();
        Map<String, DashboardSessionItemResponse> latestSessionMap = new LinkedHashMap<>();
        Map<String, DashboardApprovalItemResponse> latestApprovalMap = new LinkedHashMap<>();

        // 1. 单次遍历时间线，聚合 memory breakdown、最近会话和最近审批项。
        for (MemoryTimelineEntry entry : entries) {
            memoryTypeBreakdown.merge(entry.memoryType(), 1, Integer::sum);
            if (entry.memoryLayer() != null) {
                memoryLayerBreakdown.merge(entry.memoryLayer().name(), 1, Integer::sum);
            }
            String sessionId = asString(entry.metadata().get("sessionId"));
            if (sessionId != null) {
                latestSessionMap.merge(sessionId, toSessionItem(entry), this::pickLatest);
            }
            if ("approval_ticket".equals(entry.memoryType())) {
                DashboardApprovalItemResponse approvalItem = toApprovalItem(entry);
                if (approvalItem != null) {
                    latestApprovalMap.merge(approvalItem.approvalId(), approvalItem, this::pickLatestApproval);
                }
            }
        }

        int pending = 0;
        int approved = 0;
        int rejected = 0;
        for (DashboardApprovalItemResponse approval : latestApprovalMap.values()) {
            if ("PENDING".equals(approval.status())) {
                pending++;
            } else if ("APPROVED".equals(approval.status())) {
                approved++;
            } else if ("REJECTED".equals(approval.status())) {
                rejected++;
            }
        }

        List<DashboardApprovalItemResponse> recentApprovals = latestApprovalMap.values().stream()
                .sorted(Comparator.comparing(DashboardApprovalItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit > 0 ? limit : 10)
                .toList();

        List<DashboardSessionItemResponse> activeSessions = latestSessionMap.values().stream()
                .map(executionEnricher)
                .sorted(Comparator.comparing(DashboardSessionItemResponse::lastUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit > 0 ? limit : 10)
                .toList();

        // 2. 基于补齐后的 session 运行态计算 execution 状态计数，再统一返回 dashboard 总览。
        int runningExecutionCount = (int) activeSessions.stream()
                .filter(item -> ExecutionNodeStatus.RUNNING.name().equals(item.latestExecutionStatus()))
                .count();
        int waitingApprovalExecutionCount = (int) activeSessions.stream()
                .filter(item -> ExecutionNodeStatus.WAITING_APPROVAL.name().equals(item.latestExecutionStatus()))
                .count();

        return new ControlDashboardOverviewResponse(
                storeId,
                traceId,
                latestSessionMap.size(),
                entries.size(),
                runningExecutionCount,
                waitingApprovalExecutionCount,
                pending,
                approved,
                rejected,
                memoryTypeBreakdown,
                memoryLayerBreakdown,
                recentApprovals,
                activeSessions
        );
    }

    private DashboardApprovalItemResponse toApprovalItem(MemoryTimelineEntry entry) {
        ApprovalTicket ticket = ApprovalTicketCodec.decode(entry.content());
        if (ticket == null) {
            return null;
        }
        return new DashboardApprovalItemResponse(
                ticket.ticketId(),
                asString(entry.metadata().get("sessionId")),
                ticket.status().name(),
                asString(ticket.payload().get("targetAgent")),
                ticket.requestedBy(),
                ticket.decidedBy(),
                ticket.createdAt(),
                ticket.decidedAt()
        );
    }

    private DashboardSessionItemResponse toSessionItem(MemoryTimelineEntry entry) {
        String sessionId = asString(entry.metadata().get("sessionId"));
        return new DashboardSessionItemResponse(
                sessionId,
                entry.memoryType(),
                entry.traceId(),
                entry.createdAt(),
                1,
                null,
                null
        );
    }

    private DashboardSessionItemResponse pickLatest(DashboardSessionItemResponse left, DashboardSessionItemResponse right) {
        DashboardSessionItemResponse latest = left.lastUpdatedAt() != null
                && right.lastUpdatedAt() != null
                && left.lastUpdatedAt().isAfter(right.lastUpdatedAt()) ? left : right;
        return new DashboardSessionItemResponse(
                latest.sessionId(),
                latest.lastMemoryType(),
                latest.lastTraceId(),
                latest.lastUpdatedAt(),
                left.eventCount() + right.eventCount(),
                latest.latestExecutionId(),
                latest.latestExecutionStatus()
        );
    }

    private DashboardApprovalItemResponse pickLatestApproval(DashboardApprovalItemResponse left, DashboardApprovalItemResponse right) {
        if (left.decidedAt() == null && right.decidedAt() == null) {
            return left.createdAt() != null && right.createdAt() != null && left.createdAt().isAfter(right.createdAt()) ? left : right;
        }
        if (left.decidedAt() == null) {
            return right;
        }
        if (right.decidedAt() == null) {
            return left;
        }
        return left.decidedAt().isAfter(right.decidedAt()) ? left : right;
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
