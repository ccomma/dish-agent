package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.common.runtime.ExecutionEvent;
import com.example.dish.control.execution.model.ExecutionEventAppendRequest;
import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphSnapshotWriteRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionLatestQueryRequest;
import com.example.dish.control.execution.model.ExecutionNodeView;
import com.example.dish.control.execution.model.ExecutionReplayQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayResult;
import com.example.dish.memory.runtime.ExecutionRuntimeGraphProjector;
import com.example.dish.memory.storage.ExecutionRuntimeStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ExecutionRuntimeServiceImplTest {

    private ExecutionRuntimeStorage executionRuntimeStorage;
    private ExecutionRuntimeGraphProjector executionRuntimeGraphProjector;

    @BeforeEach
    void setUp() {
        executionRuntimeStorage = new ExecutionRuntimeStorage();
        executionRuntimeGraphProjector = new ExecutionRuntimeGraphProjector();
        ExecutionRuntimeWriteServiceImpl.clearForTest();
    }

    @Test
    void shouldPersistGraphAndReplayOrderedEvents() throws Exception {
        ExecutionRuntimeWriteServiceImpl writeService = newWriteService();
        ExecutionRuntimeReadServiceImpl readService = newReadService();

        ExecutionGraphViewResult graph = new ExecutionGraphViewResult(
                "exec-1",
                "plan-1",
                "SESSION-1",
                "STORE-1",
                "trace-1",
                "QUERY_ORDER",
                "serial",
                ExecutionNodeStatus.PENDING,
                Instant.parse("2026-04-21T10:00:00Z"),
                null,
                0L,
                Map.of("userInput", "查询订单123"),
                List.of(new ExecutionNodeView(
                        "node-1",
                        "work-order",
                        "AGENT_CALL",
                        ExecutionNodeStatus.PENDING,
                        false,
                        "medium",
                        0L,
                        "planned",
                        null,
                        Instant.parse("2026-04-21T10:00:00Z"),
                        Map.of("stepIndex", 1, "stepCount", 1)
                )),
                List.of(),
                0
        );

        Assertions.assertTrue(writeService.initialize(new ExecutionGraphSnapshotWriteRequest("STORE-1", "SESSION-1", graph)));
        Assertions.assertTrue(writeService.appendEvent(new ExecutionEventAppendRequest(
                "STORE-1",
                "SESSION-1",
                new ExecutionEvent(
                        "evt-1",
                        "exec-1",
                        "plan-1",
                        "node-1",
                        ExecutionNodeStatus.RUNNING,
                        Instant.parse("2026-04-21T10:00:02Z"),
                        Map.of("responseSummary", "started"),
                        Map.of("sessionId", "SESSION-1", "storeId", "STORE-1", "traceId", "trace-1", "latencyMs", 25L)
                )
        )));
        Assertions.assertTrue(writeService.appendEvent(new ExecutionEventAppendRequest(
                "STORE-1",
                "SESSION-1",
                new ExecutionEvent(
                        "evt-2",
                        "exec-1",
                        "plan-1",
                        "node-1",
                        ExecutionNodeStatus.SUCCEEDED,
                        Instant.parse("2026-04-21T10:00:04Z"),
                        Map.of("responseSummary", "done"),
                        Map.of("sessionId", "SESSION-1", "storeId", "STORE-1", "traceId", "trace-1", "latencyMs", 240L)
                )
        )));

        ExecutionGraphViewResult latest = readService.latest(new ExecutionLatestQueryRequest("STORE-1", "SESSION-1", "trace-query"));
        ExecutionReplayResult replay = readService.replay(new ExecutionReplayQueryRequest("STORE-1", "exec-1", "trace-query"));
        ExecutionGraphViewResult graphView = readService.graph(new ExecutionGraphQueryRequest("STORE-1", "exec-1", "trace-query"));

        Assertions.assertEquals("exec-1", latest.executionId());
        Assertions.assertEquals(ExecutionNodeStatus.SUCCEEDED, graphView.overallStatus());
        Assertions.assertEquals(2, replay.totalEvents());
        Assertions.assertEquals("evt-1", replay.events().get(0).eventId());
        Assertions.assertEquals("evt-2", replay.events().get(1).eventId());
    }

    @Test
    void shouldKeepRuntimeStorageOutOfWriteServiceImplStatics() {
        boolean hasStaticMap = Arrays.stream(ExecutionRuntimeWriteServiceImpl.class.getDeclaredFields())
                .filter(field -> Map.class.isAssignableFrom(field.getType()))
                .anyMatch(field -> Modifier.isStatic(field.getModifiers()));
        Set<String> storageMethodNames = Set.of("loadState", "latestExecutionId");
        boolean hasStaticStorageMethod = Arrays.stream(ExecutionRuntimeWriteServiceImpl.class.getDeclaredMethods())
                .filter(method -> storageMethodNames.contains(method.getName()))
                .anyMatch(method -> Modifier.isStatic(method.getModifiers()));

        Assertions.assertFalse(hasStaticMap);
        Assertions.assertFalse(hasStaticStorageMethod);
    }

    @Test
    void shouldInjectRuntimeStorageAsBeanInsteadOfConstructingItInServices() {
        Assertions.assertFalse(fileContains("src/main/java/com/example/dish/memory/service/impl/ExecutionRuntimeWriteServiceImpl.java", "new ExecutionRuntimeStorage"));
        Assertions.assertFalse(fileContains("src/main/java/com/example/dish/memory/service/impl/ExecutionRuntimeReadServiceImpl.java", "new ExecutionRuntimeStorage"));
    }

    @Test
    void shouldMoveRuntimeHelpersAndTracingBoilerplateOutOfServiceImpls() {
        String writeSource = fileContent("src/main/java/com/example/dish/memory/service/impl/ExecutionRuntimeWriteServiceImpl.java");
        String readSource = fileContent("src/main/java/com/example/dish/memory/service/impl/ExecutionRuntimeReadServiceImpl.java");

        Assertions.assertFalse(writeSource.contains("durationMs("));
        Assertions.assertFalse(writeSource.contains("openProviderSpan"));
        Assertions.assertFalse(readSource.contains("openProviderSpan"));
        Assertions.assertFalse(readSource.contains("try (spanScope)"));
    }

    @Test
    void shouldMoveRuntimeProjectionOutOfServiceImplPackage() {
        Assertions.assertFalse(Files.exists(Path.of("src/main/java/com/example/dish/memory/service/impl/ExecutionRuntimeGraphProjector.java")));
        Assertions.assertTrue(Files.exists(Path.of("src/main/java/com/example/dish/memory/runtime/ExecutionRuntimeGraphProjector.java")));
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

    private ExecutionRuntimeWriteServiceImpl newWriteService() throws Exception {
        ExecutionRuntimeWriteServiceImpl service = new ExecutionRuntimeWriteServiceImpl();
        inject(service, "executionRuntimeStorage", executionRuntimeStorage);
        inject(service, "executionRuntimeGraphProjector", executionRuntimeGraphProjector);
        return service;
    }

    private ExecutionRuntimeReadServiceImpl newReadService() throws Exception {
        ExecutionRuntimeReadServiceImpl service = new ExecutionRuntimeReadServiceImpl();
        inject(service, "executionRuntimeStorage", executionRuntimeStorage);
        inject(service, "executionRuntimeGraphProjector", executionRuntimeGraphProjector);
        return service;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
