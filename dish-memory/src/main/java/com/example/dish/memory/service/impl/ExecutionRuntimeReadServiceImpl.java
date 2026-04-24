package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionLatestQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayResult;
import com.example.dish.control.execution.service.ExecutionRuntimeReadService;
import com.example.dish.memory.model.ExecutionRuntimeState;
import com.example.dish.memory.runtime.ExecutionRuntimeGraphProjector;
import com.example.dish.memory.storage.ExecutionRuntimeStorage;
import com.example.dish.common.util.TimeSupport;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * execution runtime 读服务门面。
 *
 * <p>该类只负责对外暴露 latest / graph / replay 三个查询入口，
 * 具体的 graph 状态推导委托给 runtime 投影层处理。</p>
 */
@Service
@DubboService(interfaceClass = ExecutionRuntimeReadService.class, timeout = 5000, retries = 0)
public class ExecutionRuntimeReadServiceImpl implements ExecutionRuntimeReadService {

    @Autowired
    private ExecutionRuntimeStorage executionRuntimeStorage;

    @Autowired
    private ExecutionRuntimeGraphProjector executionRuntimeGraphProjector;

    @Override
    @DubboProviderSpan("runtime.latest")
    public ExecutionGraphViewResult latest(ExecutionLatestQueryRequest request) {
        // 1. 校验租户和会话，避免无范围查询。
        if (request == null || StringUtils.isBlank(request.tenantId()) || StringUtils.isBlank(request.sessionId())) {
            return null;
        }

        // 2. 读取 session 最新 executionId，找不到时返回空。
        String executionId = executionRuntimeStorage.latestExecutionId(request.tenantId(), request.sessionId());
        if (StringUtils.isBlank(executionId)) {
            return null;
        }

        // 3. 复用 graph 查询逻辑返回最新 execution 视图。
        return graph(new ExecutionGraphQueryRequest(request.tenantId(), executionId, request.traceId()));
    }

    @Override
    @DubboProviderSpan("runtime.graph")
    public ExecutionGraphViewResult graph(ExecutionGraphQueryRequest request) {
        // 1. 校验 executionId 并读取运行态快照。
        if (request == null || StringUtils.isBlank(request.executionId())) {
            return null;
        }
        ExecutionRuntimeState state = executionRuntimeStorage.loadState(request.executionId());
        if (state == null || state.graph() == null) {
            return null;
        }

        // 2. 根据当前时间和回放事件推导展示用状态与耗时。
        ExecutionGraphViewResult graph = state.graph();
        Instant now = Instant.now();
        long currentDurationMs = graph.finishedAt() != null
                ? graph.durationMs()
                : TimeSupport.durationMs(graph.startedAt(), now);
        ExecutionNodeStatus overall = graph.overallStatus() == ExecutionNodeStatus.PENDING && !state.events().isEmpty()
                ? executionRuntimeGraphProjector.inferOverallFromReplay(state.graph())
                : graph.overallStatus();

        // 3. 返回包含事件总数的 graph 视图。
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
                currentDurationMs,
                graph.metadata(),
                graph.nodes(),
                graph.edges(),
                state.events().size()
        );
    }

    @Override
    @DubboProviderSpan("runtime.replay")
    public ExecutionReplayResult replay(ExecutionReplayQueryRequest request) {
        // 1. 校验 executionId 并读取完整事件流。
        if (request == null || StringUtils.isBlank(request.executionId())) {
            return null;
        }
        ExecutionRuntimeState state = executionRuntimeStorage.loadState(request.executionId());
        if (state == null || state.graph() == null) {
            return null;
        }

        // 2. 复用 graph 查询结果，保证回放状态和 graph 状态一致。
        ExecutionGraphViewResult graph = graph(new ExecutionGraphQueryRequest(request.tenantId(), request.executionId(), request.traceId()));

        // 3. 返回按写入顺序保存的 replay 事件列表。
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
    }

}
