package com.enterprise.langchain4j.tool;

import java.time.LocalDateTime;

/**
 * 退款工单结果
 */
public class RefundResult {

    private final boolean success;
    private final String errorMessage;
    private final String ticketId;
    private final String orderId;
    private final String reason;
    private final String status;
    private final LocalDateTime createTime;

    private RefundResult(boolean success, String errorMessage, String ticketId, String orderId,
                        String reason, String status, LocalDateTime createTime) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.reason = reason;
        this.status = status;
        this.createTime = createTime;
    }

    /**
     * 创建成功结果
     */
    public static RefundResult success(String ticketId, String orderId, String reason,
                                        String status, LocalDateTime createTime) {
        return new RefundResult(true, null, ticketId, orderId, reason, status, createTime);
    }

    /**
     * 创建失败结果
     */
    public static RefundResult failure(String errorMessage) {
        return new RefundResult(false, errorMessage, null, null, null, null, null);
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public String getTicketId() { return ticketId; }
    public String getOrderId() { return orderId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public LocalDateTime getCreateTime() { return createTime; }
}
