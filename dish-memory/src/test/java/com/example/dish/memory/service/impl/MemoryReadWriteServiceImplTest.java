package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

class MemoryReadWriteServiceImplTest {

    private LongTermMemoryVectorStore longTermMemoryVectorStore;

    @BeforeEach
    void setUp() {
        longTermMemoryVectorStore = new LongTermMemoryVectorStore();
        longTermMemoryVectorStore.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
        MemoryReadServiceImpl.clearForTest();
    }

    @AfterEach
    void tearDown() {
        longTermMemoryVectorStore.clearForTest();
        MemoryReadServiceImpl.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
    }

    @Test
    void shouldWriteAndReadBySession() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();
        MemoryReadServiceImpl readService = newReadService();

        boolean ok = writeService.write(new MemoryWriteRequest(
                "STORE-1",
                "SESSION-1",
                MemoryLayer.SHORT_TERM_SESSION,
                "execution_summary",
                "refund flow entered approval",
                Map.of(),
                "trace-1"
        ));

        MemoryReadResult result = readService.read(new MemoryReadRequest(
                "STORE-1",
                "SESSION-1",
                "refund",
                List.of(MemoryLayer.SHORT_TERM_SESSION),
                5,
                "trace-2"
        ));

        Assertions.assertTrue(ok);
        Assertions.assertTrue(result.hit());
        Assertions.assertEquals(1, result.snippets().size());
        Assertions.assertEquals("refund flow entered approval", result.snippets().get(0));
        Assertions.assertEquals("SHORT_TERM_SESSION", result.hits().get(0).memoryLayer().name());
    }

    @Test
    void shouldReturnNewestFirstAndLimitToFive() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();
        MemoryReadServiceImpl readService = newReadService();

        for (int i = 1; i <= 6; i++) {
            writeService.write(new MemoryWriteRequest(
                    "STORE-2",
                    "SESSION-2",
                    MemoryLayer.SHORT_TERM_SESSION,
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
                List.of(MemoryLayer.SHORT_TERM_SESSION),
                5,
                "trace-read"
        ));

        Assertions.assertTrue(result.hit());
        Assertions.assertEquals(5, result.snippets().size());
        Assertions.assertEquals("record-6", result.snippets().get(0));
        Assertions.assertEquals("record-2", result.snippets().get(4));
    }

    @Test
    void shouldRejectInvalidWriteRequest() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();

        boolean ok = writeService.write(new MemoryWriteRequest(
                null,
                "SESSION-3",
                MemoryLayer.SHORT_TERM_SESSION,
                "execution_summary",
                "x",
                Map.of(),
                "trace"
        ));

        Assertions.assertFalse(ok);
    }

    @Test
    void shouldQueryTimelineByTypeMetadataAndLayer() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();
        MemoryTimelineServiceImpl timelineService = newTimelineService();

        writeService.write(new MemoryWriteRequest(
                "STORE-3",
                "SESSION-3",
                MemoryLayer.APPROVAL,
                "approval_ticket",
                "{\"ticketId\":\"APR-1\"}",
                Map.of("approvalId", "APR-1"),
                "trace-approval"
        ));
        writeService.write(new MemoryWriteRequest(
                "STORE-3",
                "SESSION-3",
                MemoryLayer.SHORT_TERM_SESSION,
                "execution_summary",
                "summary",
                Map.of("planId", "plan-1"),
                "trace-summary"
        ));

        MemoryTimelineResult result = timelineService.timeline(new MemoryTimelineRequest(
                "STORE-3",
                "SESSION-3",
                "approval_ticket",
                MemoryLayer.APPROVAL,
                null,
                Map.of("approvalId", "APR-1"),
                10,
                "trace-query"
        ));

        Assertions.assertEquals(1, result.total());
        Assertions.assertEquals("approval_ticket", result.entries().get(0).memoryType());
        Assertions.assertEquals("APR-1", result.entries().get(0).metadata().get("approvalId"));
        Assertions.assertEquals(MemoryLayer.APPROVAL, result.entries().get(0).memoryLayer());
    }

    @Test
    void shouldQueryTimelineAcrossAllSessionsForTenant() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();
        MemoryTimelineServiceImpl timelineService = newTimelineService();

        writeService.write(new MemoryWriteRequest("STORE-4", "SESSION-A", MemoryLayer.SHORT_TERM_SESSION, "execution_summary", "summary-a", Map.of("sessionId", "SESSION-A"), "trace-a"));
        writeService.write(new MemoryWriteRequest("STORE-4", "SESSION-B", MemoryLayer.SHORT_TERM_SESSION, "execution_summary", "summary-b", Map.of("sessionId", "SESSION-B"), "trace-b"));

        MemoryTimelineResult result = timelineService.timeline(new MemoryTimelineRequest(
                "STORE-4",
                null,
                "execution_summary",
                MemoryLayer.SHORT_TERM_SESSION,
                null,
                Map.of(),
                10,
                "trace-query"
        ));

        Assertions.assertEquals(2, result.total());
    }

    @Test
    void shouldRetrieveLongTermKnowledgeFromVectorStore() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();
        MemoryReadServiceImpl readService = newReadService();

        writeService.write(new MemoryWriteRequest(
                "GLOBAL",
                "KNOWLEDGE_BOOTSTRAP",
                MemoryLayer.LONG_TERM_KNOWLEDGE,
                "knowledge_bootstrap",
                "退款进入人工审批后，需要记录门店经理审核结果。",
                Map.of("sourceFile", "knowledge.md"),
                "trace-knowledge"
        ));

        MemoryReadResult result = readService.read(new MemoryReadRequest(
                "STORE-5",
                "SESSION-5",
                "经理审核退款要记录什么",
                List.of(MemoryLayer.LONG_TERM_KNOWLEDGE),
                5,
                "trace-read"
        ));

        Assertions.assertTrue(result.hit());
        Assertions.assertTrue(result.hits().get(0).retrievalSource().contains("long-term"));
        Assertions.assertTrue(result.hits().get(0).content().contains("门店经理"));
    }

    @Test
    void shouldRetrieveHybridResultsAcrossLayers() throws Exception {
        MemoryWriteServiceImpl writeService = newWriteService();
        MemoryReadServiceImpl readService = newReadService();

        writeService.write(new MemoryWriteRequest(
                "STORE-6",
                "SESSION-6",
                MemoryLayer.SHORT_TERM_SESSION,
                "execution_summary",
                "退款申请进入人工审批，等待门店经理审核",
                Map.of("sessionId", "SESSION-6"),
                "trace-6"
        ));
        writeService.write(new MemoryWriteRequest(
                "STORE-6",
                "SESSION-6",
                MemoryLayer.LONG_TERM_KNOWLEDGE,
                "operational_knowledge",
                "长期经验：审批通过后要沉淀处理经验到长期知识记忆。",
                Map.of("sessionId", "SESSION-6"),
                "trace-7"
        ));

        MemoryReadResult result = readService.read(new MemoryReadRequest(
                "STORE-6",
                "SESSION-6",
                "审批经验和经理审核进展怎么样",
                List.of(MemoryLayer.SHORT_TERM_SESSION, MemoryLayer.LONG_TERM_KNOWLEDGE),
                5,
                "trace-read"
        ));

        Assertions.assertTrue(result.hit());
        Assertions.assertTrue(result.hits().size() >= 2);
        Assertions.assertTrue(result.source().contains("long-term") || result.source().contains("short-term"));
    }

    private MemoryWriteServiceImpl newWriteService() throws Exception {
        MemoryWriteServiceImpl service = new MemoryWriteServiceImpl();
        inject(service, "longTermMemoryVectorStore", longTermMemoryVectorStore);
        return service;
    }

    private MemoryReadServiceImpl newReadService() throws Exception {
        MemoryReadServiceImpl service = new MemoryReadServiceImpl();
        inject(service, "longTermMemoryVectorStore", longTermMemoryVectorStore);
        return service;
    }

    private MemoryTimelineServiceImpl newTimelineService() {
        return new MemoryTimelineServiceImpl();
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
