package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
import com.example.dish.control.execution.model.ExecutionEventAppendRequest;
import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphSnapshotWriteRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionNodeView;
import com.example.dish.control.execution.service.ExecutionRuntimeWriteService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@DubboService(interfaceClass = ExecutionRuntimeWriteService.class, timeout = 5000, retries = 0)
public class ExecutionRuntimeWriteServiceImpl implements ExecutionRuntimeWriteService {

    private static final Map<String, ExecutionRuntimeState> STATES = new ConcurrentHashMap<>();
    private static final Map<String, String> SESSION_LATEST = new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArrayList<String>> PLAN_EXECUTIONS = new ConcurrentHashMap<>();

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean initialize(ExecutionGraphSnapshotWriteRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("runtime.initialize", "dish-memory");
        try (spanScope) {
            if (request == null || request.graph() == null || blank(request.graph().executionId())) {
                return false;
            }
            ExecutionGraphViewResult graph = request.graph();
            ExecutionRuntimeState state = new ExecutionRuntimeState(graph, List.of());
            saveState(state);
            if (!blank(request.sessionId())) {
                saveLatestExecution(request.tenantId(), request.sessionId(), graph.executionId());
            }
            if (!blank(graph.planId())) {
                savePlanExecution(request.tenantId(), graph.planId(), graph.executionId());
            }
            return true;
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public boolean appendEvent(ExecutionEventAppendRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("runtime.append_event", "dish-memory");
        try (spanScope) {
            if (request == null || request.event() == null || blank(request.event().executionId())) {
                return false;
            }

            ExecutionRuntimeState current = loadState(request.tenantId(), request.event().executionId());
            if (current == null || current.graph() == null) {
                return false;
            }

            ExecutionEventStreamItem item = new ExecutionEventStreamItem(
                    request.event().eventId(),
                    request.event().executionId(),
                    request.event().planId(),
                    request.event().nodeId(),
                    request.event().status(),
                    request.event().occurredAt(),
                    request.event().payload(),
                    request.event().metadata()
            );

            List<ExecutionEventStreamItem> events = new ArrayList<>(current.events());
            events.add(item);
            ExecutionGraphViewResult updatedGraph = apply(current.graph(), item);
            saveState(new ExecutionRuntimeState(updatedGraph, List.copyOf(events)));

            String sessionId = asString(item.metadata().get("sessionId"));
            if (!blank(sessionId)) {
                saveLatestExecution(request.tenantId(), sessionId, item.executionId());
            }
            return true;
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    static void clearForTest() {
        STATES.clear();
        SESSION_LATEST.clear();
        PLAN_EXECUTIONS.clear();
    }

    private ExecutionGraphViewResult apply(ExecutionGraphViewResult graph, ExecutionEventStreamItem item) {
        List<ExecutionNodeView> nodes = graph.nodes().stream()
                .map(node -> Objects.equals(node.nodeId(), item.nodeId()) ? updateNode(node, item) : node)
                .toList();

        ExecutionNodeStatus overallStatus = overallStatus(nodes);
        Instant startedAt = graph.startedAt() != null ? graph.startedAt() : item.occurredAt();
        Instant finishedAt = isTerminal(overallStatus) ? item.occurredAt() : null;
        long durationMs = durationMs(startedAt, finishedAt != null ? finishedAt : item.occurredAt());

        return new ExecutionGraphViewResult(
                graph.executionId(),
                graph.planId(),
                graph.sessionId(),
                graph.storeId(),
                graph.traceId(),
                graph.intent(),
                graph.executionMode(),
                overallStatus,
                startedAt,
                finishedAt,
                durationMs,
                graph.metadata(),
                nodes,
                graph.edges(),
                graph.totalEvents() + 1
        );
    }

    private ExecutionNodeView updateNode(ExecutionNodeView node, ExecutionEventStreamItem item) {
        Map<String, Object> payload = item.payload();
        Map<String, Object> metadata = item.metadata();
        long latencyMs = asLong(metadata.get("latencyMs"), node.latencyMs());
        String riskLevel = asString(metadata.get("riskLevel"));
        if (blank(riskLevel)) {
            riskLevel = node.riskLevel();
        }
        String statusReason = asString(metadata.get("statusReason"));
        if (blank(statusReason)) {
            statusReason = asString(payload.get("responseSummary"));
        }
        if (blank(statusReason)) {
            statusReason = node.statusReason();
        }
        String approvalId = asString(metadata.get("approvalId"));
        if (blank(approvalId)) {
            approvalId = node.approvalId();
        }

        return new ExecutionNodeView(
                node.nodeId(),
                node.targetAgent(),
                node.nodeType(),
                item.status(),
                node.requiresApproval(),
                riskLevel,
                latencyMs,
                statusReason,
                approvalId,
                item.occurredAt(),
                mergeMetadata(node.metadata(), metadata)
        );
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> left, Map<String, Object> right) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return Map.copyOf(merged);
    }

    private ExecutionNodeStatus overallStatus(List<ExecutionNodeView> nodes) {
        if (nodes.stream().anyMatch(node -> node.status() == ExecutionNodeStatus.FAILED)) {
            return ExecutionNodeStatus.FAILED;
        }
        if (nodes.stream().anyMatch(node -> node.status() == ExecutionNodeStatus.WAITING_APPROVAL)) {
            return ExecutionNodeStatus.WAITING_APPROVAL;
        }
        if (nodes.stream().anyMatch(node -> node.status() == ExecutionNodeStatus.RUNNING || node.status() == ExecutionNodeStatus.RETRYING)) {
            return ExecutionNodeStatus.RUNNING;
        }
        if (nodes.stream().allMatch(node -> node.status() == ExecutionNodeStatus.SUCCEEDED)) {
            return ExecutionNodeStatus.SUCCEEDED;
        }
        if (nodes.stream().allMatch(node -> node.status() == ExecutionNodeStatus.CANCELLED)
                || nodes.stream().anyMatch(node -> node.status() == ExecutionNodeStatus.CANCELLED)) {
            return ExecutionNodeStatus.CANCELLED;
        }
        return ExecutionNodeStatus.PENDING;
    }

    private boolean isTerminal(ExecutionNodeStatus status) {
        return status == ExecutionNodeStatus.SUCCEEDED
                || status == ExecutionNodeStatus.FAILED
                || status == ExecutionNodeStatus.CANCELLED;
    }

    private long durationMs(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            return 0L;
        }
        return Math.max(0L, endedAt.toEpochMilli() - startedAt.toEpochMilli());
    }

    private void saveState(ExecutionRuntimeState state) {
        STATES.put(state.graph().executionId(), state);
        if (useRedis()) {
            redisTemplate.opsForValue().set(redisStateKey(state.graph().executionId()), ExecutionRuntimeStorageCodec.encode(state));
        }
    }

    static ExecutionRuntimeState loadState(String memoryMode, StringRedisTemplate redisTemplate, String tenantId, String executionId) {
        if ("redis".equalsIgnoreCase(memoryMode) && redisTemplate != null) {
            return ExecutionRuntimeStorageCodec.decode(redisTemplate.opsForValue().get(redisStateKey(executionId)), ExecutionRuntimeState.class);
        }
        return STATES.get(executionId);
    }

    private ExecutionRuntimeState loadState(String tenantId, String executionId) {
        return loadState(memoryMode, redisTemplate, tenantId, executionId);
    }

    static String latestExecutionId(String memoryMode, StringRedisTemplate redisTemplate, String tenantId, String sessionId) {
        if ("redis".equalsIgnoreCase(memoryMode) && redisTemplate != null) {
            return redisTemplate.opsForValue().get(redisLatestSessionKey(tenantId, sessionId));
        }
        return SESSION_LATEST.get(sessionKey(tenantId, sessionId));
    }

    private void saveLatestExecution(String tenantId, String sessionId, String executionId) {
        SESSION_LATEST.put(sessionKey(tenantId, sessionId), executionId);
        if (useRedis()) {
            redisTemplate.opsForValue().set(redisLatestSessionKey(tenantId, sessionId), executionId);
        }
    }

    private void savePlanExecution(String tenantId, String planId, String executionId) {
        PLAN_EXECUTIONS.computeIfAbsent(planKey(tenantId, planId), ignored -> new CopyOnWriteArrayList<>()).add(executionId);
        if (useRedis()) {
            redisTemplate.opsForList().leftPush(redisPlanExecutionsKey(tenantId, planId), executionId);
        }
    }

    private boolean useRedis() {
        return "redis".equalsIgnoreCase(memoryMode) && redisTemplate != null;
    }

    private static String redisStateKey(String executionId) {
        return "dish:runtime:execution:" + executionId + ":state";
    }

    private static String redisLatestSessionKey(String tenantId, String sessionId) {
        return "dish:runtime:" + tenantId + ":session:" + sessionId + ":latest";
    }

    private static String redisPlanExecutionsKey(String tenantId, String planId) {
        return "dish:runtime:" + tenantId + ":plan:" + planId + ":executions";
    }

    private static String sessionKey(String tenantId, String sessionId) {
        return tenantId + "::" + sessionId;
    }

    private static String planKey(String tenantId, String planId) {
        return tenantId + "::" + planId;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
