package com.enterprise.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存查询工具
 * 从SaaSClient拆分，专门处理库存相关操作
 */
public class InventoryTools {

    // ===== 模拟数据 =====
    private final Map<String, Map<String, Integer>> storeInventory = new HashMap<>();

    public InventoryTools() {
        initMockData();
    }

    private void initMockData() {
        Map<String, Integer> inventory1 = new HashMap<>();
        inventory1.put("宫保鸡丁", 50);
        inventory1.put("麻婆豆腐", 30);
        inventory1.put("红烧肉", 20);
        inventory1.put("糖醋里脊", 25);
        inventory1.put("鱼香肉丝", 40);
        storeInventory.put("STORE_001", inventory1);

        Map<String, Integer> inventory2 = new HashMap<>();
        inventory2.put("宫保鸡丁", 0); // 售罄
        inventory2.put("麻婆豆腐", 15);
        inventory2.put("红烧肉", 10);
        storeInventory.put("STORE_002", inventory2);
    }

    /**
     * 查询指定门店的所有菜品库存
     */
    @Tool("查询指定门店的菜品库存")
    public InventoryResult queryAllInventory(@P("门店ID") String storeId) {
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

    /**
     * 查询指定门店的特定菜品库存
     */
    @Tool("查询指定门店的特定菜品库存")
    public InventoryResult queryInventory(
            @P("门店ID") String storeId,
            @P("菜品名称") String dishName) {

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

        List<InventoryResult.InventoryItem> items = List.of(
            new InventoryResult.InventoryItem(dishName, quantity)
        );
        return InventoryResult.success(storeId, items);
    }

    /**
     * 获取门店列表
     */
    @Tool("获取所有可用门店的信息")
    public StoreListResult getStoreList() {
        List<StoreListResult.StoreInfo> stores = List.of(
            new StoreListResult.StoreInfo("STORE_001", "旗舰店", "北京市朝阳区建国路88号"),
            new StoreListResult.StoreInfo("STORE_002", "二分店", "上海市浦东新区世纪大道100号"),
            new StoreListResult.StoreInfo("STORE_003", "三分店", "广州市天河区天河路99号")
        );
        return new StoreListResult(stores);
    }
}
