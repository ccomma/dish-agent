package com.enterprise.langchain4j.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderTools 单元测试
 */
class OrderToolsTest {

    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        orderTools = new OrderTools();
    }

    @Test
    void testQueryOrderStatus_ExistingOrder() {
        OrderResult result = orderTools.queryOrderStatus("12345");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("12345", result.getOrderId());
        assertEquals("STORE_001", result.getStoreId());
        assertTrue(result.getItems().contains("宫保鸡丁") || result.getItems().contains("麻婆豆腐"));
    }

    @Test
    void testQueryOrderStatus_AnotherExistingOrder() {
        OrderResult result = orderTools.queryOrderStatus("67890");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("67890", result.getOrderId());
        assertTrue(result.getItems().contains("红烧肉"));
    }

    @Test
    void testQueryOrderStatus_OrderNotFound() {
        OrderResult result = orderTools.queryOrderStatus("NON_EXISTENT");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到订单") || result.getErrorMessage().contains("NON_EXISTENT"));
    }

    @Test
    void testOrderResult_SuccessFields() {
        OrderResult result = orderTools.queryOrderStatus("12345");

        assertTrue(result.isSuccess());
        assertEquals("12345", result.getOrderId());
        assertEquals("STORE_001", result.getStoreId());
        assertNotNull(result.getItems());
        assertEquals("配送中", result.getStatus());
        assertNotNull(result.getCreateTime());
    }

    @Test
    void testOrderResult_CompletedOrder() {
        OrderResult result = orderTools.queryOrderStatus("67890");

        assertTrue(result.isSuccess());
        assertEquals("已完成", result.getStatus());
    }

    @Test
    void testOrderResult_ShippedOrder() {
        OrderResult result = orderTools.queryOrderStatus("11111");

        assertTrue(result.isSuccess());
        assertEquals("已发货", result.getStatus());
    }

    @Test
    void testOrderResult_EmptyOrderId() {
        OrderResult result = orderTools.queryOrderStatus("");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到订单"));
    }

    @Test
    void testOrderResult_FailureHasNoFields() {
        OrderResult result = orderTools.queryOrderStatus("INVALID");

        assertFalse(result.isSuccess());
        assertNull(result.getOrderId());
        assertNull(result.getStoreId());
        assertNull(result.getItems());
        assertNull(result.getStatus());
        assertNull(result.getCreateTime());
        assertNotNull(result.getErrorMessage());
    }
}
