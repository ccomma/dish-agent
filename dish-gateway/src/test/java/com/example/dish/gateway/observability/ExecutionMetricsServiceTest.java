package com.example.dish.gateway.observability;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class ExecutionMetricsServiceTest {

    @Test
    void shouldTrackExecutionLifecycleAndStreams() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExecutionMetricsService service = new ExecutionMetricsService(registry);
        ExecutionGraphViewResult graph = new ExecutionGraphViewResult(
                "exec-metric-1",
                "plan-metric-1",
                "SESSION-METRIC",
                "STORE-METRIC",
                "trace-metric-1",
                "QUERY_ORDER",
                "serial",
                ExecutionNodeStatus.PENDING,
                Instant.parse("2026-04-21T10:00:00Z"),
                null,
                0L,
                Map.of(),
                List.of(),
                List.of(),
                0
        );

        service.recordExecutionStarted(graph);
        service.recordNodeStatus(graph.executionId(), "work-order", ExecutionNodeStatus.RUNNING, 125L);
        service.recordStreamOpened(graph.executionId());
        service.recordApprovalDecision(ApprovalDecisionAction.APPROVE, true);
        service.recordExecutionOutcome(graph, ExecutionNodeStatus.SUCCEEDED, 1500L, 2);
        service.recordStreamClosed(graph.executionId());

        Assertions.assertEquals(1.0, registry.get("dish.execution.started.total").counter().count());
        Assertions.assertEquals(1.0, registry.get("dish.execution.outcome.total").counter().count());
        Assertions.assertEquals(1.0, registry.get("dish.execution.approval.decisions.total").counter().count());
        Assertions.assertEquals(0.0, registry.get("dish.execution.active").gauge().value());
        Assertions.assertEquals(0.0, registry.get("dish.execution.stream.subscribers").gauge().value());
        Assertions.assertTrue(registry.get("dish.execution.node.latency").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 125L);
    }
}
