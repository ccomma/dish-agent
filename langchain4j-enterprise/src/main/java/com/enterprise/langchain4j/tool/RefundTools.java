package com.enterprise.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 退款工单工具
 * 从SaaSClient拆分，专门处理退款相关操作
 */
public class RefundTools {

    // ===== 模拟数据 =====
    private final List<OrderReference> orders = new ArrayList<>();
    private final List<RefundTicket> refundTickets = new ArrayList<>();

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
    public String createRefundTicket(
            @P("订单号") String orderId,
            @P("退款原因") String reason) {

        // 验证订单是否存在
        OrderReference order = orders.stream()
            .filter(o -> o.orderId.equals(orderId))
            .findFirst()
            .orElse(null);

        if (order == null) {
            return "未找到订单: " + orderId + "\n无法创建退款工单。\n" +
                   "注意：只有已完成或配送中的订单可以申请退款。";
        }

        // 生成工单号
        String ticketId = "TK" + System.currentTimeMillis();

        // 创建工单
        RefundTicket ticket = new RefundTicket(ticketId, orderId, reason);
        refundTickets.add(ticket);

        return "【退款工单创建成功】\n\n" + ticket.toString() +
               "\n\n备注：退款将在3-7个工作日内原路返回。";
    }

    /**
     * 查询退款工单状态
     */
    @Tool("查询退款工单的状态")
    public String queryRefundStatus(@P("工单号") String ticketId) {
        RefundTicket ticket = refundTickets.stream()
            .filter(t -> t.ticketId.equals(ticketId))
            .findFirst()
            .orElse(null);

        if (ticket == null) {
            return "未找到工单: " + ticketId + "\n请确认工单号是否正确。";
        }

        return "【工单查询结果】\n\n" + ticket.toString();
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

    public static class RefundTicket {
        public String ticketId;
        public String orderId;
        public String reason;
        public String status;
        public LocalDateTime createTime;

        public RefundTicket(String ticketId, String orderId, String reason) {
            this.ticketId = ticketId;
            this.orderId = orderId;
            this.reason = reason;
            this.status = "待处理";
            this.createTime = LocalDateTime.now();
        }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return String.format("工单号: %s\n订单号: %s\n原因: %s\n状态: %s\n创建时间: %s",
                    ticketId, orderId, reason, status, createTime.format(fmt));
        }
    }
}
