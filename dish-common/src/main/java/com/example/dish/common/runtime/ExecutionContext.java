package com.example.dish.common.runtime;

import com.example.dish.common.context.AgentContext;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 执行上下文。
 */
public record ExecutionContext(
        String executionId,
        String planId,
        AgentContext agentContext,
        Map<String, Object> variables,
        Map<String, Object> metadata
) implements Serializable {

    public ExecutionContext {
        variables = variables == null ? Collections.emptyMap() : Map.copyOf(variables);
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
