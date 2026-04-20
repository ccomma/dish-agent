package com.example.dish.common.runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 统一工具调用请求。
 */
public record ToolCallRequest(
        String requestId,
        String toolName,
        String tenantId,
        String executionId,
        String nodeId,
        Map<String, Object> arguments,
        Map<String, Object> metadata
) implements Serializable {

    public ToolCallRequest {
        arguments = arguments == null ? Collections.emptyMap() : Map.copyOf(arguments);
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
