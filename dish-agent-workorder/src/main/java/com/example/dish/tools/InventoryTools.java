package com.example.dish.tools;

import com.example.dish.tools.backend.WorkOrderBackendGateway;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 库存查询工具
 */
@Component
public class InventoryTools {

    @Resource
    private WorkOrderBackendGateway backendGateway;

    public InventoryResult queryAllInventory(String storeId) {
        return backendGateway.queryAllInventory(storeId);
    }

    public InventoryResult queryInventory(String storeId, String dishName) {
        return backendGateway.queryInventory(storeId, dishName);
    }

    public StoreListResult getStoreList() {
        return backendGateway.getStoreList();
    }
}
