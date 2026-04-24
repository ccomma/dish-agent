package com.example.dish.control.planner.service;

import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;

/**
 * 执行规划服务契约。
 */
public interface ExecutionPlannerService {

    PlanningResult plan(PlanningRequest request);
}
