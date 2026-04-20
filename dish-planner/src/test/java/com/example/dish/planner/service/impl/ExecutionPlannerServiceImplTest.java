package com.example.dish.planner.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.control.planner.model.PlanningRequest;
import com.example.dish.control.planner.model.PlanningResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ExecutionPlannerServiceImplTest {

    @Test
    void shouldBuildSingleStepPlanForChatIntent() {
        ExecutionPlannerServiceImpl planner = new ExecutionPlannerServiceImpl();
        PlanningRequest request = new PlanningRequest(
                "你好",
                AgentContext.builder().sessionId("S-1").intent(IntentType.GENERAL_CHAT).build(),
                "store-1",
                "trace-1"
        );

        PlanningResult result = planner.plan(request);

        Assertions.assertTrue(result.success());
        Assertions.assertEquals("plan-S-1", result.plan().planId());
        Assertions.assertEquals("single", result.plan().metadata().get("executionMode"));
        Assertions.assertEquals(1, result.plan().nodes().size());
        Assertions.assertTrue(result.plan().edges().isEmpty());

        ExecutionNode node = result.plan().nodes().get(0);
        Assertions.assertEquals(ExecutionNodeType.AGENT_CALL, node.nodeType());
        Assertions.assertEquals("chat", node.target());
    }

    @Test
    void shouldBuildTwoStepPlanForDishIntent() {
        ExecutionPlannerServiceImpl planner = new ExecutionPlannerServiceImpl();
        PlanningRequest request = new PlanningRequest(
                "宫保鸡丁做法",
                AgentContext.builder().sessionId("S-2").intent(IntentType.DISH_QUESTION).build(),
                "store-2",
                "trace-2"
        );

        PlanningResult result = planner.plan(request);

        Assertions.assertTrue(result.success());
        Assertions.assertEquals("serial", result.plan().metadata().get("executionMode"));
        Assertions.assertEquals(2, result.plan().nodes().size());
        Assertions.assertEquals(1, result.plan().edges().size());

        List<String> targets = result.plan().nodes().stream().map(ExecutionNode::target).toList();
        Assertions.assertEquals(List.of("dish-knowledge", "chat"), targets);
        Assertions.assertEquals("n-dish-1", result.plan().edges().get(0).fromNodeId());
        Assertions.assertEquals("n-chat-2", result.plan().edges().get(0).toNodeId());
    }

    @Test
    void shouldBuildTwoStepPlanForWorkOrderIntent() {
        ExecutionPlannerServiceImpl planner = new ExecutionPlannerServiceImpl();
        PlanningRequest request = new PlanningRequest(
                "帮我查订单",
                AgentContext.builder().sessionId("S-3").intent(IntentType.QUERY_ORDER).build(),
                "store-3",
                "trace-3"
        );

        PlanningResult result = planner.plan(request);

        Assertions.assertTrue(result.success());
        List<String> targets = result.plan().nodes().stream().map(ExecutionNode::target).toList();
        Assertions.assertEquals(List.of("work-order", "chat"), targets);
        Assertions.assertEquals(1, result.plan().edges().size());
    }
}
