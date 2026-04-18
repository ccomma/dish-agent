package com.example.dish.tools.backend;

import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.StoreListResult;

/**
 * 工单后端网关，隔离工具层与具体数据源实现（mock/http）。
 */
public interface WorkOrderBackendGateway {

    InventoryResult queryAllInventory(String storeId);

    InventoryResult queryInventory(String storeId, String dishName);

    StoreListResult getStoreList();

    OrderResult queryOrderStatus(String orderId);

    RefundResult createRefundTicket(String orderId, String reason);

    RefundResult queryRefundStatus(String ticketId);
}
