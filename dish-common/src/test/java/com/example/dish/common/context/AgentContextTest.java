package com.example.dish.common.context;

import com.example.dish.common.classifier.IntentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class AgentContextTest {

    @Test
    void shouldBuildSessionOnlyContext() {
        AgentContext context = AgentContext.forSession("SESSION_CHAT");

        Assertions.assertEquals("SESSION_CHAT", context.getSessionId());
        Assertions.assertTrue(context.getMetadata().isEmpty());
    }

    @Test
    void shouldBuildRequestContextWithKnownFields() {
        AgentContext context = AgentContext.fromRequest(
                "SESSION_WORK",
                IntentType.CREATE_REFUND,
                "STORE_001",
                "ORDER_001",
                "宫保鸡丁",
                "菜品温度不理想",
                "申请退款"
        );

        Assertions.assertEquals("SESSION_WORK", context.getSessionId());
        Assertions.assertEquals(IntentType.CREATE_REFUND, context.getIntent());
        Assertions.assertEquals("STORE_001", context.getStoreId());
        Assertions.assertEquals("ORDER_001", context.getOrderId());
        Assertions.assertEquals("宫保鸡丁", context.getDishName());
        Assertions.assertEquals("菜品温度不理想", context.getRefundReason());
        Assertions.assertEquals("申请退款", context.getUserInput());
    }

    @Test
    void shouldCopyContextWithAdditionalMetadataWithoutMutatingOriginal() {
        AgentContext original = AgentContext.builder()
                .sessionId("SESSION_DISH")
                .intent(IntentType.DISH_QUESTION)
                .metadata(Map.of("source", "gateway"))
                .build();

        AgentContext copied = original.withMetadataValue("enableReflection", true);

        Assertions.assertFalse(original.getMetadata().containsKey("enableReflection"));
        Assertions.assertEquals("gateway", copied.getMetadata().get("source"));
        Assertions.assertEquals(true, copied.getMetadata().get("enableReflection"));
        Assertions.assertEquals(IntentType.DISH_QUESTION, copied.getIntent());
    }
}
