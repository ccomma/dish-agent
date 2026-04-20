package com.example.dish.gateway.service.impl;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketCommandResult;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryResult;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.service.MemoryTimelineService;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class ControlPlaneQueryServiceImplTest {

    @Test
    void shouldReturnSessionTimeline() throws Exception {
        ControlPlaneQueryServiceImpl service = new ControlPlaneQueryServiceImpl();
        inject(service, "memoryTimelineService", (MemoryTimelineService) request -> new MemoryTimelineResult(
                List.of(new MemoryTimelineEntry(
                        "execution_summary",
                        "summary",
                        Map.of("planId", "plan-1"),
                        "trace-1",
                        Instant.parse("2026-04-21T10:15:30Z"),
                        2L
                )),
                "stub",
                1
        ));

        SessionMemoryTimelineResponse response = service.getSessionTimeline("STORE-1", "SESSION-1", null, null, 20, "trace-query");

        Assertions.assertEquals(1, response.total());
        Assertions.assertEquals("execution_summary", response.entries().get(0).memoryType());
        Assertions.assertEquals("plan-1", response.entries().get(0).metadata().get("planId"));
    }

    @Test
    void shouldParseApprovalTicketFromTimeline() throws Exception {
        ControlPlaneQueryServiceImpl service = new ControlPlaneQueryServiceImpl();
        ApprovalTicket ticket = new ApprovalTicket(
                "APR-2001",
                "trace-2001",
                "step-1",
                ApprovalTicketStatus.PENDING,
                "gateway",
                null,
                "policy require approval",
                Instant.parse("2026-04-21T10:15:30Z"),
                null,
                Map.of("targetAgent", "work-order"),
                Map.of("traceId", "trace-2001")
        );
        inject(service, "approvalTicketService", new ApprovalTicketService() {
            @Override
            public ApprovalTicketCommandResult create(ApprovalTicketCreateRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApprovalTicketCommandResult decide(ApprovalTicketDecisionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApprovalTicketQueryResult get(ApprovalTicketQueryRequest request) {
                return new ApprovalTicketQueryResult(true, ticket, "ok");
            }
        });

        ApprovalTicketViewResponse response = service.getApprovalTicket("STORE-2", "SESSION-2", "APR-2001", "trace-query");

        Assertions.assertTrue(response.found());
        Assertions.assertEquals("PENDING", response.status());
        Assertions.assertEquals("work-order", response.targetAgent());
    }

    @Test
    void shouldDecideApprovalTicket() throws Exception {
        ControlPlaneQueryServiceImpl service = new ControlPlaneQueryServiceImpl();
        ApprovalTicket ticket = new ApprovalTicket(
                "APR-3001",
                "trace-3001",
                "step-1",
                ApprovalTicketStatus.APPROVED,
                "gateway",
                "ops-user",
                "approved manually",
                Instant.parse("2026-04-21T10:15:30Z"),
                Instant.parse("2026-04-21T10:18:30Z"),
                Map.of("targetAgent", "work-order"),
                Map.of("traceId", "trace-3001")
        );
        inject(service, "approvalTicketService", new ApprovalTicketService() {
            @Override
            public ApprovalTicketCommandResult create(ApprovalTicketCreateRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApprovalTicketCommandResult decide(ApprovalTicketDecisionRequest request) {
                return new ApprovalTicketCommandResult(true, ticket, "approved");
            }

            @Override
            public ApprovalTicketQueryResult get(ApprovalTicketQueryRequest request) {
                throw new UnsupportedOperationException();
            }
        });

        ApprovalDecisionResponse response = service.decideApprovalTicket(
                "STORE-3", "SESSION-3", "APR-3001", ApprovalDecisionAction.APPROVE, "ops-user", "approved manually", "trace-3001"
        );

        Assertions.assertTrue(response.success());
        Assertions.assertEquals("APPROVED", response.status());
        Assertions.assertEquals("ops-user", response.decidedBy());
    }

    @Test
    void shouldBuildDashboardOverview() throws Exception {
        ControlPlaneQueryServiceImpl service = new ControlPlaneQueryServiceImpl();
        ApprovalTicket pending = new ApprovalTicket(
                "APR-4001",
                "trace-4001",
                "step-1",
                ApprovalTicketStatus.PENDING,
                "gateway",
                null,
                "wait",
                Instant.parse("2026-04-21T09:15:30Z"),
                null,
                Map.of("targetAgent", "work-order", "sessionId", "SESSION-A"),
                Map.of("traceId", "trace-4001")
        );
        ApprovalTicket approved = new ApprovalTicket(
                "APR-4002",
                "trace-4002",
                "step-2",
                ApprovalTicketStatus.APPROVED,
                "gateway",
                "ops",
                "done",
                Instant.parse("2026-04-21T08:15:30Z"),
                Instant.parse("2026-04-21T08:20:30Z"),
                Map.of("targetAgent", "work-order", "sessionId", "SESSION-B"),
                Map.of("traceId", "trace-4002")
        );
        inject(service, "memoryTimelineService", (MemoryTimelineService) request -> new MemoryTimelineResult(
                List.of(
                        new MemoryTimelineEntry("approval_ticket", ApprovalTicketCodec.encode(pending), Map.of("approvalId", "APR-4001", "status", "PENDING", "sessionId", "SESSION-A"), "trace-4001", Instant.parse("2026-04-21T09:15:30Z"), 3L),
                        new MemoryTimelineEntry("approval_ticket", ApprovalTicketCodec.encode(approved), Map.of("approvalId", "APR-4002", "status", "APPROVED", "sessionId", "SESSION-B"), "trace-4002", Instant.parse("2026-04-21T08:20:30Z"), 2L),
                        new MemoryTimelineEntry("execution_summary", "summary", Map.of("sessionId", "SESSION-A", "planId", "plan-1"), "trace-4003", Instant.parse("2026-04-21T09:18:30Z"), 4L)
                ),
                "stub",
                3
        ));

        ControlDashboardOverviewResponse response = service.getDashboardOverview("STORE-4", 10, "trace-dashboard");

        Assertions.assertEquals(2, response.totalSessions());
        Assertions.assertEquals(1, response.pendingApprovalCount());
        Assertions.assertEquals(1, response.approvedApprovalCount());
        Assertions.assertTrue(response.memoryTypeBreakdown().containsKey("execution_summary"));
        Assertions.assertEquals(2, response.recentApprovals().size());
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
