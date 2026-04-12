package com.enterprise.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 退款工单工具
 * 从SaaSClient拆分，专门处理退款相关操作
 */
public class RefundTools {

    // ===== 模拟数据 =====
    private final List<OrderReference> orders = new ArrayList<>();
    private final List<RefundResult> refundTickets = new ArrayList<>();

    public RefundTools() {
        initMockData();
    }

    private void initMockData() {
        // 模拟可退款的订单
        orders.add(new OrderReference("67890", "STORE_002", "红烧肉 x2"));
        orders.add(new OrderReference("22222", "STORE_001", "宫保鸡丁 x1"));
    }

    /**
     * 创建退款/售后工单
     */
    @Tool("创建退款或售后工单")
    public RefundResult createRefundTicket(
            @P("订单号") String orderId,
            @P("退款原因") String reason) {

        // 验证订单是否存在
        OrderReference order = orders.stream()
            .filter(o -> o.orderId.equals(orderId))
            .findFirst()
            .orElse(null);

        if (order == null) {
            return RefundResult.failure("未找到订单: " + orderId + "\n无法创建退款工单。\n" +
                   "注意：只有已完成或配送中的订单可以申请退款。");
        }

        // 生成工单号
        String ticketId = "TK" + System.currentTimeMillis();
        LocalDateTime createTime = LocalDateTime.now();

        // 创建工单
        RefundResult ticket = RefundResult.success(ticketId, orderId, reason, "待处理", createTime);
        refundTickets.add(ticket);

        return ticket;
    }

    /**
     * 查询退款工单状态
     */
    @Tool("查询退款工单的状态")
    public RefundResult queryRefundStatus(@P("工单号") String ticketId) {
        RefundResult ticket = refundTickets.stream()
            .filter(t -> ticketId.equals(t.getTicketId()))
            .findFirst()
            .orElse(null);

        if (ticket == null) {
            return RefundResult.failure("未找到工单: " + ticketId + "\n请确认工单号是否正确。");
        }

        return ticket;
    }

    // ===== 数据模型 =====
    public static class OrderReference {
        public String orderId;
        public String storeId;
        public String items;

        public OrderReference(String orderId, String storeId, String items) {
            this.orderId = orderId;
            this.storeId = storeId;
            this.items = items;
        }
    }
}
