package com.example.dish.tools;

import com.example.dish.tools.backend.WorkOrderBackendGateway;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 订单查询工具。
 */
@Component
public class OrderTools {

    @Resource
    private WorkOrderBackendGateway backendGateway;

    public OrderResult queryOrderStatus(String orderId) {
        return backendGateway.queryOrderStatus(orderId);
    }
}
