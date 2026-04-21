package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionLatestQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayResult;
import com.example.dish.control.execution.service.ExecutionRuntimeReadService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@DubboService(interfaceClass = ExecutionRuntimeReadService.class, timeout = 5000, retries = 0)
public class ExecutionRuntimeReadServiceImpl implements ExecutionRuntimeReadService {

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public ExecutionGraphViewResult latest(ExecutionLatestQueryRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("runtime.latest", "dish-memory");
        try (spanScope) {
            if (request == null || blank(request.tenantId()) || blank(request.sessionId())) {
                return null;
            }
            String executionId = ExecutionRuntimeWriteServiceImpl.latestExecutionId(memoryMode, redisTemplate, request.tenantId(), request.sessionId());
            if (blank(executionId)) {
                return null;
            }
            return graph(new ExecutionGraphQueryRequest(request.tenantId(), executionId, request.traceId()));
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public ExecutionGraphViewResult graph(ExecutionGraphQueryRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("runtime.graph", "dish-memory");
        try (spanScope) {
            if (request == null || blank(request.executionId())) {
                return null;
            }
            ExecutionRuntimeState state = ExecutionRuntimeWriteServiceImpl.loadState(memoryMode, redisTemplate, request.tenantId(), request.executionId());
            if (state == null || state.graph() == null) {
                return null;
            }
            ExecutionGraphViewResult graph = state.graph();
            Instant now = Instant.now();
            long durationMs = graph.finishedAt() != null
                    ? graph.durationMs()
                    : graph.startedAt() != null ? Math.max(0L, now.toEpochMilli() - graph.startedAt().toEpochMilli()) : 0L;
            ExecutionNodeStatus overall = graph.overallStatus() == ExecutionNodeStatus.PENDING && !state.events().isEmpty()
                    ? inferOverallFromReplay(state.graph())
                    : graph.overallStatus();

            return new ExecutionGraphViewResult(
                    graph.executionId(),
                    graph.planId(),
                    graph.sessionId(),
                    graph.storeId(),
                    graph.traceId(),
                    graph.intent(),
                    graph.executionMode(),
                    overall,
                    graph.startedAt(),
                    graph.finishedAt(),
                    durationMs,
                    graph.metadata(),
                    graph.nodes(),
                    graph.edges(),
                    state.events().size()
            );
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public ExecutionReplayResult replay(ExecutionReplayQueryRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("runtime.replay", "dish-memory");
        try (spanScope) {
            if (request == null || blank(request.executionId())) {
                return null;
            }
            ExecutionRuntimeState state = ExecutionRuntimeWriteServiceImpl.loadState(memoryMode, redisTemplate, request.tenantId(), request.executionId());
            if (state == null || state.graph() == null) {
                return null;
            }
            ExecutionGraphViewResult graph = graph(new ExecutionGraphQueryRequest(request.tenantId(), request.executionId(), request.traceId()));
            return new ExecutionReplayResult(
                    graph.executionId(),
                    graph.planId(),
                    graph.overallStatus(),
                    graph.startedAt(),
                    graph.finishedAt(),
                    graph.durationMs(),
                    state.events().size(),
                    state.events()
            );
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    private ExecutionNodeStatus inferOverallFromReplay(ExecutionGraphViewResult graph) {
        if (graph.nodes().stream().anyMatch(node -> node.status() == ExecutionNodeStatus.FAILED)) {
            return ExecutionNodeStatus.FAILED;
        }
        if (graph.nodes().stream().anyMatch(node -> node.status() == ExecutionNodeStatus.WAITING_APPROVAL)) {
            return ExecutionNodeStatus.WAITING_APPROVAL;
        }
        if (graph.nodes().stream().anyMatch(node -> node.status() == ExecutionNodeStatus.RUNNING || node.status() == ExecutionNodeStatus.RETRYING)) {
            return ExecutionNodeStatus.RUNNING;
        }
        if (graph.nodes().stream().allMatch(node -> node.status() == ExecutionNodeStatus.SUCCEEDED)) {
            return ExecutionNodeStatus.SUCCEEDED;
        }
        if (graph.nodes().stream().anyMatch(node -> node.status() == ExecutionNodeStatus.CANCELLED)) {
            return ExecutionNodeStatus.CANCELLED;
        }
        return graph.overallStatus();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
