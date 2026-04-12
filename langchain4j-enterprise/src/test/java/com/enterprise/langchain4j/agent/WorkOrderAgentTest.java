package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.tool.InventoryTools;
import com.enterprise.langchain4j.tool.OrderTools;
import com.enterprise.langchain4j.tool.RefundTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WorkOrderAgent 单元测试
 *
 * 测试 WorkOrderAgent 的工单处理逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderAgent 工单处理代理测试")
class WorkOrderAgentTest {

    @Mock
    private InventoryTools inventoryTools;

    @Mock
    private OrderTools orderTools;

    @Mock
    private RefundTools refundTools;

    private WorkOrderAgent workOrderAgent;

    @BeforeEach
    void setUp() {
        // 使用反射注入 mock tools（模拟带参构造函数的效果）
        workOrderAgent = new WorkOrderAgent() {
            {
                // Use reflection to inject mock tools
                try {
                    java.lang.reflect.Field inventoryField = WorkOrderAgent.class.getDeclaredField("inventoryTools");
                    inventoryField.setAccessible(true);
                    inventoryField.set(this, WorkOrderAgentTest.this.inventoryTools);

                    java.lang.reflect.Field orderField = WorkOrderAgent.class.getDeclaredField("orderTools");
                    orderField.setAccessible(true);
                    orderField.set(this, WorkOrderAgentTest.this.orderTools);

                    java.lang.reflect.Field refundField = WorkOrderAgent.class.getDeclaredField("refundTools");
                    refundField.setAccessible(true);
                    refundField.set(this, WorkOrderAgentTest.this.refundTools);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to inject mock tools", e);
                }
            }
        };
    }

    @Test
    @DisplayName("测试 process() 处理 QUERY_INVENTORY 意图")
    void testProcessQueryInventory() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_001")
                .intent(IntentType.QUERY_INVENTORY)
                .storeId("STORE_001")
                .dishName("宫保鸡丁")
                .userInput("门店还有宫保鸡丁吗")
                .build();

        when(inventoryTools.queryInventory("STORE_001", "宫保鸡丁"))
                .thenReturn("【库存查询结果】\n• 宫保鸡丁: 50份 (有货)");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        assertEquals("WorkOrderAgent", response.getAgentName());
        assertTrue(response.getContent().contains("宫保鸡丁"));
        verify(inventoryTools).queryInventory("STORE_001", "宫保鸡丁");
    }

    @Test
    @DisplayName("测试 process() 处理 QUERY_INVENTORY 无菜品名时查询所有库存")
    void testProcessQueryAllInventory() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_002")
                .intent(IntentType.QUERY_INVENTORY)
                .storeId("STORE_001")
                .userInput("门店有什么菜")
                .build();

        when(inventoryTools.queryAllInventory("STORE_001"))
                .thenReturn("【门店 STORE_001 库存情况】\n\n• 宫保鸡丁: 50份");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        verify(inventoryTools).queryAllInventory("STORE_001");
        verify(inventoryTools, never()).queryInventory(anyString(), anyString());
    }

    @Test
    @DisplayName("测试 process() 处理 QUERY_ORDER 意图")
    void testProcessQueryOrder() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_003")
                .intent(IntentType.QUERY_ORDER)
                .orderId("12345")
                .userInput("查询订单12345状态")
                .build();

        when(orderTools.queryOrderStatus("12345"))
                .thenReturn("【订单查询结果】\n\n订单号: 12345\n状态: 配送中");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        assertEquals("WorkOrderAgent", response.getAgentName());
        assertTrue(response.getContent().contains("12345"));
        assertTrue(response.getContent().contains("配送中"));
        verify(orderTools).queryOrderStatus("12345");
    }

    @Test
    @DisplayName("测试 process() 处理 QUERY_ORDER 但无 orderId 时返回失败")
    void testProcessQueryOrderWithoutOrderId() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_004")
                .intent(IntentType.QUERY_ORDER)
                .userInput("查询订单状态")
                .build();

        AgentResponse response = workOrderAgent.process(context);

        assertFalse(response.isSuccess());
        assertEquals("WorkOrderAgent", response.getAgentName());
        assertTrue(response.getContent().contains("请提供订单号"));
        verify(orderTools, never()).queryOrderStatus(anyString());
    }

    @Test
    @DisplayName("测试 process() 处理 CREATE_REFUND 意图")
    void testProcessCreateRefund() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_005")
                .intent(IntentType.CREATE_REFUND)
                .orderId("67890")
                .refundReason("菜品质量不好")
                .userInput("订单67890要退款因为菜品质量不好")
                .build();

        when(refundTools.createRefundTicket("67890", "菜品质量不好"))
                .thenReturn("【退款工单创建成功】\n\n工单号: TK1234567890");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        assertEquals("WorkOrderAgent", response.getAgentName());
        assertTrue(response.getContent().contains("退款工单创建成功"));
        verify(refundTools).createRefundTicket("67890", "菜品质量不好");
    }

    @Test
    @DisplayName("测试 process() 处理 CREATE_REFUND 但无 orderId 时返回失败")
    void testProcessCreateRefundWithoutOrderId() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_006")
                .intent(IntentType.CREATE_REFUND)
                .refundReason("太辣了")
                .userInput("要退款因为太辣了")
                .build();

        AgentResponse response = workOrderAgent.process(context);

        assertFalse(response.isSuccess());
        assertEquals("WorkOrderAgent", response.getAgentName());
        assertTrue(response.getContent().contains("请提供订单号"));
        verify(refundTools, never()).createRefundTicket(anyString(), anyString());
    }

    @Test
    @DisplayName("测试 process() 处理 CREATE_REFUND 使用默认退款原因")
    void testProcessCreateRefundWithDefaultReason() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_007")
                .intent(IntentType.CREATE_REFUND)
                .orderId("67890")
                .userInput("订单67890要退款")
                .build();

        when(refundTools.createRefundTicket("67890", "用户主动申请退款"))
                .thenReturn("【退款工单创建成功】\n\n工单号: TK1234567890");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        verify(refundTools).createRefundTicket("67890", "用户主动申请退款");
    }

    @Test
    @DisplayName("测试 process() 处理未知意图返回失败")
    void testProcessUnknownIntent() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_008")
                .intent(IntentType.UNKNOWN)
                .userInput("乱七八糟的内容")
                .build();

        AgentResponse response = workOrderAgent.process(context);

        assertFalse(response.isSuccess());
        assertTrue(response.getContent().contains("无法处理"));
    }

    @Test
    @DisplayName("测试 handleInventoryQuery() 返回包含 followUpHints 的响应")
    void testHandleInventoryQueryReturnsFollowUpHints() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_009")
                .intent(IntentType.QUERY_INVENTORY)
                .storeId("STORE_001")
                .dishName("麻婆豆腐")
                .build();

        when(inventoryTools.queryInventory("STORE_001", "麻婆豆腐"))
                .thenReturn("【库存查询结果】\n• 麻婆豆腐: 30份 (有货)");

        AgentResponse response = workOrderAgent.process(context);

        assertNotNull(response.getFollowUpHints());
        assertFalse(response.getFollowUpHints().isEmpty());
        assertTrue(response.getFollowUpHints().size() == 2);
    }

    @Test
    @DisplayName("测试 handleOrderQuery() 返回成功响应")
    void testHandleOrderQuerySuccess() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_010")
                .intent(IntentType.QUERY_ORDER)
                .orderId("11111")
                .build();

        when(orderTools.queryOrderStatus("11111"))
                .thenReturn("【订单查询结果】\n\n订单号: 11111\n状态: 已发货");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        assertNotNull(response.getContext());
        assertEquals("11111", response.getContext().getOrderId());
    }

    @Test
    @DisplayName("测试 handleRefundCreation() 返回成功响应")
    void testHandleRefundCreationSuccess() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_011")
                .intent(IntentType.CREATE_REFUND)
                .orderId("22222")
                .refundReason("等太久了")
                .build();

        when(refundTools.createRefundTicket("22222", "等太久了"))
                .thenReturn("【退款工单创建成功】\n\n工单号: TK9876543210");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        assertTrue(response.getContent().contains("退款工单创建成功"));
        assertTrue(response.getContent().contains("TK9876543210"));
    }

    @Test
    @DisplayName("测试 process() 处理时 storeId 为 null 使用默认值 STORE_001")
    void testProcessWithNullStoreId() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_012")
                .intent(IntentType.QUERY_INVENTORY)
                .dishName("宫保鸡丁")
                .userInput("宫保鸡丁还有吗")
                .build();

        when(inventoryTools.queryInventory("STORE_001", "宫保鸡丁"))
                .thenReturn("【库存查询结果】\n• 宫保鸡丁: 50份 (有货)");

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        verify(inventoryTools).queryInventory("STORE_001", "宫保鸡丁");
    }
}