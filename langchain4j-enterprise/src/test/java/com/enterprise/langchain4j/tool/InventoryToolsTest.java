package com.enterprise.langchain4j.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InventoryTools 单元测试
 */
class InventoryToolsTest {

    private InventoryTools inventoryTools;

    @BeforeEach
    void setUp() {
        inventoryTools = new InventoryTools();
    }

    @Test
    void testQueryAllInventory_ReturnsValidResponse() {
        InventoryResult result = inventoryTools.queryAllInventory("STORE_001");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("STORE_001", result.getStoreId());
        assertNotNull(result.getItems());
        assertFalse(result.getItems().isEmpty());

        // Check items exist
        assertTrue(result.getItems().stream().anyMatch(i -> i.getDishName().equals("宫保鸡丁")));
        assertTrue(result.getItems().stream().anyMatch(i -> i.getDishName().equals("麻婆豆腐")));
        assertTrue(result.getItems().stream().anyMatch(i -> i.getDishName().equals("红烧肉")));
    }

    @Test
    void testQueryAllInventory_StoreNotFound() {
        InventoryResult result = inventoryTools.queryAllInventory("INVALID_STORE");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("门店不存在") || result.getErrorMessage().contains("INVALID_STORE"));
    }

    @Test
    void testQueryInventory_SpecificDish() {
        InventoryResult result = inventoryTools.queryInventory("STORE_001", "宫保鸡丁");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getItems().size());
        assertEquals("宫保鸡丁", result.getItems().get(0).getDishName());
        assertEquals(50, result.getItems().get(0).getQuantity());
        assertEquals("有货", result.getItems().get(0).getStatus());
    }

    @Test
    void testQueryInventory_DishNotFound() {
        InventoryResult result = inventoryTools.queryInventory("STORE_001", "不存在的菜品");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("没有菜品") || result.getErrorMessage().contains("不存在的菜品"));
    }

    @Test
    void testQueryInventory_StoreNotFound() {
        InventoryResult result = inventoryTools.queryInventory("INVALID_STORE", "宫保鸡丁");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("门店不存在") || result.getErrorMessage().contains("INVALID_STORE"));
    }

    @Test
    void testQueryInventory_NullDishName_ReturnsAllInventory() {
        InventoryResult result = inventoryTools.queryInventory("STORE_001", null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("STORE_001", result.getStoreId());
        assertTrue(result.getItems().size() > 1);
    }

    @Test
    void testQueryInventory_EmptyDishName_ReturnsAllInventory() {
        InventoryResult result = inventoryTools.queryInventory("STORE_001", "");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("STORE_001", result.getStoreId());
        assertTrue(result.getItems().size() > 1);
    }

    @Test
    void testGetStoreList_ReturnsValidResponse() {
        StoreListResult result = inventoryTools.getStoreList();

        assertNotNull(result);
        assertNotNull(result.getStores());
        assertEquals(3, result.getStores().size());

        assertTrue(result.getStores().stream().anyMatch(s -> s.getStoreId().equals("STORE_001")));
        assertTrue(result.getStores().stream().anyMatch(s -> s.getStoreId().equals("STORE_002")));
        assertTrue(result.getStores().stream().anyMatch(s -> s.getStoreId().equals("STORE_003")));
    }

    @Test
    void testGetStoreList_StoreDetails() {
        StoreListResult result = inventoryTools.getStoreList();

        StoreListResult.StoreInfo store1 = result.getStores().get(0);
        assertEquals("STORE_001", store1.getStoreId());
        assertEquals("旗舰店", store1.getName());
        assertEquals("北京市朝阳区建国路88号", store1.getAddress());
    }

    @Test
    void testQueryInventory_SoldOutDish() {
        InventoryResult result = inventoryTools.queryInventory("STORE_002", "宫保鸡丁");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("宫保鸡丁", result.getItems().get(0).getDishName());
        assertEquals(0, result.getItems().get(0).getQuantity());
        assertEquals("售罄", result.getItems().get(0).getStatus());
    }

    @Test
    void testQueryInventory_LowStockDish() {
        // STORE_002 has 红烧肉 with quantity 10, which is "有货"
        // For testing "库存紧张", we would need < 10
        InventoryResult result = inventoryTools.queryInventory("STORE_002", "红烧肉");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("红烧肉", result.getItems().get(0).getDishName());
        assertEquals(10, result.getItems().get(0).getQuantity());
        assertEquals("有货", result.getItems().get(0).getStatus());
    }

    @Test
    void testInventoryItem_Status() {
        InventoryResult.InventoryItem soldOut = new InventoryResult.InventoryItem("测试", 0);
        assertEquals("售罄", soldOut.getStatus());

        InventoryResult.InventoryItem lowStock = new InventoryResult.InventoryItem("测试", 5);
        assertEquals("库存紧张", lowStock.getStatus());

        InventoryResult.InventoryItem inStock = new InventoryResult.InventoryItem("测试", 50);
        assertEquals("有货", inStock.getStatus());
    }
}
