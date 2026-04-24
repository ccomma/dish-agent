package com.example.dish.planner.service.impl;

import com.example.dish.common.runtime.ExecutionPlan;
import com.example.dish.common.telemetry.DubboProviderSpan;
import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;
import com.example.dish.control.planner.service.ExecutionPlannerService;
import com.example.dish.planner.support.ExecutionPlanFactory;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 执行规划服务门面。
 * 负责接收规划请求并委托计划工厂生成统一 ExecutionPlan。
 */
@Service
@DubboService(interfaceClass = ExecutionPlannerService.class, timeout = 15000, retries = 0)
public class ExecutionPlannerServiceImpl implements ExecutionPlannerService {

    private final ExecutionPlanFactory executionPlanFactory;

    public ExecutionPlannerServiceImpl() {
        this(new ExecutionPlanFactory());
    }

    public ExecutionPlannerServiceImpl(ExecutionPlanFactory executionPlanFactory) {
        this.executionPlanFactory = executionPlanFactory;
    }

    @Override
    @DubboProviderSpan("planner.plan")
    public PlanningResult plan(PlanningRequest request) {
        // 1. 把请求交给计划工厂，统一完成意图归一化、模板选择和 ExecutionPlan 组装。
        ExecutionPlan plan = executionPlanFactory.create(request);

        // 2. 返回规划结果，供 gateway 预览或真实执行链路复用。
        return new PlanningResult(
                true,
                "planner-v1-rule-graph",
                "intent-based execution graph generated",
                plan
        );
    }
}
