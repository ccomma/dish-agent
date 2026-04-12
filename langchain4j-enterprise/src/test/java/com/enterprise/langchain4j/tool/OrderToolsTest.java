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
        String result = orderTools.queryOrderStatus("12345");

        assertNotNull(result);
        assertTrue(result.contains("12345"));
        assertTrue(result.contains("订单号") || result.contains("STORE_001"));
        assertTrue(result.contains("宫保鸡丁") || result.contains("麻婆豆腐"));
    }

    @Test
    void testQueryOrderStatus_AnotherExistingOrder() {
        String result = orderTools.queryOrderStatus("67890");

        assertNotNull(result);
        assertTrue(result.contains("67890"));
        assertTrue(result.contains("红烧肉"));
    }

    @Test
    void testQueryOrderStatus_OrderNotFound() {
        String result = orderTools.queryOrderStatus("NON_EXISTENT");

        assertNotNull(result);
        assertTrue(result.contains("未找到订单") || result.contains("NON_EXISTENT"));
    }

    @Test
    void testOrderInfo_InnerClass() {
        OrderTools.OrderInfo orderInfo = new OrderTools.OrderInfo(
                "99999",
                "STORE_003",
                "测试菜品 x1",
                "测试状态",
                java.time.LocalDateTime.now()
        );

        assertEquals("99999", orderInfo.orderId);
        assertEquals("STORE_003", orderInfo.storeId);
        assertEquals("测试菜品 x1", orderInfo.items);
        assertEquals("测试状态", orderInfo.status);
        assertNotNull(orderInfo.createTime);
    }

    @Test
    void testOrderInfo_ToString() {
        OrderTools.OrderInfo orderInfo = new OrderTools.OrderInfo(
                "11111",
                "STORE_001",
                "糖醋里脊 x1, 鱼香肉丝 x1",
                "已发货",
                java.time.LocalDateTime.of(2024, 1, 15, 10, 30)
        );

        String str = orderInfo.toString();

        assertNotNull(str);
        assertTrue(str.contains("11111"));
        assertTrue(str.contains("STORE_001"));
        assertTrue(str.contains("糖醋里脊"));
        assertTrue(str.contains("鱼香肉丝"));
        assertTrue(str.contains("已发货"));
    }

    @Test
    void testQueryOrderStatus_DeliveryOrder() {
        String result = orderTools.queryOrderStatus("12345");

        assertNotNull(result);
        assertTrue(result.contains("配送中"));
    }

    @Test
    void testQueryOrderStatus_CompletedOrder() {
        String result = orderTools.queryOrderStatus("67890");

        assertNotNull(result);
        assertTrue(result.contains("已完成"));
    }

    @Test
    void testQueryOrderStatus_ShippedOrder() {
        String result = orderTools.queryOrderStatus("11111");

        assertNotNull(result);
        assertTrue(result.contains("已发货"));
    }

    @Test
    void testQueryOrderStatus_EmptyOrderId() {
        String result = orderTools.queryOrderStatus("");

        assertNotNull(result);
        assertTrue(result.contains("未找到订单"));
    }
}
