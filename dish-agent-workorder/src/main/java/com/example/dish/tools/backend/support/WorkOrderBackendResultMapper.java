package com.example.dish.tools.backend.support;

import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.StoreListResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单后端结果映射器。
 * 负责校验 HTTP 响应并转换成工具层统一结果对象，避免网关类同时承担映射和网络调用职责。
 */
@Component
public class WorkOrderBackendResultMapper {

    public InventoryResult mapInventory(WorkOrderBackendPayloads.InventoryResponse response) {
        if (response == null) {
            return InventoryResult.failure("库存查询失败：后端返回空响应");
        }
        if (!response.success) {
            return InventoryResult.failure(defaultIfBlank(response.errorMessage, "库存查询失败"));
        }
        String validationError = validateInventorySuccessResponse(response);
        if (validationError != null) {
            return InventoryResult.failure("库存查询失败：后端响应无效（" + validationError + "）");
        }

        List<InventoryResult.InventoryItem> items = new ArrayList<>();
        for (WorkOrderBackendPayloads.InventoryItemDto item : response.items) {
            items.add(new InventoryResult.InventoryItem(item.dishName.trim(), item.quantity));
        }
        return InventoryResult.success(response.storeId, items);
    }

    public StoreListResult mapStoreList(WorkOrderBackendPayloads.StoreListResponse response) {
        if (response == null || Boolean.FALSE.equals(response.success)) {
            return new StoreListResult(List.of());
        }
        String validationError = validateStoreListResponse(response);
        if (validationError != null) {
            return new StoreListResult(List.of());
        }

        List<StoreListResult.StoreInfo> stores = new ArrayList<>();
        for (WorkOrderBackendPayloads.StoreInfoDto store : response.stores) {
            stores.add(new StoreListResult.StoreInfo(store.storeId.trim(), store.name.trim(), normalizeText(store.address)));
        }
        return new StoreListResult(stores);
    }

    public OrderResult mapOrder(WorkOrderBackendPayloads.OrderResponse response) {
        if (response == null) {
            return OrderResult.failure("查询失败：后端返回空响应");
        }
        if (!response.success) {
            return OrderResult.failure(defaultIfBlank(response.errorMessage, "查询失败"));
        }
        String validationError = validateOrderSuccessResponse(response);
        if (validationError != null) {
            return OrderResult.failure("查询失败：后端响应无效（" + validationError + "）");
        }
        return OrderResult.success(
                response.orderId.trim(),
                response.storeId.trim(),
                response.items.trim(),
                response.status.trim(),
                parseTime(response.createTime)
        );
    }

    public RefundResult mapRefund(WorkOrderBackendPayloads.RefundResponse response) {
        if (response == null) {
            return RefundResult.failure("退款申请失败：后端返回空响应");
        }
        if (!response.success) {
            return RefundResult.failure(defaultIfBlank(response.errorMessage, "退款申请失败"));
        }
        String validationError = validateRefundSuccessResponse(response);
        if (validationError != null) {
            return RefundResult.failure("退款申请失败：后端响应无效（" + validationError + "）");
        }
        return RefundResult.success(
                response.ticketId.trim(),
                response.orderId.trim(),
                response.reason.trim(),
                response.status.trim(),
                parseTime(response.createTime)
        );
    }

    public String validateStoreListResponse(WorkOrderBackendPayloads.StoreListResponse response) {
        if (response.stores == null) {
            return "stores 为空";
        }
        for (int i = 0; i < response.stores.size(); i++) {
            WorkOrderBackendPayloads.StoreInfoDto store = response.stores.get(i);
            if (store == null) {
                return "stores[" + i + "] 为空";
            }
            if (isBlank(store.storeId)) {
                return "stores[" + i + "].storeId 为空";
            }
            if (isBlank(store.name)) {
                return "stores[" + i + "].name 为空";
            }
        }
        return null;
    }

    private String validateInventorySuccessResponse(WorkOrderBackendPayloads.InventoryResponse response) {
        if (isBlank(response.storeId)) {
            return "storeId 为空";
        }
        if (response.items == null) {
            return "items 为空";
        }
        for (int i = 0; i < response.items.size(); i++) {
            WorkOrderBackendPayloads.InventoryItemDto item = response.items.get(i);
            if (item == null) {
                return "items[" + i + "] 为空";
            }
            if (isBlank(item.dishName)) {
                return "items[" + i + "].dishName 为空";
            }
            if (item.quantity == null) {
                return "items[" + i + "].quantity 为空";
            }
            if (item.quantity < 0) {
                return "items[" + i + "].quantity 不能小于0";
            }
        }
        return null;
    }

    private String validateOrderSuccessResponse(WorkOrderBackendPayloads.OrderResponse response) {
        if (isBlank(response.orderId)) {
            return "orderId 为空";
        }
        if (isBlank(response.storeId)) {
            return "storeId 为空";
        }
        if (isBlank(response.items)) {
            return "items 为空";
        }
        if (isBlank(response.status)) {
            return "status 为空";
        }
        return null;
    }

    private String validateRefundSuccessResponse(WorkOrderBackendPayloads.RefundResponse response) {
        if (isBlank(response.ticketId)) {
            return "ticketId 为空";
        }
        if (isBlank(response.orderId)) {
            return "orderId 为空";
        }
        if (isBlank(response.reason)) {
            return "reason 为空";
        }
        if (isBlank(response.status)) {
            return "status 为空";
        }
        return null;
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
