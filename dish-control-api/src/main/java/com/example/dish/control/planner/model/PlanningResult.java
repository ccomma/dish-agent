package com.example.dish.control.planner.model;

import com.example.dish.common.runtime.ExecutionPlan;

import java.io.Serializable;

public record PlanningResult(
        boolean success,
        String plannerVersion,
        String reason,
        ExecutionPlan plan
) implements Serializable {
}
