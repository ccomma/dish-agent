package com.example.dish.memory.model;

import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;

import java.io.Serializable;
import java.util.List;

/**
 * execution runtime 在存储层落盘时使用的完整快照，
 * 包含最新 graph 视图和按时间顺序追加的事件流。
 */
public record ExecutionRuntimeState(
        ExecutionGraphViewResult graph,
        List<ExecutionEventStreamItem> events
) implements Serializable {
}
