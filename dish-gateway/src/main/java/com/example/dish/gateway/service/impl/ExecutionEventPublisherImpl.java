package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionEvent;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionEdgeView;
import com.example.dish.control.execution.model.ExecutionEventAppendRequest;
import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphSnapshotWriteRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionNodeView;
import com.example.dish.control.execution.service.ExecutionRuntimeWriteService;
import com.example.dish.gateway.observability.ExecutionMetricsService;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Resource;

/**
 * execution 事件发布器。
 * 负责初始化 execution graph、向 memory runtime 追加事件，并把事件广播给 SSE 订阅方。
 */
@Service
public class ExecutionEventPublisherImpl implements ExecutionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventPublisherImpl.class);

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private ExecutionRuntimeWriteService executionRuntimeWriteService;
    @Resource
    private ExecutionMetricsService executionMetricsService;

    @Override
    public ExecutionGraphViewResult startExecution(RoutingDecision routing, List<AgentExecutionStep> steps, String traceId) {
        // 1. 为本次执行生成 executionId，并根据步骤列表构造初始 graph。
        String executionId = "exec-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now();
        String sessionId = routing != null && routing.context() != null ? routing.context().getSessionId() : null;
        String storeId = routing != null && routing.context() != null ? routing.context().getStoreId() : null;

        List<ExecutionNodeView> nodes = new ArrayList<>();
        List<ExecutionEdgeView> edges = new ArrayList<>();
        int stepCount = steps != null ? steps.size() : 0;
        for (int i = 0; i < stepCount; i++) {
            AgentExecutionStep step = steps.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (step.metadata() != null) {
                metadata.putAll(step.metadata());
            }
            metadata.put("dependsOn", step.dependsOn());
            metadata.put("timeoutMs", step.timeoutMs());
            metadata.put("stepIndex", i + 1);
            metadata.put("stepCount", stepCount);
            metadata.put("targetAgent", step.targetAgent());

            nodes.add(new ExecutionNodeView(
                    step.stepId(),
                    step.targetAgent(),
                    step.nodeType(),
                    ExecutionNodeStatus.PENDING,
                    false,
                    asString(metadata.get("riskLevel")),
                    0L,
                    "planned",
                    null,
                    startedAt,
                    Map.copyOf(metadata)
            ));

            if (step.dependsOn() != null) {
                for (String dependency : step.dependsOn()) {
                    edges.add(new ExecutionEdgeView(
                            "edge-" + dependency + "-" + step.stepId(),
                            dependency,
                            step.stepId(),
                            "ON_SUCCESS",
                            Map.of()
                    ));
                }
            }
        }

        ExecutionGraphViewResult graph = new ExecutionGraphViewResult(
                executionId,
                routing != null ? routing.planId() : null,
                sessionId,
                storeId,
                traceId,
                routing != null && routing.intent() != null ? routing.intent().name() : null,
                routing != null ? routing.executionMode() : null,
                ExecutionNodeStatus.PENDING,
                startedAt,
                null,
                0L,
                graphMetadata(routing, traceId),
                routing != null && routing.context() != null ? routing.context().getUserInput() : null,
                routing != null ? routing.targetAgent() : null,
                routing != null ? routing.confidence() : 0.0,
                List.copyOf(nodes),
                List.copyOf(edges),
                0
        );

        // 2. 先把初始 graph 快照持久化，再记录 execution started 指标。
        executionRuntimeWriteService.initialize(new ExecutionGraphSnapshotWriteRequest(storeId, sessionId, graph));
        executionMetricsService.recordExecutionStarted(graph);

        // 3. 最后把每个步骤的 planned 事件补写到 runtime/replay 流里。
        for (int i = 0; i < stepCount; i++) {
            AgentExecutionStep step = steps.get(i);
            publishNodeStatus(graph, step, ExecutionNodeStatus.PENDING, i + 1, stepCount, traceId, "step planned", 0L, null, null);
        }

        return graph;
    }

    @Override
    public void publishNodeStatus(ExecutionGraphViewResult graph,
                                  AgentExecutionStep step,
                                  ExecutionNodeStatus status,
                                  int stepIndex,
                                  int stepCount,
                                  String traceId,
                                  String statusReason,
                                  long latencyMs,
                                  AgentResponse response,
                                  String approvalId) {
        if (graph == null || step == null) {
            return;
        }

        // 1. 组装当前节点事件的 payload 和 metadata。
        Instant occurredAt = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (response != null && response.getContent() != null) {
            payload.put("responseSummary", response.getContent());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", graph.sessionId());
        metadata.put("storeId", graph.storeId());
        metadata.put("traceId", traceId);
        metadata.put("targetAgent", step.targetAgent());
        metadata.put("intent", graph.intent());
        metadata.put("executionMode", graph.executionMode());
        metadata.put("stepIndex", stepIndex);
        metadata.put("stepCount", stepCount);
        metadata.put("statusReason", statusReason);
        metadata.put("latencyMs", latencyMs);
        if (approvalId != null) {
            metadata.put("approvalId", approvalId);
        }
        if (step.metadata() != null) {
            if (step.metadata().get("riskLevel") != null) {
                metadata.put("riskLevel", step.metadata().get("riskLevel"));
            }
            if (step.metadata().get("policyDecision") != null) {
                metadata.put("policyDecision", step.metadata().get("policyDecision"));
            }
        }

        // 2. 持久化事件，并同步广播到 SSE 订阅者。
        appendAndBroadcast(graph, new ExecutionEvent(
                "evt-" + UUID.randomUUID().toString().substring(0, 8),
                graph.executionId(),
                graph.planId(),
                step.stepId(),
                status,
                occurredAt,
                Map.copyOf(payload),
                Map.copyOf(metadata)
        ));
        // 3. 更新节点级指标。
        executionMetricsService.recordNodeStatus(graph.executionId(), step.targetAgent(), status, latencyMs);
    }

    @Override
    public void publishExecutionSummary(ExecutionGraphViewResult graph,
                                        ExecutionNodeStatus status,
                                        String traceId,
                                        String statusReason,
                                        long durationMs,
                                        int executedSteps) {
        if (graph == null) {
            return;
        }

        // execution summary 用特殊 nodeId=__execution__ 标记整条执行链的最终结果。
        Map<String, Object> payload = Map.of(
                "responseSummary", statusReason != null ? statusReason : "execution completed",
                "executedSteps", executedSteps
        );
        Map<String, Object> metadata = Map.of(
                "sessionId", graph.sessionId(),
                "storeId", graph.storeId(),
                "traceId", traceId,
                "intent", graph.intent(),
                "executionMode", graph.executionMode(),
                "statusReason", statusReason != null ? statusReason : "execution completed",
                "latencyMs", durationMs
        );

        appendAndBroadcast(graph, new ExecutionEvent(
                "evt-" + UUID.randomUUID().toString().substring(0, 8),
                graph.executionId(),
                graph.planId(),
                "__execution__",
                status,
                Instant.now(),
                payload,
                metadata
        ));
        executionMetricsService.recordExecutionOutcome(graph, status, durationMs, executedSteps);
    }

    @Override
    public SseEmitter subscribe(String executionId) {
        // 1. 为 execution 注册新的 SSE 订阅者。
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(executionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        executionMetricsService.recordStreamOpened(executionId);
        emitter.onCompletion(() -> removeEmitter(executionId, emitter));
        emitter.onTimeout(() -> removeEmitter(executionId, emitter));
        emitter.onError(ignored -> removeEmitter(executionId, emitter));
        try {
            // 2. 建立连接后立即发送 connected 事件，方便前端确认订阅成功。
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("executionId", executionId)));
        } catch (IOException ex) {
            removeEmitter(executionId, emitter);
        }
        return emitter;
    }

    private void appendAndBroadcast(ExecutionGraphViewResult graph, ExecutionEvent event) {
        executionRuntimeWriteService.appendEvent(new ExecutionEventAppendRequest(graph.storeId(), graph.sessionId(), event));
        broadcast(new ExecutionEventStreamItem(
                event.eventId(),
                event.executionId(),
                event.planId(),
                event.nodeId(),
                event.status(),
                event.occurredAt(),
                event.payload(),
                event.metadata()
        ));
    }

    private void broadcast(ExecutionEventStreamItem item) {
        CopyOnWriteArrayList<SseEmitter> targets = emitters.get(item.executionId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event()
                        .name("execution-event")
                        .data(item));
            } catch (IOException ex) {
                log.debug("execution stream emitter dropped: executionId={}, message={}", item.executionId(), ex.getMessage());
                removeEmitter(item.executionId(), emitter);
            }
        }
    }

    private void removeEmitter(String executionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> targets = emitters.get(executionId);
        if (targets == null) {
            return;
        }
        targets.remove(emitter);
        executionMetricsService.recordStreamClosed(executionId);
        if (targets.isEmpty()) {
            emitters.remove(executionId);
        }
    }

    private Map<String, Object> graphMetadata(RoutingDecision routing, String traceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (routing != null && routing.context() != null) {
            metadata.put("sessionId", routing.context().getSessionId());
            metadata.put("storeId", routing.context().getStoreId());
        }
        metadata.put("traceId", traceId);
        return Map.copyOf(metadata);
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
