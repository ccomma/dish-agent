package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.classifier.IntentClassifier;
import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.RoutingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RoutingAgent 单元测试
 *
 * 测试 RoutingAgent 的路由决策逻辑、参数抽取和路由原因生成
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingAgent 路由代理测试")
class RoutingAgentTest {

    @Mock
    private IntentClassifier intentClassifier;

    private RoutingAgent routingAgent;

    @BeforeEach
    void setUp() {
        routingAgent = new RoutingAgent(intentClassifier);
    }

    @Test
    @DisplayName("测试 GREETING 意图路由到 ChatAgent")
    void testGreetingRoutesToChat() {
        when(intentClassifier.classify("你好")).thenReturn(IntentType.GREETING);

        RoutingDecision decision = routingAgent.route("你好", null);

        assertEquals(IntentType.GREETING, decision.intent());
        assertEquals(RoutingDecision.TARGET_CHAT, decision.targetAgent());
        assertNotNull(decision.context());
    }

    @Test
    @DisplayName("测试 GENERAL_CHAT 意图路由到 ChatAgent")
    void testGeneralChatRoutesToChat() {
        when(intentClassifier.classify("今天天气不错")).thenReturn(IntentType.GENERAL_CHAT);

        RoutingDecision decision = routingAgent.route("今天天气不错", null);

        assertEquals(IntentType.GENERAL_CHAT, decision.intent());
        assertEquals(RoutingDecision.TARGET_CHAT, decision.targetAgent());
    }

    @Test
    @DisplayName("测试 DISH_QUESTION 意图路由到 DishKnowledgeAgent")
    void testDishQuestionRoutesToDishKnowledge() {
        when(intentClassifier.classify("宫保鸡丁好吃吗")).thenReturn(IntentType.DISH_QUESTION);

        RoutingDecision decision = routingAgent.route("宫保鸡丁好吃吗", null);

        assertEquals(IntentType.DISH_QUESTION, decision.intent());
        assertEquals(RoutingDecision.TARGET_DISH_KNOWLEDGE, decision.targetAgent());
    }

    @Test
    @DisplayName("测试 DISH_INGREDIENT 意图路由到 DishKnowledgeAgent")
    void testDishIngredientRoutesToDishKnowledge() {
        when(intentClassifier.classify("宫保鸡丁用什么肉")).thenReturn(IntentType.DISH_INGREDIENT);

        RoutingDecision decision = routingAgent.route("宫保鸡丁用什么肉", null);

        assertEquals(IntentType.DISH_INGREDIENT, decision.intent());
        assertEquals(RoutingDecision.TARGET_DISH_KNOWLEDGE, decision.targetAgent());
    }

    @Test
    @DisplayName("测试 DISH_COOKING_METHOD 意图路由到 DishKnowledgeAgent")
    void testDishCookingMethodRoutesToDishKnowledge() {
        when(intentClassifier.classify("宫保鸡丁怎么做")).thenReturn(IntentType.DISH_COOKING_METHOD);

        RoutingDecision decision = routingAgent.route("宫保鸡丁怎么做", null);

        assertEquals(IntentType.DISH_COOKING_METHOD, decision.intent());
        assertEquals(RoutingDecision.TARGET_DISH_KNOWLEDGE, decision.targetAgent());
    }

    @Test
    @DisplayName("测试 POLICY_QUESTION 意图路由到 DishKnowledgeAgent")
    void testPolicyQuestionRoutesToDishKnowledge() {
        when(intentClassifier.classify("退款政策是什么")).thenReturn(IntentType.POLICY_QUESTION);

        RoutingDecision decision = routingAgent.route("退款政策是什么", null);

        assertEquals(IntentType.POLICY_QUESTION, decision.intent());
        assertEquals(RoutingDecision.TARGET_DISH_KNOWLEDGE, decision.targetAgent());
    }

    @Test
    @DisplayName("测试 QUERY_INVENTORY 意图路由到 WorkOrderAgent")
    void testQueryInventoryRoutesToWorkOrder() {
        when(intentClassifier.classify("门店还有宫保鸡丁吗")).thenReturn(IntentType.QUERY_INVENTORY);

        RoutingDecision decision = routingAgent.route("门店还有宫保鸡丁吗", null);

        assertEquals(IntentType.QUERY_INVENTORY, decision.intent());
        assertEquals(RoutingDecision.TARGET_WORK_ORDER, decision.targetAgent());
        // 验证参数抽取：storeId 应该被设置
        assertEquals("STORE_001", decision.context().getStoreId());
    }

    @Test
    @DisplayName("测试 QUERY_ORDER 意图路由到 WorkOrderAgent")
    void testQueryOrderRoutesToWorkOrder() {
        when(intentClassifier.classify("查询订单12345状态")).thenReturn(IntentType.QUERY_ORDER);

        RoutingDecision decision = routingAgent.route("查询订单12345状态", null);

        assertEquals(IntentType.QUERY_ORDER, decision.intent());
        assertEquals(RoutingDecision.TARGET_WORK_ORDER, decision.targetAgent());
        // 验证参数抽取：orderId 应该被设置
        assertEquals("12345", decision.context().getOrderId());
    }

    @Test
    @DisplayName("测试 CREATE_REFUND 意图路由到 WorkOrderAgent")
    void testCreateRefundRoutesToWorkOrder() {
        // Input includes a 5+ digit order ID for extraction
        when(intentClassifier.classify("订单88888要退款因为太辣了")).thenReturn(IntentType.CREATE_REFUND);

        RoutingDecision decision = routingAgent.route("订单88888要退款因为太辣了", null);

        assertEquals(IntentType.CREATE_REFUND, decision.intent());
        assertEquals(RoutingDecision.TARGET_WORK_ORDER, decision.targetAgent());
        // 验证参数抽取：orderId 和 refundReason 应该被设置
        assertNotNull(decision.context().getOrderId());
        assertNotNull(decision.context().getRefundReason());
    }

    @Test
    @DisplayName("测试 buildContext 保留 existingContext 中的 sessionId")
    void testBuildContextPreservesSessionId() {
        when(intentClassifier.classify("你好")).thenReturn(IntentType.GREETING);

        AgentContext existingContext = AgentContext.builder()
                .sessionId("EXISTING_SESSION")
                .build();

        RoutingDecision decision = routingAgent.route("你好", existingContext);

        assertEquals("EXISTING_SESSION", decision.context().getSessionId());
    }

    @Test
    @DisplayName("测试 buildContext 为 QUERY_INVENTORY 抽取 storeId 和 dishName")
    void testBuildContextExtractsInventoryParams() {
        when(intentClassifier.classify("门店的宫保鸡丁还有吗")).thenReturn(IntentType.QUERY_INVENTORY);

        RoutingDecision decision = routingAgent.route("门店的宫保鸡丁还有吗", null);

        assertEquals("STORE_001", decision.context().getStoreId());
        assertEquals("宫保鸡丁", decision.context().getDishName());
    }

    @Test
    @DisplayName("测试 buildContext 为 QUERY_ORDER 抽取 orderId")
    void testBuildContextExtractsOrderId() {
        when(intentClassifier.classify("查询订单99999状态")).thenReturn(IntentType.QUERY_ORDER);

        RoutingDecision decision = routingAgent.route("查询订单99999状态", null);

        assertEquals("99999", decision.context().getOrderId());
    }

    @Test
    @DisplayName("测试 buildContext 为 CREATE_REFUND 抽取 orderId 和 refundReason")
    void testBuildContextExtractsRefundParams() {
        when(intentClassifier.classify("订单88888要退款因为味道不好")).thenReturn(IntentType.CREATE_REFUND);

        RoutingDecision decision = routingAgent.route("订单88888要退款因为味道不好", null);

        assertEquals("88888", decision.context().getOrderId());
        assertTrue(decision.context().getRefundReason().contains("味道不好") ||
                   decision.context().getRefundReason().equals("用户主动申请"));
    }

    @Test
    @DisplayName("测试 RoutingDecision.isChatRouting() 对闲聊意图返回 true")
    void testIsChatRouting() {
        when(intentClassifier.classify("你好")).thenReturn(IntentType.GREETING);

        RoutingDecision decision = routingAgent.route("你好", null);

        assertTrue(decision.isChatRouting());
        assertFalse(decision.isDishKnowledgeRouting());
        assertFalse(decision.isWorkOrderRouting());
    }

    @Test
    @DisplayName("测试 RoutingDecision.isDishKnowledgeRouting() 对菜品意图返回 true")
    void testIsDishKnowledgeRouting() {
        when(intentClassifier.classify("宫保鸡丁怎么做")).thenReturn(IntentType.DISH_COOKING_METHOD);

        RoutingDecision decision = routingAgent.route("宫保鸡丁怎么做", null);

        assertTrue(decision.isDishKnowledgeRouting());
        assertFalse(decision.isChatRouting());
        assertFalse(decision.isWorkOrderRouting());
    }

    @Test
    @DisplayName("测试 RoutingDecision.isWorkOrderRouting() 对工单意图返回 true")
    void testIsWorkOrderRouting() {
        when(intentClassifier.classify("查询库存")).thenReturn(IntentType.QUERY_INVENTORY);

        RoutingDecision decision = routingAgent.route("查询库存", null);

        assertTrue(decision.isWorkOrderRouting());
        assertFalse(decision.isChatRouting());
        assertFalse(decision.isDishKnowledgeRouting());
    }

    @Test
    @DisplayName("测试 generateRoutingReason 为每种意图类型生成原因")
    void testRoutingReasonGeneration() {
        // GREETING
        when(intentClassifier.classify("hi")).thenReturn(IntentType.GREETING);
        RoutingDecision greetingDecision = routingAgent.route("hi", null);
        assertEquals("问候语，直接进入闲聊模式", greetingDecision.reason());

        // DISH_QUESTION
        when(intentClassifier.classify("菜品问题")).thenReturn(IntentType.DISH_QUESTION);
        RoutingDecision dishDecision = routingAgent.route("菜品问题", null);
        assertEquals("菜品咨询，使用知识库检索", dishDecision.reason());

        // QUERY_INVENTORY
        when(intentClassifier.classify("库存")).thenReturn(IntentType.QUERY_INVENTORY);
        RoutingDecision inventoryDecision = routingAgent.route("库存", null);
        assertEquals("库存查询，使用业务工具", inventoryDecision.reason());

        // CREATE_REFUND
        when(intentClassifier.classify("退款")).thenReturn(IntentType.CREATE_REFUND);
        RoutingDecision refundDecision = routingAgent.route("退款", null);
        assertEquals("退款申请，使用业务工具", refundDecision.reason());
    }
}