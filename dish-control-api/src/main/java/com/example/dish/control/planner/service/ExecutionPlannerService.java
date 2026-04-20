package com.example.dish.control.planner.service;

import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;

public interface ExecutionPlannerService {

    PlanningResult plan(PlanningRequest request);
}
