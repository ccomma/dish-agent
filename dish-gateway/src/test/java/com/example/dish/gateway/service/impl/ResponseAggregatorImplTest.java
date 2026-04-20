package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.GatewayResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ResponseAggregatorImplTest {

    @Test
    void shouldMergeMultipleAgentResponses() {
        ResponseAggregatorImpl aggregator = new ResponseAggregatorImpl();

        AgentContext context = AgentContext.builder()
                .sessionId("S-4004")
                .intent(IntentType.DISH_QUESTION)
                .build();

        RoutingDecision routing = new RoutingDecision(
                IntentType.DISH_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "test",
                context,
                "plan-S-4004",
                List.of(),
                "serial",
                0.93,
                Map.of()
        );
        context.getMetadata().put("traceId", "trace-4004");
        context.getMetadata().put("memoryHit", true);
        context.getMetadata().put("memorySnippets", List.of("历史上问过宫保鸡丁"));

        AgentResponse primary = AgentResponse.builder()
                .success(true)
                .content("主回答")
                .agentName("dish-agent")
                .context(context)
                .followUpHints(List.of("hint-1"))
                .build();

        AgentResponse support = AgentResponse.builder()
                .success(true)
                .content("补充回答")
                .agentName("workorder-agent")
                .context(context)
                .followUpHints(List.of("hint-2"))
                .build();

        GatewayResponse response = aggregator.aggregate(List.of(primary, support), routing);

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertTrue(response.getContent().contains("主回答"));
        Assertions.assertTrue(response.getContent().contains("补充信息"));
        Assertions.assertTrue(response.getContent().contains("补充回答"));
        Assertions.assertEquals("S-4004", response.getSessionId());
        Assertions.assertEquals(2, response.getFollowUpHints().size());
        Assertions.assertEquals("trace-4004", response.getTraceId());
        Assertions.assertEquals("plan-S-4004", response.getPlanId());
        Assertions.assertEquals("serial", response.getExecutionMode());
        Assertions.assertTrue(response.isMemoryHit());
        Assertions.assertEquals(2, response.getExecutedStepCount());
    }

    @Test
    void shouldReturnFailureWhenNoResponses() {
        ResponseAggregatorImpl aggregator = new ResponseAggregatorImpl();

        GatewayResponse response = aggregator.aggregate(List.of(), null);

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("编排结果为空", response.getContent());
        Assertions.assertEquals("Gateway-Orchestrator", response.getAgentName());
    }
}
