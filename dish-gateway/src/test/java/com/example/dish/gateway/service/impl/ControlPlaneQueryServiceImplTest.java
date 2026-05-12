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
import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionLatestQueryRequest;
import com.example.dish.control.execution.model.ExecutionNodeView;
import com.example.dish.control.execution.model.ExecutionReplayResult;
import com.example.dish.control.execution.service.ExecutionRuntimeReadService;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryRetrievalHit;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.memory.service.MemoryTimelineService;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;
import com.example.dish.gateway.observability.ExecutionMetricsService;
import com.example.dish.gateway.service.ExecutionResumeService;
import com.example.dish.gateway.support.DashboardOverviewAssembler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class ControlPlaneQueryServiceImplTest {

    @Test
    void shouldReturnLatestExecutionAndReplay() throws Exception {
        ControlPlaneQueryServiceImpl service = newService();
        ExecutionGraphViewResult graph = new ExecutionGraphViewResult(
                "exec-100",
                "plan-100",
                "SESSION-100",
                "STORE-100",
                "trace-100",
                "QUERY_ORDER",
                "serial",
                com.example.dish.common.runtime.ExecutionNodeStatus.RUNNING,
                Instant.parse("2026-04-21T10:15:30Z"),
                null,
                3200L,
                Map.of(),
                null,
                "work-order",
                0.0,
                List.of(new ExecutionNodeView(
                        "node-1",
                        "work-order",
                        "AGENT_CALL",
                        com.example.dish.common.runtime.ExecutionNodeStatus.RUNNING,
                        false,
                        "medium",
                        1200L,
                        "running",
                        null,
                        Instant.parse("2026-04-21T10:15:32Z"),
                        Map.of("stepIndex", 1)
                )),
                List.of(),
                2
        );
        inject(service, "executionRuntimeReadService", new ExecutionRuntimeReadService() {
            @Override
            public ExecutionGraphViewResult latest(ExecutionLatestQueryRequest request) {
                return graph;
            }

            @Override
            public ExecutionGraphViewResult graph(com.example.dish.control.execution.model.ExecutionGraphQueryRequest request) {
                return graph;
            }

            @Override
            public ExecutionReplayResult replay(com.example.dish.control.execution.model.ExecutionReplayQueryRequest request) {
                return new ExecutionReplayResult(
                        "exec-100",
                        "plan-100",
                        com.example.dish.common.runtime.ExecutionNodeStatus.RUNNING,
                        graph.startedAt(),
                        null,
                        3200L,
                        1,
                        List.of(new ExecutionEventStreamItem(
                                "evt-1",
                                "exec-100",
                                "plan-100",
                                "node-1",
                                com.example.dish.common.runtime.ExecutionNodeStatus.RUNNING,
                                Instant.parse("2026-04-21T10:15:32Z"),
                                Map.of("responseSummary", "dispatch started"),
                                Map.of("traceId", "trace-100")
                        ))
                );
            }
        });

        Assertions.assertEquals("exec-100", service.getLatestExecution("STORE-100", "SESSION-100", "trace-100").executionId());
        Assertions.assertEquals(1, service.getExecutionReplay("STORE-100", "exec-100", "trace-100").totalEvents());
    }

    @Test
    void shouldReturnSessionTimeline() throws Exception {
        ControlPlaneQueryServiceImpl service = newService();
        inject(service, "memoryTimelineService", (MemoryTimelineService) request -> new MemoryTimelineResult(
                List.of(new MemoryTimelineEntry(
                        "execution_summary",
                        MemoryLayer.SHORT_TERM_SESSION,
                        "summary",
                        Map.of("planId", "plan-1"),
                        "trace-1",
                        Instant.parse("2026-04-21T10:15:30Z"),
                        2L,
                        "redis-short-term"
                )),
                "stub",
                1
        ));

        SessionMemoryTimelineResponse response = service.getSessionTimeline("STORE-1", "SESSION-1", null, null, 20, "trace-query");

        Assertions.assertEquals(1, response.total());
        Assertions.assertEquals("execution_summary", response.entries().get(0).memoryType());
        Assertions.assertEquals("SHORT_TERM_SESSION", response.entries().get(0).memoryLayer());
        Assertions.assertEquals("plan-1", response.entries().get(0).metadata().get("planId"));
    }

    @Test
    void shouldReturnRetrievalExplainability() throws Exception {
        ControlPlaneQueryServiceImpl service = newService();
        inject(service, "memoryReadService", (MemoryReadService) request -> new MemoryReadResult(
                List.of("长期经验：审批通过后沉淀知识"),
                "milvus:dish_memory_long_term",
                true,
                List.of(new MemoryRetrievalHit(
                        "operational_knowledge",
                        MemoryLayer.LONG_TERM_KNOWLEDGE,
                        "长期经验：审批通过后沉淀知识",
                        Map.of("sourceFile", "guide.md"),
                        "trace-memory",
                        Instant.parse("2026-04-21T10:15:30Z"),
                        9L,
                        "milvus:dish_memory_long_term",
                        0.92,
                        0.48,
                        0.97,
                        0.06,
                        "layer=LONG_TERM_KNOWLEDGE, source=milvus:dish_memory_long_term, keyword=0.480, vector=0.970, recency=0.060"
                ))
        ));

        SessionMemoryRetrievalResponse response = service.retrieveSessionMemory(
                "STORE-1", "SESSION-1", "审批经验", "LONG_TERM_KNOWLEDGE", 5, "trace-query"
        );

        Assertions.assertEquals(1, response.total());
        Assertions.assertEquals("LONG_TERM_KNOWLEDGE", response.hits().get(0).memoryLayer());
        Assertions.assertTrue(response.hits().get(0).explanation().contains("vector"));
    }

    @Test
    void shouldParseApprovalTicketFromTimeline() throws Exception {
        ControlPlaneQueryServiceImpl service = newService();
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
        ControlPlaneQueryServiceImpl service = newService();
        AtomicBoolean resumed = new AtomicBoolean(false);
        inject(service, "executionMetricsService", new ExecutionMetricsService(new SimpleMeterRegistry()));
        ApprovalTicket ticket = new ApprovalTicket(
                "APR-3001",
                "exec-3001",
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
        inject(service, "executionResumeService", new ExecutionResumeService() {
            @Override
            public void resumeApprovedExecution(String storeId, String sessionId, String executionId, String traceId) {
                resumed.set(true);
            }

            @Override
            public void rejectExecution(String storeId, String sessionId, String executionId, String traceId, String reason) {
                throw new UnsupportedOperationException();
            }
        });

        ApprovalDecisionResponse response = service.decideApprovalTicket(
                "STORE-3", "SESSION-3", "APR-3001", ApprovalDecisionAction.APPROVE, "ops-user", "approved manually", "trace-3001"
        );

        Assertions.assertTrue(response.success());
        Assertions.assertEquals("APPROVED", response.status());
        Assertions.assertEquals("ops-user", response.decidedBy());
        Assertions.assertTrue(resumed.get());
    }

    @Test
    void shouldBuildDashboardOverview() throws Exception {
        ControlPlaneQueryServiceImpl service = newService();
        inject(service, "dashboardOverviewAssembler", new DashboardOverviewAssembler());
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
                        new MemoryTimelineEntry("approval_ticket", MemoryLayer.APPROVAL, ApprovalTicketCodec.encode(pending), Map.of("approvalId", "APR-4001", "status", "PENDING", "sessionId", "SESSION-A"), "trace-4001", Instant.parse("2026-04-21T09:15:30Z"), 3L, "redis-approval"),
                        new MemoryTimelineEntry("approval_ticket", MemoryLayer.APPROVAL, ApprovalTicketCodec.encode(approved), Map.of("approvalId", "APR-4002", "status", "APPROVED", "sessionId", "SESSION-B"), "trace-4002", Instant.parse("2026-04-21T08:20:30Z"), 2L, "redis-approval"),
                        new MemoryTimelineEntry("execution_summary", MemoryLayer.SHORT_TERM_SESSION, "summary", Map.of("sessionId", "SESSION-A", "planId", "plan-1"), "trace-4003", Instant.parse("2026-04-21T09:18:30Z"), 4L, "redis-short-term")
                ),
                "stub",
                3
        ));
        inject(service, "executionRuntimeReadService", new ExecutionRuntimeReadService() {
            @Override
            public ExecutionGraphViewResult latest(ExecutionLatestQueryRequest request) {
                if ("SESSION-A".equals(request.sessionId())) {
                    return new ExecutionGraphViewResult(
                            "exec-a",
                            "plan-a",
                            "SESSION-A",
                            "STORE-4",
                            "trace-exec-a",
                            "QUERY_ORDER",
                            "serial",
                            com.example.dish.common.runtime.ExecutionNodeStatus.WAITING_APPROVAL,
                            Instant.parse("2026-04-21T09:17:00Z"),
                            null,
                            1000L,
                            Map.of(),
                            null,
                            null,
                            0.0,
                            List.of(),
                            List.of(),
                            3
                    );
                }
                return new ExecutionGraphViewResult(
                        "exec-b",
                        "plan-b",
                        request.sessionId(),
                        "STORE-4",
                        "trace-exec-b",
                        "GENERAL_CHAT",
                        "single",
                        com.example.dish.common.runtime.ExecutionNodeStatus.RUNNING,
                        Instant.parse("2026-04-21T08:17:00Z"),
                        null,
                        2000L,
                        Map.of(),
                        null,
                        null,
                        0.0,
                        List.of(),
                        List.of(),
                        2
                );
            }

            @Override
            public ExecutionGraphViewResult graph(com.example.dish.control.execution.model.ExecutionGraphQueryRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ExecutionReplayResult replay(com.example.dish.control.execution.model.ExecutionReplayQueryRequest request) {
                throw new UnsupportedOperationException();
            }
        });

        ControlDashboardOverviewResponse response = service.getDashboardOverview("STORE-4", 10, "trace-dashboard");

        Assertions.assertEquals(2, response.totalSessions());
        Assertions.assertEquals(1, response.runningExecutionCount());
        Assertions.assertEquals(1, response.waitingApprovalExecutionCount());
        Assertions.assertEquals(1, response.pendingApprovalCount());
        Assertions.assertEquals(1, response.approvedApprovalCount());
        Assertions.assertTrue(response.memoryTypeBreakdown().containsKey("execution_summary"));
        Assertions.assertTrue(response.memoryLayerBreakdown().containsKey("APPROVAL"));
        Assertions.assertEquals(2, response.recentApprovals().size());
        Assertions.assertEquals("exec-a", response.activeSessions().get(0).latestExecutionId());
    }

    private ControlPlaneQueryServiceImpl newService() throws Exception {
        ControlPlaneQueryServiceImpl service = new ControlPlaneQueryServiceImpl();
        inject(service, "controlPlaneViewAssembler", new com.example.dish.gateway.support.ControlPlaneViewAssembler());
        return service;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
