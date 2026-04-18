package com.example.dish.tools.backend;

import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.RefundResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MockWorkOrderBackendGatewayTest {

    @Test
    void shouldQueryInventoryFromMockBackend() {
        MockWorkOrderBackendGateway gateway = new MockWorkOrderBackendGateway();

        InventoryResult result = gateway.queryInventory("STORE_001", "宫保鸡丁");

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("STORE_001", result.getStoreId());
        Assertions.assertEquals(1, result.getItems().size());
    }

    @Test
    void shouldQueryOrderFromMockBackend() {
        MockWorkOrderBackendGateway gateway = new MockWorkOrderBackendGateway();

        OrderResult result = gateway.queryOrderStatus("12345");

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("12345", result.getOrderId());
    }

    @Test
    void shouldCreateRefundTicketInMockBackend() {
        MockWorkOrderBackendGateway gateway = new MockWorkOrderBackendGateway();

        RefundResult result = gateway.createRefundTicket("67890", "菜品温度不理想");

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("67890", result.getOrderId());
    }
}
