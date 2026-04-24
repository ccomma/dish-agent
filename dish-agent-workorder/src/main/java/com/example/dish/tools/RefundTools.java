package com.example.dish.tools;

import com.example.dish.tools.backend.WorkOrderBackendGateway;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 退款工单工具。
 */
@Component
public class RefundTools {

    @Resource
    private WorkOrderBackendGateway backendGateway;

    public RefundResult createRefundTicket(String orderId, String reason) {
        return backendGateway.createRefundTicket(orderId, reason);
    }

    public RefundResult queryRefundStatus(String ticketId) {
        return backendGateway.queryRefundStatus(ticketId);
    }
}
