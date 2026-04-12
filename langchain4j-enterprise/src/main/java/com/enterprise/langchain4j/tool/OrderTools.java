package com.enterprise.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 订单查询工具
 * 从SaaSClient拆分，专门处理订单相关操作
 */
public class OrderTools {

    // ===== 模拟数据 =====
    private final Map<String, OrderResult> orders = new HashMap<>();

    public OrderTools() {
        initMockData();
    }

    private void initMockData() {
        orders.put("12345", OrderResult.success("12345", "STORE_001", "宫保鸡丁 x1, 麻婆豆腐 x1",
                "配送中", LocalDateTime.now().minusHours(1)));
        orders.put("67890", OrderResult.success("67890", "STORE_002", "红烧肉 x2",
                "已完成", LocalDateTime.now().minusDays(1)));
        orders.put("11111", OrderResult.success("11111", "STORE_001", "糖醋里脊 x1, 鱼香肉丝 x1",
                "已发货", LocalDateTime.now().minusHours(2)));
    }

    /**
     * 根据订单号查询订单状态
     */
    @Tool("根据订单号查询订单的详细信息和状态")
    public OrderResult queryOrderStatus(@P("订单号") String orderId) {
        OrderResult order = orders.get(orderId);
        if (order == null) {
            return OrderResult.failure("未找到订单: " + orderId + "\n请确认订单号是否正确。");
        }
        return order;
    }
}
