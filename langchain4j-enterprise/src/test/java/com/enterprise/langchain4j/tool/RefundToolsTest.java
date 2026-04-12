package com.enterprise.langchain4j.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RefundTools 单元测试
 */
class RefundToolsTest {

    private RefundTools refundTools;

    @BeforeEach
    void setUp() {
        refundTools = new RefundTools();
    }

    @Test
    void testCreateRefundTicket_Success() {
        RefundResult result = refundTools.createRefundTicket("67890", "菜品质量问题");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("67890", result.getOrderId());
        assertEquals("菜品质量问题", result.getReason());
        assertNotNull(result.getTicketId());
        assertEquals("待处理", result.getStatus());
    }

    @Test
    void testCreateRefundTicket_OrderNotFound() {
        RefundResult result = refundTools.createRefundTicket("NON_EXISTENT", "测试原因");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到订单") || result.getErrorMessage().contains("NON_EXISTENT"));
    }

    @Test
    void testCreateRefundTicket_AnotherValidOrder() {
        RefundResult result = refundTools.createRefundTicket("22222", "不想要了");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("22222", result.getOrderId());
    }

    @Test
    void testQueryRefundStatus_TicketNotFound() {
        RefundResult result = refundTools.queryRefundStatus("INVALID_TICKET");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到工单") || result.getErrorMessage().contains("INVALID_TICKET"));
    }

    @Test
    void testRefundResult_SuccessFields() {
        RefundResult result = refundTools.createRefundTicket("67890", "味道不好");

        assertTrue(result.isSuccess());
        assertNotNull(result.getTicketId());
        assertEquals("67890", result.getOrderId());
        assertEquals("味道不好", result.getReason());
        assertEquals("待处理", result.getStatus());
        assertNotNull(result.getCreateTime());
    }

    @Test
    void testRefundResult_FailureHasNoFields() {
        RefundResult result = refundTools.createRefundTicket("INVALID_ORDER", "测试");

        assertFalse(result.isSuccess());
        assertNull(result.getTicketId());
        assertNull(result.getOrderId());
        assertNull(result.getReason());
        assertNull(result.getStatus());
        assertNull(result.getCreateTime());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testCreateRefundTicket_DifferentReasons() {
        RefundResult result1 = refundTools.createRefundTicket("67890", "味道不好");
        RefundResult result2 = refundTools.createRefundTicket("67890", "送错菜了");

        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertEquals("味道不好", result1.getReason());
        assertEquals("送错菜了", result2.getReason());
    }

    @Test
    void testCreateRefundTicket_EmptyReason() {
        RefundResult result = refundTools.createRefundTicket("67890", "");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("待处理", result.getStatus());
    }

    @Test
    void testOrderReference_InnerClass() {
        RefundTools.OrderReference orderRef = new RefundTools.OrderReference(
                "11111",
                "STORE_001",
                "宫保鸡丁 x1"
        );

        assertEquals("11111", orderRef.orderId);
        assertEquals("STORE_001", orderRef.storeId);
        assertEquals("宫保鸡丁 x1", orderRef.items);
    }
}
