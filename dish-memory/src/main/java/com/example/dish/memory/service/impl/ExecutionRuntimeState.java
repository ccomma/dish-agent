package com.example.dish.memory.service.impl;

import com.example.dish.control.execution.model.ExecutionEventStreamItem;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;

import java.io.Serializable;
import java.util.List;

record ExecutionRuntimeState(
        ExecutionGraphViewResult graph,
        List<ExecutionEventStreamItem> events
) implements Serializable {
}
