package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.memory.storage.ApprovalTicketStorage;
import com.example.dish.memory.storage.MemoryEntryStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

class ApprovalTicketServiceImplTest {

    private MemoryEntryStorage memoryEntryStorage;
    private ApprovalTicketStorage approvalTicketStorage;

    @BeforeEach
    void setUp() throws Exception {
        memoryEntryStorage = new MemoryEntryStorage();
        approvalTicketStorage = new ApprovalTicketStorage();
        inject(approvalTicketStorage, "memoryEntryStorage", memoryEntryStorage);
        MemoryReadServiceImpl.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
    }

    @AfterEach
    void tearDown() {
        MemoryReadServiceImpl.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
    }

    @Test
    void shouldCreateQueryAndApproveTicket() throws Exception {
        ApprovalTicketServiceImpl service = newService();

        var created = service.create(new ApprovalTicketCreateRequest(
                "APR-5001",
                "exec-5001",
                "STORE-5",
                "SESSION-5",
                "trace-5001",
                "step-1",
                "work-order",
                "CREATE_REFUND",
                "plan-5001",
                "gateway",
                "need approval"
        ));

        Assertions.assertTrue(created.success());
        Assertions.assertEquals(ApprovalTicketStatus.PENDING, created.ticket().status());

        var queried = service.get(new ApprovalTicketQueryRequest("APR-5001", "STORE-5", "SESSION-5", "trace-query"));
        Assertions.assertTrue(queried.found());
        Assertions.assertEquals(ApprovalTicketStatus.PENDING, queried.ticket().status());

        var decided = service.decide(new ApprovalTicketDecisionRequest(
                "APR-5001",
                "STORE-5",
                "SESSION-5",
                "trace-5002",
                ApprovalDecisionAction.APPROVE,
                "ops-user",
                "approved"
        ));

        Assertions.assertTrue(decided.success());
        Assertions.assertEquals(ApprovalTicketStatus.APPROVED, decided.ticket().status());
        Assertions.assertEquals("ops-user", decided.ticket().decidedBy());
    }

    @Test
    void shouldKeepTicketStorageOutOfApprovalServiceImplStatics() {
        boolean hasStaticMap = Arrays.stream(ApprovalTicketServiceImpl.class.getDeclaredFields())
                .filter(field -> Map.class.isAssignableFrom(field.getType()))
                .anyMatch(field -> Modifier.isStatic(field.getModifiers()));

        Assertions.assertFalse(hasStaticMap);
    }

    @Test
    void shouldInjectTicketStorageAsBeanInsteadOfConstructingItInService() {
        Assertions.assertFalse(fileContains("src/main/java/com/example/dish/memory/service/impl/ApprovalTicketServiceImpl.java", "new ApprovalTicketStorage"));
        Assertions.assertFalse(fileContains("src/main/java/com/example/dish/memory/service/impl/ApprovalTicketServiceImpl.java", "new MemoryEntryStorage"));
    }

    @Test
    void shouldMoveMetadataMapAndTracingBoilerplateOutOfApprovalServiceImpl() {
        String source = fileContent("src/main/java/com/example/dish/memory/service/impl/ApprovalTicketServiceImpl.java");

        Assertions.assertFalse(source.contains("metadataMap("));
        Assertions.assertFalse(source.contains("openProviderSpan"));
        Assertions.assertFalse(source.contains("try (spanScope)"));
    }

    private boolean fileContains(String path, String text) {
        return fileContent(path).contains(text);
    }

    private String fileContent(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception ex) {
            throw new AssertionError("failed to read " + path, ex);
        }
    }

    private ApprovalTicketServiceImpl newService() throws Exception {
        ApprovalTicketServiceImpl service = new ApprovalTicketServiceImpl();
        inject(service, "memoryEntryStorage", memoryEntryStorage);
        inject(service, "approvalTicketStorage", approvalTicketStorage);
        return service;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
