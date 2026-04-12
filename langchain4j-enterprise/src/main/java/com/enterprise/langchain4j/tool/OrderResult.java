package com.enterprise.langchain4j.tool;

import java.time.LocalDateTime;

/**
 * 订单查询结果
 */
public class OrderResult {

    private final boolean success;
    private final String errorMessage;
    private final String orderId;
    private final String storeId;
    private final String items;
    private final String status;
    private final LocalDateTime createTime;

    private OrderResult(boolean success, String errorMessage, String orderId, String storeId,
                       String items, String status, LocalDateTime createTime) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.orderId = orderId;
        this.storeId = storeId;
        this.items = items;
        this.status = status;
        this.createTime = createTime;
    }

    /**
     * 创建成功结果
     */
    public static OrderResult success(String orderId, String storeId, String items,
                                       String status, LocalDateTime createTime) {
        return new OrderResult(true, null, orderId, storeId, items, status, createTime);
    }

    /**
     * 创建失败结果
     */
    public static OrderResult failure(String errorMessage) {
        return new OrderResult(false, errorMessage, null, null, null, null, null);
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public String getOrderId() { return orderId; }
    public String getStoreId() { return storeId; }
    public String getItems() { return items; }
    public String getStatus() { return status; }
    public LocalDateTime getCreateTime() { return createTime; }
}
