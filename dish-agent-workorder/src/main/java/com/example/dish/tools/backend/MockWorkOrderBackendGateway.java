package com.example.dish.tools.backend;

import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.StoreListResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 后端实现，用于本地开发和演示环境。
 */
@Component
@ConditionalOnProperty(prefix = "backend", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWorkOrderBackendGateway implements WorkOrderBackendGateway {

    private final Map<String, Map<String, Integer>> storeInventory = new HashMap<>();
    private final Map<String, OrderResult> orders = new HashMap<>();
    private final List<OrderReference> refundableOrders = new ArrayList<>();
    private final List<RefundResult> refundTickets = new ArrayList<>();

    public MockWorkOrderBackendGateway() {
        initInventory();
        initOrders();
        initRefundableOrders();
    }

    private void initInventory() {
        Map<String, Integer> inventory1 = new HashMap<>();
        inventory1.put("宫保鸡丁", 50);
        inventory1.put("麻婆豆腐", 30);
        inventory1.put("红烧肉", 20);
        inventory1.put("糖醋里脊", 25);
        inventory1.put("鱼香肉丝", 40);
        storeInventory.put("STORE_001", inventory1);

        Map<String, Integer> inventory2 = new HashMap<>();
        inventory2.put("宫保鸡丁", 0);
        inventory2.put("麻婆豆腐", 15);
        inventory2.put("红烧肉", 10);
        storeInventory.put("STORE_002", inventory2);
    }

    private void initOrders() {
        orders.put("12345", OrderResult.success("12345", "STORE_001", "宫保鸡丁 x1, 麻婆豆腐 x1",
                "配送中", LocalDateTime.now().minusHours(1)));
        orders.put("67890", OrderResult.success("67890", "STORE_002", "红烧肉 x2",
                "已完成", LocalDateTime.now().minusDays(1)));
        orders.put("11111", OrderResult.success("11111", "STORE_001", "糖醋里脊 x1, 鱼香肉丝 x1",
                "已发货", LocalDateTime.now().minusHours(2)));
    }

    private void initRefundableOrders() {
        refundableOrders.add(new OrderReference("67890"));
        refundableOrders.add(new OrderReference("22222"));
    }

    @Override
    public InventoryResult queryAllInventory(String storeId) {
        Map<String, Integer> inventory = storeInventory.get(storeId);
        if (inventory == null) {
            return InventoryResult.failure("门店不存在: " + storeId);
        }

        List<InventoryResult.InventoryItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            items.add(new InventoryResult.InventoryItem(entry.getKey(), entry.getValue()));
        }
        return InventoryResult.success(storeId, items);
    }

    @Override
    public InventoryResult queryInventory(String storeId, String dishName) {
        Map<String, Integer> inventory = storeInventory.get(storeId);
        if (inventory == null) {
            return InventoryResult.failure("门店不存在: " + storeId);
        }
        if (dishName == null || dishName.isEmpty()) {
            return queryAllInventory(storeId);
        }

        Integer quantity = inventory.get(dishName);
        if (quantity == null) {
            return InventoryResult.failure("门店 " + storeId + " 没有菜品: " + dishName);
        }
        return InventoryResult.success(storeId, List.of(new InventoryResult.InventoryItem(dishName, quantity)));
    }

    @Override
    public StoreListResult getStoreList() {
        List<StoreListResult.StoreInfo> stores = List.of(
                new StoreListResult.StoreInfo("STORE_001", "旗舰店", "北京市朝阳区建国路88号"),
                new StoreListResult.StoreInfo("STORE_002", "二分店", "上海市浦东新区世纪大道100号"),
                new StoreListResult.StoreInfo("STORE_003", "三分店", "广州市天河区天河路99号")
        );
        return new StoreListResult(stores);
    }

    @Override
    public OrderResult queryOrderStatus(String orderId) {
        OrderResult order = orders.get(orderId);
        if (order == null) {
            return OrderResult.failure("未找到订单: " + orderId + "\n请确认订单号是否正确。");
        }
        return order;
    }

    @Override
    public RefundResult createRefundTicket(String orderId, String reason) {
        boolean exists = refundableOrders.stream().anyMatch(order -> order.orderId.equals(orderId));
        if (!exists) {
            return RefundResult.failure("未找到订单: " + orderId + "\n无法创建退款工单。\n注意：只有已完成或配送中的订单可以申请退款。");
        }

        String ticketId = "TK" + System.currentTimeMillis();
        RefundResult ticket = RefundResult.success(ticketId, orderId, reason, "待处理", LocalDateTime.now());
        refundTickets.add(ticket);
        return ticket;
    }

    @Override
    public RefundResult queryRefundStatus(String ticketId) {
        return refundTickets.stream()
                .filter(t -> ticketId.equals(t.getTicketId()))
                .findFirst()
                .orElseGet(() -> RefundResult.failure("未找到工单: " + ticketId + "\n请确认工单号是否正确。"));
    }

    private record OrderReference(String orderId) {
    }
}
