package com.example.dish.control.planner.model;

import com.example.dish.common.context.AgentContext;

import java.io.Serializable;

public record PlanningRequest(
        String userInput,
        AgentContext context,
        String tenantId,
        String traceId
) implements Serializable {
}
