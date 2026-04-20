package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.rpc.ChatAgentService;
import com.example.dish.common.rpc.DishAgentService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

class AgentDispatchServiceImplTest {

    @Test
    void shouldReturnDegradedResponseWhenChatAgentFails() throws Exception {
        AgentDispatchServiceImpl service = new AgentDispatchServiceImpl();
        inject(service, "chatAgentService", (ChatAgentService) (userInput, sessionId) -> {
            throw new RuntimeException("chat down");
        });

        AgentContext context = AgentContext.builder()
                .sessionId("S-1001")
                .userInput("你好")
                .intent(IntentType.GENERAL_CHAT)
                .build();
        RoutingDecision routing = new RoutingDecision(IntentType.GENERAL_CHAT, RoutingDecision.TARGET_CHAT, "test", context);

        AgentResponse response = service.dispatch(routing);

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("Gateway", response.getAgentName());
        Assertions.assertEquals("聊天服务暂时不可用，请稍后重试", response.getContent());
        Assertions.assertFalse(response.getFollowUpHints().isEmpty());
    }

    @Test
    void shouldDispatchAllStepsInSerialOrder() throws Exception {
        AgentDispatchServiceImpl service = new AgentDispatchServiceImpl();
        inject(service, "chatAgentService", (ChatAgentService) (userInput, sessionId) ->
                AgentResponse.success("chat-ok", "chat-agent", AgentContext.builder().sessionId(sessionId).build()));
        inject(service, "dishAgentService", new DishAgentService() {
            @Override
            public AgentResponse answer(String userInput, AgentContext context) {
                return AgentResponse.success("dish-ok", "dish-agent", context);
            }

            @Override
            public AgentResponse answerWithReflection(String userInput, AgentContext context) {
                return AgentResponse.success("dish-ok", "dish-agent", context);
            }
        });

        AgentContext context = AgentContext.builder()
                .sessionId("S-2002")
                .userInput("先闲聊再问菜")
                .intent(IntentType.DISH_QUESTION)
                .build();

        List<AgentExecutionStep> steps = List.of(
                AgentExecutionStep.builder()
                        .stepId("s1")
                        .targetAgent(RoutingDecision.TARGET_CHAT)
                        .nodeType("AGENT_CALL")
                        .metadata(Map.of())
                        .build(),
                AgentExecutionStep.builder()
                        .stepId("s2")
                        .targetAgent(RoutingDecision.TARGET_DISH_KNOWLEDGE)
                        .nodeType("AGENT_CALL")
                        .dependsOn(List.of("s1"))
                        .metadata(Map.of())
                        .build()
        );

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.DISH_QUESTION)
                .targetAgent(RoutingDecision.TARGET_CHAT)
                .reason("test")
                .context(context)
                .planId("plan-S-2002")
                .executionMode("serial")
                .executionSteps(steps)
                .build();

        List<AgentResponse> responses = service.dispatchAll(routing, steps);

        Assertions.assertEquals(2, responses.size());
        Assertions.assertEquals("chat-ok", responses.get(0).getContent());
        Assertions.assertEquals("dish-ok", responses.get(1).getContent());
    }

    @Test
    void shouldFallbackToSingleDispatchWhenStepListEmpty() throws Exception {
        AgentDispatchServiceImpl service = new AgentDispatchServiceImpl();
        inject(service, "chatAgentService", (ChatAgentService) (userInput, sessionId) ->
                AgentResponse.success("single-chat", "chat-agent", AgentContext.builder().sessionId(sessionId).build()));

        AgentContext context = AgentContext.builder()
                .sessionId("S-3003")
                .userInput("你好")
                .intent(IntentType.GENERAL_CHAT)
                .build();
        RoutingDecision routing = new RoutingDecision(IntentType.GENERAL_CHAT, RoutingDecision.TARGET_CHAT, "test", context);

        List<AgentResponse> responses = service.dispatchAll(routing, List.of());

        Assertions.assertEquals(1, responses.size());
        Assertions.assertEquals("single-chat", responses.get(0).getContent());
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
