package com.example.dish.tools.backend.support;

import java.util.List;

/**
 * 工单后端 HTTP 载荷定义。
 * 集中维护后端请求/响应 DTO，避免它们散落在业务网关实现类内部。
 */
public final class WorkOrderBackendPayloads {

    private WorkOrderBackendPayloads() {
    }

    public static class InventoryResponse {
        public boolean success;
        public String errorMessage;
        public String storeId;
        public List<InventoryItemDto> items;
    }

    public static class InventoryItemDto {
        public String dishName;
        public Integer quantity;
    }

    public static class StoreListResponse {
        public Boolean success;
        public String errorMessage;
        public List<StoreInfoDto> stores;
    }

    public static class StoreInfoDto {
        public String storeId;
        public String name;
        public String address;
    }

    public static class OrderResponse {
        public boolean success;
        public String errorMessage;
        public String orderId;
        public String storeId;
        public String items;
        public String status;
        public String createTime;
    }

    public static class RefundResponse {
        public boolean success;
        public String errorMessage;
        public String ticketId;
        public String orderId;
        public String reason;
        public String status;
        public String createTime;
    }

    public static class RefundCreateRequest {
        public String orderId;
        public String reason;

        public RefundCreateRequest(String orderId, String reason) {
            this.orderId = orderId;
            this.reason = reason;
        }
    }
}
