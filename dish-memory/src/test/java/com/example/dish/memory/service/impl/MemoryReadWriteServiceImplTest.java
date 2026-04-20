package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MemoryReadWriteServiceImplTest {

    @BeforeEach
    void setUp() {
        ApprovalTicketServiceImpl.clearForTest();
    }

    @AfterEach
    void tearDown() {
        MemoryReadServiceImpl.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
    }

    @Test
    void shouldWriteAndReadBySession() {
        MemoryWriteServiceImpl writeService = new MemoryWriteServiceImpl();
        MemoryReadServiceImpl readService = new MemoryReadServiceImpl();

        boolean ok = writeService.write(new MemoryWriteRequest(
                "STORE-1",
                "SESSION-1",
                "execution_summary",
                "refund flow entered approval",
                Map.of(),
                "trace-1"
        ));

        MemoryReadResult result = readService.read(new MemoryReadRequest(
                "STORE-1",
                "SESSION-1",
                "refund",
                "trace-2"
        ));

        Assertions.assertTrue(ok);
        Assertions.assertTrue(result.hit());
        Assertions.assertEquals(1, result.snippets().size());
        Assertions.assertEquals("refund flow entered approval", result.snippets().get(0));
    }

    @Test
    void shouldReturnNewestFirstAndLimitToFive() {
        MemoryWriteServiceImpl writeService = new MemoryWriteServiceImpl();
        MemoryReadServiceImpl readService = new MemoryReadServiceImpl();

        for (int i = 1; i <= 6; i++) {
            writeService.write(new MemoryWriteRequest(
                    "STORE-2",
                    "SESSION-2",
                    "execution_summary",
                    "record-" + i,
                    Map.of(),
                    "trace-" + i
            ));
        }

        MemoryReadResult result = readService.read(new MemoryReadRequest(
                "STORE-2",
                "SESSION-2",
                "record",
                "trace-read"
        ));

        Assertions.assertTrue(result.hit());
        Assertions.assertEquals(5, result.snippets().size());
        Assertions.assertEquals("record-6", result.snippets().get(0));
        Assertions.assertEquals("record-2", result.snippets().get(4));
    }

    @Test
    void shouldRejectInvalidWriteRequest() {
        MemoryWriteServiceImpl writeService = new MemoryWriteServiceImpl();

        boolean ok = writeService.write(new MemoryWriteRequest(
                null,
                "SESSION-3",
                "execution_summary",
                "x",
                Map.of(),
                "trace"
        ));

        Assertions.assertFalse(ok);
    }

    @Test
    void shouldQueryTimelineByTypeAndMetadata() {
        MemoryWriteServiceImpl writeService = new MemoryWriteServiceImpl();
        MemoryTimelineServiceImpl timelineService = new MemoryTimelineServiceImpl();

        writeService.write(new MemoryWriteRequest(
                "STORE-3",
                "SESSION-3",
                "approval_ticket",
                "{\"ticketId\":\"APR-1\"}",
                Map.of("approvalId", "APR-1"),
                "trace-approval"
        ));
        writeService.write(new MemoryWriteRequest(
                "STORE-3",
                "SESSION-3",
                "execution_summary",
                "summary",
                Map.of("planId", "plan-1"),
                "trace-summary"
        ));

        MemoryTimelineResult result = timelineService.timeline(new MemoryTimelineRequest(
                "STORE-3",
                "SESSION-3",
                "approval_ticket",
                null,
                Map.of("approvalId", "APR-1"),
                10,
                "trace-query"
        ));

        Assertions.assertEquals(1, result.total());
        Assertions.assertEquals("approval_ticket", result.entries().get(0).memoryType());
        Assertions.assertEquals("APR-1", result.entries().get(0).metadata().get("approvalId"));
    }

    @Test
    void shouldQueryTimelineAcrossAllSessionsForTenant() {
        MemoryWriteServiceImpl writeService = new MemoryWriteServiceImpl();
        MemoryTimelineServiceImpl timelineService = new MemoryTimelineServiceImpl();

        writeService.write(new MemoryWriteRequest("STORE-4", "SESSION-A", "execution_summary", "summary-a", Map.of("sessionId", "SESSION-A"), "trace-a"));
        writeService.write(new MemoryWriteRequest("STORE-4", "SESSION-B", "execution_summary", "summary-b", Map.of("sessionId", "SESSION-B"), "trace-b"));

        MemoryTimelineResult result = timelineService.timeline(new MemoryTimelineRequest(
                "STORE-4",
                null,
                "execution_summary",
                null,
                Map.of(),
                10,
                "trace-query"
        ));

        Assertions.assertEquals(2, result.total());
    }

    @Test
    void shouldRetrieveMemoryByHybridVectorScoring() {
        MemoryWriteServiceImpl writeService = new MemoryWriteServiceImpl();
        MemoryReadServiceImpl readService = new MemoryReadServiceImpl();

        writeService.write(new MemoryWriteRequest(
                "STORE-5",
                "SESSION-5",
                "execution_summary",
                "退款申请进入人工审批，等待门店经理审核",
                Map.of("sessionId", "SESSION-5"),
                "trace-5"
        ));
        writeService.write(new MemoryWriteRequest(
                "STORE-5",
                "SESSION-5",
                "execution_summary",
                "订单查询已完成，未触发审批",
                Map.of("sessionId", "SESSION-5"),
                "trace-6"
        ));

        MemoryReadResult result = readService.read(new MemoryReadRequest(
                "STORE-5",
                "SESSION-5",
                "经理审核进展怎么样",
                "trace-read"
        ));

        Assertions.assertTrue(result.hit());
        Assertions.assertEquals("redis+vector".equals(result.source()) || "in-memory+vector".equals(result.source()), true);
        Assertions.assertTrue(result.snippets().get(0).contains("人工审批"));
    }
}
