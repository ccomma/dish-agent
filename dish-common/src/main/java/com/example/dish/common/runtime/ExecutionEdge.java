package com.example.dish.common.runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 执行图有向边。
 */
public record ExecutionEdge(
        String edgeId,
        String fromNodeId,
        String toNodeId,
        ExecutionEdgeCondition condition,
        Map<String, Object> metadata
) implements Serializable {

    public ExecutionEdge {
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
