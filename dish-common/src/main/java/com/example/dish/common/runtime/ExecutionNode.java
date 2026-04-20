package com.example.dish.common.runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 执行图节点。
 */
public record ExecutionNode(
        String nodeId,
        ExecutionNodeType nodeType,
        String target,
        long timeoutMs,
        int maxRetries,
        boolean requiresApproval,
        Map<String, Object> payload,
        Map<String, Object> metadata
) implements Serializable {

    public ExecutionNode {
        payload = payload == null ? Collections.emptyMap() : Map.copyOf(payload);
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
