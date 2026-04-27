package com.example.dish.gateway.support;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.DashboardSessionItemResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class DashboardOverviewAssemblerTest {

    @Test
    void shouldKeepDashboardOverviewAggregationStable() {
        DashboardOverviewAssembler assembler = new DashboardOverviewAssembler();
        ApprovalTicket pending = new ApprovalTicket(
                "APR-5001",
                "trace-5001",
                "step-1",
                ApprovalTicketStatus.PENDING,
                "gateway",
                null,
                "wait",
                Instant.parse("2026-04-21T09:15:30Z"),
                null,
                Map.of("targetAgent", "work-order"),
                Map.of("traceId", "trace-5001")
        );
        ApprovalTicket rejectedOlder = new ApprovalTicket(
                "APR-5002",
                "trace-5002",
                "step-2",
                ApprovalTicketStatus.REJECTED,
                "gateway",
                "ops",
                "no",
                Instant.parse("2026-04-21T08:15:30Z"),
                Instant.parse("2026-04-21T08:20:30Z"),
                Map.of("targetAgent", "work-order"),
                Map.of("traceId", "trace-5002")
        );
        ApprovalTicket rejectedNewer = new ApprovalTicket(
                "APR-5002",
                "trace-5003",
                "step-2",
                ApprovalTicketStatus.APPROVED,
                "gateway",
                "ops",
                "yes",
                Instant.parse("2026-04-21T08:15:30Z"),
                Instant.parse("2026-04-21T08:25:30Z"),
                Map.of("targetAgent", "work-order"),
                Map.of("traceId", "trace-5003")
        );
        List<MemoryTimelineEntry> entries = List.of(
                new MemoryTimelineEntry("approval_ticket", MemoryLayer.APPROVAL, ApprovalTicketCodec.encode(pending), Map.of("sessionId", "SESSION-A"), "trace-5001", Instant.parse("2026-04-21T09:15:30Z"), 1L, "redis-approval"),
                new MemoryTimelineEntry("execution_summary", MemoryLayer.SHORT_TERM_SESSION, "summary-a", Map.of("sessionId", "SESSION-A"), "trace-5004", Instant.parse("2026-04-21T09:20:30Z"), 4L, "redis-short-term"),
                new MemoryTimelineEntry("approval_ticket", MemoryLayer.APPROVAL, ApprovalTicketCodec.encode(rejectedOlder), Map.of("sessionId", "SESSION-B"), "trace-5002", Instant.parse("2026-04-21T08:20:30Z"), 2L, "redis-approval"),
                new MemoryTimelineEntry("approval_ticket", MemoryLayer.APPROVAL, ApprovalTicketCodec.encode(rejectedNewer), Map.of("sessionId", "SESSION-B"), "trace-5003", Instant.parse("2026-04-21T08:25:30Z"), 3L, "redis-approval"),
                new MemoryTimelineEntry("operational_knowledge", MemoryLayer.LONG_TERM_KNOWLEDGE, "knowledge", Map.of(), "trace-5005", Instant.parse("2026-04-21T07:20:30Z"), 5L, "bootstrap")
        );

        ControlDashboardOverviewResponse response = assembler.assemble(
                "STORE-5",
                "trace-dashboard",
                entries,
                10,
                item -> new DashboardSessionItemResponse(
                        item.sessionId(),
                        item.lastMemoryType(),
                        item.lastTraceId(),
                        item.lastUpdatedAt(),
                        item.eventCount(),
                        "SESSION-A".equals(item.sessionId()) ? "exec-a" : "exec-b",
                        "SESSION-A".equals(item.sessionId())
                                ? ExecutionNodeStatus.WAITING_APPROVAL.name()
                                : ExecutionNodeStatus.RUNNING.name()
                )
        );

        Assertions.assertEquals("STORE-5", response.storeId());
        Assertions.assertEquals("trace-dashboard", response.traceId());
        Assertions.assertEquals(2, response.totalSessions());
        Assertions.assertEquals(5, response.totalMemoryEntries());
        Assertions.assertEquals(1, response.runningExecutionCount());
        Assertions.assertEquals(1, response.waitingApprovalExecutionCount());
        Assertions.assertEquals(1, response.pendingApprovalCount());
        Assertions.assertEquals(1, response.approvedApprovalCount());
        Assertions.assertEquals(0, response.rejectedApprovalCount());
        Assertions.assertEquals(3, response.memoryTypeBreakdown().get("approval_ticket"));
        Assertions.assertEquals(3, response.memoryLayerBreakdown().get("APPROVAL"));
        Assertions.assertEquals("APR-5001", response.recentApprovals().get(0).approvalId());
        Assertions.assertEquals("APPROVED", response.recentApprovals().get(1).status());
        Assertions.assertEquals("SESSION-A", response.activeSessions().get(0).sessionId());
        Assertions.assertEquals(2, response.activeSessions().get(0).eventCount());
        Assertions.assertEquals("exec-a", response.activeSessions().get(0).latestExecutionId());
    }
}
