package com.example.dish.control.execution.model;

import java.io.Serializable;
import java.util.Map;

public record ExecutionEdgeView(
        String edgeId,
        String fromNodeId,
        String toNodeId,
        String condition,
        Map<String, Object> metadata
) implements Serializable {
}
