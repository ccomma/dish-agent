package com.example.dish.control.policy.model;

import com.example.dish.common.runtime.ExecutionContext;
import com.example.dish.common.runtime.ExecutionNode;

import java.io.Serializable;

public record PolicyEvaluationRequest(
        ExecutionNode node,
        ExecutionContext context,
        String tenantId,
        String traceId
) implements Serializable {
}
