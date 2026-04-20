package com.example.dish.common.runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 统一工具调用结果。
 */
public record ToolCallResult(
        String requestId,
        String toolName,
        boolean success,
        String errorCode,
        String errorMessage,
        boolean retryable,
        Map<String, Object> payload,
        Map<String, Object> metadata
) implements Serializable {

    public ToolCallResult {
        payload = payload == null ? Collections.emptyMap() : Map.copyOf(payload);
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }

    public static ToolCallResult success(String requestId, String toolName, Map<String, Object> payload) {
        return new ToolCallResult(requestId, toolName, true, null, null, false, payload, Collections.emptyMap());
    }

    public static ToolCallResult failure(String requestId, String toolName, String errorCode, String errorMessage, boolean retryable) {
        return new ToolCallResult(requestId, toolName, false, errorCode, errorMessage, retryable, Collections.emptyMap(), Collections.emptyMap());
    }
}
