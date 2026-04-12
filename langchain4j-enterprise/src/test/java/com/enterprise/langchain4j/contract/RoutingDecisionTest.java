package com.enterprise.langchain4j.contract;

import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoutingDecision 单元测试
 */
@DisplayName("RoutingDecision 测试")
class RoutingDecisionTest {

    private AgentContext defaultContext;

    @Nested
    @DisplayName("路由类型判断方法测试")
    class RoutingTypeTests {

        @BeforeEach
        void setUp() {
            defaultContext = AgentContext.createDefault();
        }

        @Test
        @DisplayName("isChatRouting() 应正确识别闲聊路由")
        void testIsChatRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.GREETING,
                RoutingDecision.TARGET_CHAT,
                "问候语",
                defaultContext
            );

            assertTrue(decision.isChatRouting());
            assertFalse(decision.isDishKnowledgeRouting());
            assertFalse(decision.isWorkOrderRouting());
        }

        @Test
        @DisplayName("isDishKnowledgeRouting() 应正确识别菜品知识路由")
        void testIsDishKnowledgeRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.DISH_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "菜品问题",
                defaultContext
            );

            assertFalse(decision.isChatRouting());
            assertTrue(decision.isDishKnowledgeRouting());
            assertFalse(decision.isWorkOrderRouting());
        }

        @Test
        @DisplayName("isWorkOrderRouting() 应正确识工单处理路由")
        void testIsWorkOrderRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.QUERY_INVENTORY,
                RoutingDecision.TARGET_WORK_ORDER,
                "库存查询",
                defaultContext
            );

            assertFalse(decision.isChatRouting());
            assertFalse(decision.isDishKnowledgeRouting());
            assertTrue(decision.isWorkOrderRouting());
        }

        @Test
        @DisplayName("通用闲聊意图应路由到 Chat")
        void testGeneralChatRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.GENERAL_CHAT,
                RoutingDecision.TARGET_CHAT,
                "通用闲聊",
                defaultContext
            );

            assertTrue(decision.isChatRouting());
            assertFalse(decision.isDishKnowledgeRouting());
            assertFalse(decision.isWorkOrderRouting());
        }
    }

    @Nested
    @DisplayName("意图类型与路由目标对应测试")
    class IntentRoutingMappingTests {

        @Test
        @DisplayName("DISH_QUESTION 应路由到 dish-knowledge")
        void testDishQuestionRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.DISH_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "询问菜品",
                null
            );

            assertTrue(decision.isDishKnowledgeRouting());
        }

        @Test
        @DisplayName("DISH_INGREDIENT 应路由到 dish-knowledge")
        void testDishIngredientRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.DISH_INGREDIENT,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "询问成分",
                null
            );

            assertTrue(decision.isDishKnowledgeRouting());
        }

        @Test
        @DisplayName("DISH_COOKING_METHOD 应路由到 dish-knowledge")
        void testDishCookingMethodRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.DISH_COOKING_METHOD,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "询问做法",
                null
            );

            assertTrue(decision.isDishKnowledgeRouting());
        }

        @Test
        @DisplayName("POLICY_QUESTION 应路由到 dish-knowledge")
        void testPolicyQuestionRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.POLICY_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "政策问题",
                null
            );

            assertTrue(decision.isDishKnowledgeRouting());
        }

        @Test
        @DisplayName("QUERY_INVENTORY 应路由到 work-order")
        void testQueryInventoryRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.QUERY_INVENTORY,
                RoutingDecision.TARGET_WORK_ORDER,
                "库存查询",
                null
            );

            assertTrue(decision.isWorkOrderRouting());
        }

        @Test
        @DisplayName("QUERY_ORDER 应路由到 work-order")
        void testQueryOrderRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.QUERY_ORDER,
                RoutingDecision.TARGET_WORK_ORDER,
                "订单查询",
                null
            );

            assertTrue(decision.isWorkOrderRouting());
        }

        @Test
        @DisplayName("CREATE_REFUND 应路由到 work-order")
        void testCreateRefundRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.CREATE_REFUND,
                RoutingDecision.TARGET_WORK_ORDER,
                "退款申请",
                null
            );

            assertTrue(decision.isWorkOrderRouting());
        }

        @Test
        @DisplayName("GREETING 应路由到 chat")
        void testGreetingRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.GREETING,
                RoutingDecision.TARGET_CHAT,
                "问候",
                null
            );

            assertTrue(decision.isChatRouting());
        }

        @Test
        @DisplayName("GENERAL_CHAT 应路由到 chat")
        void testGeneralChatIntentRouting() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.GENERAL_CHAT,
                RoutingDecision.TARGET_CHAT,
                "闲聊",
                null
            );

            assertTrue(decision.isChatRouting());
        }
    }

    @Nested
    @DisplayName("record 字段访问测试")
    class RecordFieldTests {

        @Test
        @DisplayName("应正确访问 record 的各个字段")
        void testRecordFields() {
            AgentContext ctx = AgentContext.builder()
                .sessionId("test-session")
                .build();

            RoutingDecision decision = new RoutingDecision(
                IntentType.DISH_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "测试原因",
                ctx
            );

            assertEquals(IntentType.DISH_QUESTION, decision.intent());
            assertEquals(RoutingDecision.TARGET_DISH_KNOWLEDGE, decision.targetAgent());
            assertEquals("测试原因", decision.reason());
            assertSame(ctx, decision.context());
        }

        @Test
        @DisplayName("context 可以为 null")
        void testNullContext() {
            RoutingDecision decision = new RoutingDecision(
                IntentType.GREETING,
                RoutingDecision.TARGET_CHAT,
                "问候",
                null
            );

            assertNull(decision.context());
            assertTrue(decision.isChatRouting());
        }
    }

    @Nested
    @DisplayName("目标Agent常量测试")
    class TargetAgentConstantsTests {

        @Test
        @DisplayName("TARGET_DISH_KNOWLEDGE 常量值应为 'dish-knowledge'")
        void testTargetDishKnowledgeConstant() {
            assertEquals("dish-knowledge", RoutingDecision.TARGET_DISH_KNOWLEDGE);
        }

        @Test
        @DisplayName("TARGET_WORK_ORDER 常量值应为 'work-order'")
        void testTargetWorkOrderConstant() {
            assertEquals("work-order", RoutingDecision.TARGET_WORK_ORDER);
        }

        @Test
        @DisplayName("TARGET_CHAT 常量值应为 'chat'")
        void testTargetChatConstant() {
            assertEquals("chat", RoutingDecision.TARGET_CHAT);
        }
    }
}
