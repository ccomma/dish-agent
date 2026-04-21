package com.example.dish.gateway.observability;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExecutionMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeExecutions = new AtomicInteger();
    private final AtomicInteger runningExecutions = new AtomicInteger();
    private final AtomicInteger waitingApprovalExecutions = new AtomicInteger();
    private final AtomicInteger streamSubscribers = new AtomicInteger();

    private final Map<String, ExecutionNodeStatus> executionStatuses = new ConcurrentHashMap<>();
    private final Map<String, Integer> executionSubscribers = new ConcurrentHashMap<>();

    public ExecutionMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("dish.execution.active", activeExecutions, AtomicInteger::get)
                .description("Current active executions tracked by gateway mission control")
                .register(meterRegistry);
        Gauge.builder("dish.execution.running", runningExecutions, AtomicInteger::get)
                .description("Executions currently in RUNNING state")
                .register(meterRegistry);
        Gauge.builder("dish.execution.waiting_approval", waitingApprovalExecutions, AtomicInteger::get)
                .description("Executions currently paused for approval")
                .register(meterRegistry);
        Gauge.builder("dish.execution.stream.subscribers", streamSubscribers, AtomicInteger::get)
                .description("Active SSE subscribers for execution runtime streams")
                .register(meterRegistry);
    }

    public void recordExecutionStarted(ExecutionGraphViewResult graph) {
        if (graph == null || graph.executionId() == null) {
            return;
        }
        executionStatuses.put(graph.executionId(), ExecutionNodeStatus.PENDING);
        refreshExecutionGauges();
        Counter.builder("dish.execution.started.total")
                .description("Total execution runtimes started by gateway")
                .tag("intent", safe(graph.intent()))
                .tag("mode", safe(graph.executionMode()))
                .register(meterRegistry)
                .increment();
    }

    public void recordNodeStatus(String executionId, String targetAgent, ExecutionNodeStatus status, long latencyMs) {
        if (status == null) {
            return;
        }
        if (executionId != null && shouldUpdateExecutionState(status)) {
            executionStatuses.put(executionId, status);
            refreshExecutionGauges();
        }
        Counter.builder("dish.execution.node.transitions.total")
                .description("Execution node status transitions")
                .tag("target_agent", safe(targetAgent))
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();

        if (latencyMs > 0) {
            Timer.builder("dish.execution.node.latency")
                    .description("Latency for execution node dispatch and completion")
                    .tag("target_agent", safe(targetAgent))
                    .tag("status", status.name())
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
        }
    }

    public void recordExecutionOutcome(ExecutionGraphViewResult graph,
                                       ExecutionNodeStatus status,
                                       long durationMs,
                                       int executedSteps) {
        if (graph == null || status == null || graph.executionId() == null) {
            return;
        }
        executionStatuses.put(graph.executionId(), status);
        refreshExecutionGauges();

        Counter.builder("dish.execution.outcome.total")
                .description("Execution summaries emitted by gateway")
                .tag("intent", safe(graph.intent()))
                .tag("mode", safe(graph.executionMode()))
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();

        if (durationMs > 0) {
            Timer.builder("dish.execution.duration")
                    .description("Execution end-to-end duration from runtime start to summary")
                    .tag("intent", safe(graph.intent()))
                    .tag("mode", safe(graph.executionMode()))
                    .tag("status", status.name())
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }

        DistributionSummary.builder("dish.execution.step.count")
                .description("Executed step count per runtime summary")
                .tag("intent", safe(graph.intent()))
                .tag("mode", safe(graph.executionMode()))
                .tag("status", status.name())
                .register(meterRegistry)
                .record(executedSteps);
    }

    public void recordApprovalDecision(ApprovalDecisionAction action, boolean success) {
        if (action == null) {
            return;
        }
        Counter.builder("dish.execution.approval.decisions.total")
                .description("Approval decisions performed by mission control")
                .tag("action", action.name())
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    public void recordStreamOpened(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        executionSubscribers.merge(executionId, 1, Integer::sum);
        refreshSubscriberGauge();
    }

    public void recordStreamClosed(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        executionSubscribers.computeIfPresent(executionId, (key, value) -> value > 1 ? value - 1 : null);
        refreshSubscriberGauge();
    }

    private boolean shouldUpdateExecutionState(ExecutionNodeStatus status) {
        return status == ExecutionNodeStatus.RUNNING
                || status == ExecutionNodeStatus.WAITING_APPROVAL
                || status == ExecutionNodeStatus.FAILED
                || status == ExecutionNodeStatus.CANCELLED;
    }

    private void refreshExecutionGauges() {
        activeExecutions.set((int) executionStatuses.values().stream().filter(status -> !isTerminal(status)).count());
        runningExecutions.set((int) executionStatuses.values().stream().filter(status -> status == ExecutionNodeStatus.RUNNING).count());
        waitingApprovalExecutions.set((int) executionStatuses.values().stream().filter(status -> status == ExecutionNodeStatus.WAITING_APPROVAL).count());
    }

    private void refreshSubscriberGauge() {
        streamSubscribers.set(executionSubscribers.values().stream().mapToInt(Integer::intValue).sum());
    }

    private boolean isTerminal(ExecutionNodeStatus status) {
        return status == ExecutionNodeStatus.SUCCEEDED
                || status == ExecutionNodeStatus.FAILED
                || status == ExecutionNodeStatus.CANCELLED;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
