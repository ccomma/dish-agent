package com.example.dish.common.contract;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.react.ReActEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AgentResponseTest {

    @Test
    void shouldBuildResponseFromReactResultWithFollowUpHints() {
        AgentContext context = AgentContext.forSession("SESSION_001");
        ReActEngine.ReActResult result = new ReActEngine.ReActResult(
                "处理完成",
                new ReActState("SESSION_001", "用户问题"),
                true
        );

        AgentResponse response = AgentResponse.fromReActResult(
                result,
                "DishAgent",
                context,
                List.of("还要继续吗？")
        );

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals("处理完成", response.getContent());
        Assertions.assertEquals("DishAgent", response.getAgentName());
        Assertions.assertSame(context, response.getContext());
        Assertions.assertEquals(List.of("还要继续吗？"), response.getFollowUpHints());
    }
}
