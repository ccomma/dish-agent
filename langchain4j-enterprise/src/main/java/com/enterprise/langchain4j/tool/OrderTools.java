package com.enterprise.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 订单查询工具
 * 从SaaSClient拆分，专门处理订单相关操作
 */
public class OrderTools {

    // ===== 模拟数据 =====
    private final Map<String, OrderInfo> orders = new HashMap<>();

    public OrderTools() {
        initMockData();
    }

    private void initMockData() {
        orders.put("12345", new OrderInfo("12345", "STORE_001", "宫保鸡丁 x1, 麻婆豆腐 x1",
                "配送中", LocalDateTime.now().minusHours(1)));
        orders.put("67890", new OrderInfo("67890", "STORE_002", "红烧肉 x2",
                "已完成", LocalDateTime.now().minusDays(1)));
        orders.put("11111", new OrderInfo("11111", "STORE_001", "糖醋里脊 x1, 鱼香肉丝 x1",
                "已发货", LocalDateTime.now().minusHours(2)));
    }

    /**
     * 根据订单号查询订单状态
     */
    @Tool("根据订单号查询订单的详细信息和状态")
    public String queryOrderStatus(@P("订单号") String orderId) {
        OrderInfo order = orders.get(orderId);
        if (order == null) {
            return "未找到订单: " + orderId + "\n请确认订单号是否正确。";
        }
        return "【订单查询结果】\n\n" + order.toString();
    }

    // ===== 数据模型 =====
    public static class OrderInfo {
        public String orderId;
        public String storeId;
        public String items;
        public String status;
        public LocalDateTime createTime;

        public OrderInfo(String orderId, String storeId, String items, String status, LocalDateTime createTime) {
            this.orderId = orderId;
            this.storeId = storeId;
            this.items = items;
            this.status = status;
            this.createTime = createTime;
        }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return String.format("订单号: %s\n门店: %s\n商品: %s\n状态: %s\n下单时间: %s",
                    orderId, storeId, items, status, createTime.format(fmt));
        }
    }
}
