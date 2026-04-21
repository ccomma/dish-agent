package com.example.dish.control.execution.model;

import com.example.dish.common.runtime.ExecutionEvent;

import java.io.Serializable;

public record ExecutionEventAppendRequest(
        String tenantId,
        String sessionId,
        ExecutionEvent event
) implements Serializable {
}
