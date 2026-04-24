package com.example.dish.memory.runtime;

import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.common.util.MetadataSupport;
import com.example.dish.common.util.TimeSupport;
import com.example.dish.common.util.ValueSupport;
import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionNodeView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * execution runtime 视图投影器。
 *
 * <p>该类负责根据 event stream 中的一条事件，把已有 graph 快照投影成新的展示视图，
 * 包括节点状态、整体状态、结束时间和耗时等运行态字段。</p>
 */
@Component
public class ExecutionRuntimeGraphProjector {

    public ExecutionGraphViewResult apply(ExecutionGraphViewResult graph, ExecutionEventStreamItem item) {
        // 1. 逐个节点检查是否命中当前事件的 nodeId，命中时投影出新的节点视图。
        List<ExecutionNodeView> nodes = graph.nodes().stream()
                .map(node -> Objects.equals(node.nodeId(), item.nodeId()) ? updateNode(node, item) : node)
                .toList();

        // 2. 基于最新节点列表重新推导整体状态、开始时间、结束时间和耗时。
        ExecutionNodeStatus overallStatus = overallStatus(nodes);
        Instant startedAt = graph.startedAt() != null ? graph.startedAt() : item.occurredAt();
        Instant finishedAt = isTerminal(overallStatus) ? item.occurredAt() : null;
        long durationMs = TimeSupport.durationMs(startedAt, finishedAt != null ? finishedAt : item.occurredAt());

        // 3. 返回新的 graph 视图，同时把总事件数加一。
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

    public ExecutionNodeStatus inferOverallFromReplay(ExecutionGraphViewResult graph) {
        // 回放场景只依赖当前节点视图，不重复读取 event stream。
        return overallStatus(graph.nodes(), graph.overallStatus());
    }

    private ExecutionNodeView updateNode(ExecutionNodeView node, ExecutionEventStreamItem item) {
        // 1. 统一从事件 payload / metadata 提取节点展示字段。
        Map<String, Object> payload = item.payload();
        Map<String, Object> metadata = item.metadata();
        long latencyMs = ValueSupport.asLong(metadata.get("latencyMs"), node.latencyMs());
        String riskLevel = firstNotBlank(ValueSupport.asString(metadata.get("riskLevel")), node.riskLevel());
        String statusReason = firstNotBlank(
                ValueSupport.asString(metadata.get("statusReason")),
                ValueSupport.asString(payload.get("responseSummary")),
                node.statusReason()
        );
        String approvalId = firstNotBlank(ValueSupport.asString(metadata.get("approvalId")), node.approvalId());

        // 2. 生成新的节点视图，并保留原节点元数据与事件元数据的合并结果。
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
                MetadataSupport.merge(node.metadata(), metadata)
        );
    }

    private ExecutionNodeStatus overallStatus(List<ExecutionNodeView> nodes) {
        return overallStatus(nodes, ExecutionNodeStatus.PENDING);
    }

    private ExecutionNodeStatus overallStatus(List<ExecutionNodeView> nodes, ExecutionNodeStatus fallback) {
        // 整体状态按失败、等待审批、运行中、全部成功、已取消的优先级顺序推导。
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
        if (nodes.stream().anyMatch(node -> node.status() == ExecutionNodeStatus.CANCELLED)) {
            return ExecutionNodeStatus.CANCELLED;
        }
        return fallback;
    }

    private boolean isTerminal(ExecutionNodeStatus status) {
        return status == ExecutionNodeStatus.SUCCEEDED
                || status == ExecutionNodeStatus.FAILED
                || status == ExecutionNodeStatus.CANCELLED;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
