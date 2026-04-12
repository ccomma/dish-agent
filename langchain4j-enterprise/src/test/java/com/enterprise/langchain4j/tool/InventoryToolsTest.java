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
        String result = inventoryTools.queryAllInventory("STORE_001");

        assertNotNull(result);
        assertTrue(result.contains("STORE_001"));
        assertTrue(result.contains("宫保鸡丁"));
        assertTrue(result.contains("麻婆豆腐"));
        assertTrue(result.contains("红烧肉"));
    }

    @Test
    void testQueryAllInventory_StoreNotFound() {
        String result = inventoryTools.queryAllInventory("INVALID_STORE");

        assertNotNull(result);
        assertTrue(result.contains("门店不存在") || result.contains("INVALID_STORE"));
    }

    @Test
    void testQueryInventory_SpecificDish() {
        String result = inventoryTools.queryInventory("STORE_001", "宫保鸡丁");

        assertNotNull(result);
        assertTrue(result.contains("宫保鸡丁"));
        assertTrue(result.contains("50") || result.contains("有货") || result.contains("库存"));
    }

    @Test
    void testQueryInventory_DishNotFound() {
        String result = inventoryTools.queryInventory("STORE_001", "不存在的菜品");

        assertNotNull(result);
        assertTrue(result.contains("没有菜品") || result.contains("不存在的菜品"));
    }

    @Test
    void testQueryInventory_StoreNotFound() {
        String result = inventoryTools.queryInventory("INVALID_STORE", "宫保鸡丁");

        assertNotNull(result);
        assertTrue(result.contains("门店不存在") || result.contains("INVALID_STORE"));
    }

    @Test
    void testQueryInventory_NullDishName_ReturnsAllInventory() {
        String result = inventoryTools.queryInventory("STORE_001", null);

        assertNotNull(result);
        assertTrue(result.contains("STORE_001"));
    }

    @Test
    void testQueryInventory_EmptyDishName_ReturnsAllInventory() {
        String result = inventoryTools.queryInventory("STORE_001", "");

        assertNotNull(result);
        assertTrue(result.contains("STORE_001"));
    }

    @Test
    void testGetStoreList_ReturnsValidResponse() {
        String result = inventoryTools.getStoreList();

        assertNotNull(result);
        assertTrue(result.contains("STORE_001"));
        assertTrue(result.contains("STORE_002"));
        assertTrue(result.contains("STORE_003"));
        assertTrue(result.contains("门店") || result.contains("可用"));
    }

    @Test
    void testQueryInventory_SoldOutDish() {
        String result = inventoryTools.queryInventory("STORE_002", "宫保鸡丁");

        assertNotNull(result);
        assertTrue(result.contains("宫保鸡丁"));
        assertTrue(result.contains("售罄") || result.contains("0"));
    }

    @Test
    void testQueryInventory_LowStockDish() {
        String result = inventoryTools.queryInventory("STORE_002", "红烧肉");

        assertNotNull(result);
        assertTrue(result.contains("红烧肉"));
        assertTrue(result.contains("库存紧张") || result.contains("10"));
    }
}
