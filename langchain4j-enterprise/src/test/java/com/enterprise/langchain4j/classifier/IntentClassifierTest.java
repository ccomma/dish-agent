package com.enterprise.langchain4j.classifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentClassifier 单元测试
 *
 * 注意：意图分类功能依赖 LLM API，属于集成测试范畴。
 * 以下测试主要验证 IntentType 枚举的辅助方法。
 */
@DisplayName("IntentClassifier 意图分类测试")
class IntentClassifierTest {

    @Test
    @DisplayName("测试 IntentType 辅助方法 - isChat()")
    void testIsChat() {
        assertTrue(IntentType.GREETING.isChat());
        assertTrue(IntentType.GENERAL_CHAT.isChat());
        assertFalse(IntentType.DISH_QUESTION.isChat());
        assertFalse(IntentType.DISH_INGREDIENT.isChat());
        assertFalse(IntentType.DISH_COOKING_METHOD.isChat());
        assertFalse(IntentType.POLICY_QUESTION.isChat());
        assertFalse(IntentType.QUERY_INVENTORY.isChat());
        assertFalse(IntentType.QUERY_ORDER.isChat());
        assertFalse(IntentType.CREATE_REFUND.isChat());
        assertFalse(IntentType.UNKNOWN.isChat());
    }

    @Test
    @DisplayName("测试 IntentType 辅助方法 - isRAG()")
    void testIsRAG() {
        assertFalse(IntentType.GREETING.isRAG());
        assertFalse(IntentType.GENERAL_CHAT.isRAG());
        assertTrue(IntentType.DISH_QUESTION.isRAG());
        assertTrue(IntentType.DISH_INGREDIENT.isRAG());
        assertTrue(IntentType.DISH_COOKING_METHOD.isRAG());
        assertTrue(IntentType.POLICY_QUESTION.isRAG());
        assertFalse(IntentType.QUERY_INVENTORY.isRAG());
        assertFalse(IntentType.QUERY_ORDER.isRAG());
        assertFalse(IntentType.CREATE_REFUND.isRAG());
        assertFalse(IntentType.UNKNOWN.isRAG());
    }

    @Test
    @DisplayName("测试 IntentType 辅助方法 - isFunctionCall()")
    void testIsFunctionCall() {
        assertFalse(IntentType.GREETING.isFunctionCall());
        assertFalse(IntentType.GENERAL_CHAT.isFunctionCall());
        assertFalse(IntentType.DISH_QUESTION.isFunctionCall());
        assertFalse(IntentType.DISH_INGREDIENT.isFunctionCall());
        assertFalse(IntentType.DISH_COOKING_METHOD.isFunctionCall());
        assertFalse(IntentType.POLICY_QUESTION.isFunctionCall());
        assertTrue(IntentType.QUERY_INVENTORY.isFunctionCall());
        assertTrue(IntentType.QUERY_ORDER.isFunctionCall());
        assertTrue(IntentType.CREATE_REFUND.isFunctionCall());
        assertFalse(IntentType.UNKNOWN.isFunctionCall());
    }

    @Test
    @DisplayName("测试 IntentType getDescription()")
    void testGetDescription() {
        assertEquals("问候语", IntentType.GREETING.getDescription());
        assertEquals("通用闲聊", IntentType.GENERAL_CHAT.getDescription());
        assertEquals("菜品问题", IntentType.DISH_QUESTION.getDescription());
        assertEquals("菜品成分", IntentType.DISH_INGREDIENT.getDescription());
        assertEquals("菜品做法", IntentType.DISH_COOKING_METHOD.getDescription());
        assertEquals("政策规则", IntentType.POLICY_QUESTION.getDescription());
        assertEquals("库存查询", IntentType.QUERY_INVENTORY.getDescription());
        assertEquals("订单查询", IntentType.QUERY_ORDER.getDescription());
        assertEquals("申请退款", IntentType.CREATE_REFUND.getDescription());
        assertEquals("未知意图", IntentType.UNKNOWN.getDescription());
    }

    @Test
    @DisplayName("测试 IntentType.values() 返回所有枚举值")
    void testValues() {
        IntentType[] values = IntentType.values();
        assertEquals(10, values.length);
    }

    @Test
    @DisplayName("测试 IntentClassifier 实例化")
    void testClassifierInstantiation() {
        // 验证 IntentClassifier 可以成功实例化
        IntentClassifier classifier = new IntentClassifier();
        assertNotNull(classifier);
    }
}
