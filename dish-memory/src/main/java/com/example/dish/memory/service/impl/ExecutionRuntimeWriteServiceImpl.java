package com.example.dish.memory.service.impl;

import com.example.dish.control.execution.model.ExecutionEventAppendRequest;
import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphSnapshotWriteRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.service.ExecutionRuntimeWriteService;
import com.example.dish.memory.model.ExecutionRuntimeState;
import com.example.dish.memory.runtime.ExecutionRuntimeGraphProjector;
import com.example.dish.memory.storage.ExecutionRuntimeStorage;
import com.example.dish.common.util.ValueSupport;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * execution runtime 写服务门面。
 *
 * <p>该类负责接收 graph 初始化和 event 追加请求，
 * 然后把状态写入存储层，并委托 runtime 投影器刷新最新 graph 视图。</p>
 */
@Service
@DubboService(interfaceClass = ExecutionRuntimeWriteService.class, timeout = 5000, retries = 0)
public class ExecutionRuntimeWriteServiceImpl implements ExecutionRuntimeWriteService {

    @Autowired
    private ExecutionRuntimeStorage executionRuntimeStorage;

    @Autowired
    private ExecutionRuntimeGraphProjector executionRuntimeGraphProjector;

    @Override
    @DubboProviderSpan("runtime.initialize")
    public boolean initialize(ExecutionGraphSnapshotWriteRequest request) {
        // 1. 校验 execution graph 快照，缺少 executionId 时拒绝初始化。
        if (request == null || request.graph() == null || StringUtils.isBlank(request.graph().executionId())) {
            return false;
        }

        // 2. 保存初始 graph 和空事件列表。
        ExecutionGraphViewResult graph = request.graph();
        ExecutionRuntimeState state = new ExecutionRuntimeState(graph, List.of());
        executionRuntimeStorage.saveState(state);

        // 3. 维护 session 最新 execution 索引，方便控制台快速定位。
        if (StringUtils.isNotBlank(request.sessionId())) {
            executionRuntimeStorage.saveLatestExecution(request.tenantId(), request.sessionId(), graph.executionId());
        }

        // 4. 维护 plan 到 execution 的索引，为后续聚合查询预留。
        if (StringUtils.isNotBlank(graph.planId())) {
            executionRuntimeStorage.savePlanExecution(request.tenantId(), graph.planId(), graph.executionId());
        }
        return true;
    }

    @Override
    @DubboProviderSpan("runtime.append_event")
    public boolean appendEvent(ExecutionEventAppendRequest request) {
        // 1. 校验事件请求，缺少 executionId 时不写入。
        if (request == null || request.event() == null || StringUtils.isBlank(request.event().executionId())) {
            return false;
        }

        // 2. 读取现有运行态，只有已初始化的 execution 才允许追加事件。
        ExecutionRuntimeState current = executionRuntimeStorage.loadState(request.event().executionId());
        if (current == null || current.graph() == null) {
            return false;
        }

        // 3. 将内部事件转换为稳定的 SSE/回放 DTO。
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

        // 4. 追加事件并基于事件更新 graph 节点状态。
        List<ExecutionEventStreamItem> events = new ArrayList<>(current.events());
        events.add(item);
        ExecutionGraphViewResult updatedGraph = executionRuntimeGraphProjector.apply(current.graph(), item);
        executionRuntimeStorage.saveState(new ExecutionRuntimeState(updatedGraph, List.copyOf(events)));

        // 5. 若事件元数据带 sessionId，同步刷新 session 最新 execution 索引。
        String sessionId = ValueSupport.asString(item.metadata().get("sessionId"));
        if (StringUtils.isNotBlank(sessionId)) {
            executionRuntimeStorage.saveLatestExecution(request.tenantId(), sessionId, item.executionId());
        }
        return true;
    }

    static void clearForTest() {
        ExecutionRuntimeStorage.clearForTest();
    }
}
