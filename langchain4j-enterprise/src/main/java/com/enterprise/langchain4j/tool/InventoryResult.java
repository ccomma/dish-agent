package com.enterprise.langchain4j.tool;

import java.util.List;
import java.util.Map;

/**
 * 库存查询结果
 */
public class InventoryResult {

    private final boolean success;
    private final String errorMessage;
    private final String storeId;
    private final List<InventoryItem> items;

    private InventoryResult(boolean success, String errorMessage, String storeId, List<InventoryItem> items) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.storeId = storeId;
        this.items = items;
    }

    /**
     * 创建成功结果
     */
    public static InventoryResult success(String storeId, List<InventoryItem> items) {
        return new InventoryResult(true, null, storeId, items);
    }

    /**
     * 创建失败结果
     */
    public static InventoryResult failure(String errorMessage) {
        return new InventoryResult(false, errorMessage, null, null);
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public String getStoreId() { return storeId; }
    public List<InventoryItem> getItems() { return items; }

    /**
     * 单个库存项
     */
    public static class InventoryItem {
        private final String dishName;
        private final int quantity;
        private final String status;

        public InventoryItem(String dishName, int quantity) {
            this.dishName = dishName;
            this.quantity = quantity;
            this.status = computeStatus(quantity);
        }

        private static String computeStatus(int quantity) {
            if (quantity == 0) return "售罄";
            if (quantity < 10) return "库存紧张";
            return "有货";
        }

        public String getDishName() { return dishName; }
        public int getQuantity() { return quantity; }
        public String getStatus() { return status; }
    }
}
