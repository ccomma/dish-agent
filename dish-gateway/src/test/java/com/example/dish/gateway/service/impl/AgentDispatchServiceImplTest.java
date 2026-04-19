package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.rpc.ChatAgentService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

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

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
