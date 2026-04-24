package com.example.dish.tools.backend;

import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.StoreListResult;
import com.example.dish.tools.backend.support.WorkOrderBackendHttpClient;
import com.example.dish.tools.backend.support.WorkOrderBackendPayloads;
import com.example.dish.tools.backend.support.WorkOrderBackendResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * HTTP 后端实现，接入真实业务系统。
 * 这里只保留参数校验、调用编排和失败降级；HTTP 调用与响应映射分别下沉到独立支撑类。
 */
@Component
@ConditionalOnProperty(prefix = "backend", name = "mode", havingValue = "http")
public class HttpWorkOrderBackendGateway implements WorkOrderBackendGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkOrderBackendGateway.class);

    private final WorkOrderBackendHttpClient httpClient;
    private final WorkOrderBackendResultMapper resultMapper;

    public HttpWorkOrderBackendGateway(WorkOrderBackendHttpClient httpClient,
                                       WorkOrderBackendResultMapper resultMapper) {
        this.httpClient = httpClient;
        this.resultMapper = resultMapper;
        log.info("http backend adapter enabled: baseUrl={}", this.httpClient.baseUrl());
    }

    @Override
    public InventoryResult queryAllInventory(String storeId) {
        return withHttpFallback(
                () -> resultMapper.mapInventory(httpClient.get(
                        "/api/backend/inventory/all?storeId=" + httpClient.encode(storeId),
                        WorkOrderBackendPayloads.InventoryResponse.class
                )),
                "库存查询失败：后端服务不可用"
        );
    }

    @Override
    public InventoryResult queryInventory(String storeId, String dishName) {
        return withHttpFallback(
                () -> resultMapper.mapInventory(httpClient.get(
                        "/api/backend/inventory?storeId=" + httpClient.encode(storeId)
                                + "&dishName=" + httpClient.encode(dishName),
                        WorkOrderBackendPayloads.InventoryResponse.class
                )),
                "库存查询失败：后端服务不可用"
        );
    }

    @Override
    public StoreListResult getStoreList() {
        return withHttpFallback(
                () -> resultMapper.mapStoreList(httpClient.get(
                        "/api/backend/stores",
                        WorkOrderBackendPayloads.StoreListResponse.class
                )),
                new StoreListResult(List.of())
        );
    }

    @Override
    public OrderResult queryOrderStatus(String orderId) {
        if (isBlank(orderId)) {
            return OrderResult.failure("查询失败：订单号不能为空");
        }
        return withHttpFallback(
                () -> resultMapper.mapOrder(httpClient.get(
                        "/api/backend/orders/" + httpClient.encode(orderId),
                        WorkOrderBackendPayloads.OrderResponse.class
                )),
                "查询失败：后端服务不可用"
        );
    }

    @Override
    public RefundResult createRefundTicket(String orderId, String reason) {
        if (isBlank(orderId)) {
            return RefundResult.failure("退款申请失败：订单号不能为空");
        }
        String finalReason = isBlank(reason) ? "用户主动申请" : reason.trim();
        WorkOrderBackendPayloads.RefundCreateRequest request =
                new WorkOrderBackendPayloads.RefundCreateRequest(orderId, finalReason);
        return withHttpFallback(
                () -> resultMapper.mapRefund(httpClient.post(
                        "/api/backend/refunds",
                        request,
                        WorkOrderBackendPayloads.RefundResponse.class
                )),
                "退款申请失败：后端服务不可用"
        );
    }

    @Override
    public RefundResult queryRefundStatus(String ticketId) {
        if (isBlank(ticketId)) {
            return RefundResult.failure("退款申请失败：工单号不能为空");
        }
        return withHttpFallback(
                () -> resultMapper.mapRefund(httpClient.get(
                        "/api/backend/refunds/" + httpClient.encode(ticketId),
                        WorkOrderBackendPayloads.RefundResponse.class
                )),
                "退款申请失败：后端服务不可用"
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private InventoryResult withHttpFallback(InventorySupplier supplier, String errorMessage) {
        try {
            return supplier.get();
        } catch (RestClientResponseException ex) {
            log.error("http backend call failed with status={}, body={}",
                    ex.getRawStatusCode(), ex.getResponseBodyAsString(), ex);
            return InventoryResult.failure(errorMessage + " (HTTP " + ex.getRawStatusCode() + ")");
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return InventoryResult.failure(errorMessage + " (网络异常)");
        } catch (Exception ex) {
            log.error("http backend mapping failed", ex);
            return InventoryResult.failure(errorMessage + " (响应解析异常)");
        }
    }

    private StoreListResult withHttpFallback(StoreListSupplier supplier, StoreListResult fallback) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            log.error("http backend call failed", ex);
            return fallback;
        }
    }

    private OrderResult withHttpFallback(OrderSupplier supplier, String errorMessage) {
        try {
            return supplier.get();
        } catch (RestClientResponseException ex) {
            log.error("http backend call failed with status={}, body={}",
                    ex.getRawStatusCode(), ex.getResponseBodyAsString(), ex);
            return OrderResult.failure(errorMessage + " (HTTP " + ex.getRawStatusCode() + ")");
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return OrderResult.failure(errorMessage + " (网络异常)");
        } catch (Exception ex) {
            log.error("http backend mapping failed", ex);
            return OrderResult.failure(errorMessage + " (响应解析异常)");
        }
    }

    private RefundResult withHttpFallback(RefundSupplier supplier, String errorMessage) {
        try {
            return supplier.get();
        } catch (RestClientResponseException ex) {
            log.error("http backend call failed with status={}, body={}",
                    ex.getRawStatusCode(), ex.getResponseBodyAsString(), ex);
            return RefundResult.failure(errorMessage + " (HTTP " + ex.getRawStatusCode() + ")");
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return RefundResult.failure(errorMessage + " (网络异常)");
        } catch (Exception ex) {
            log.error("http backend mapping failed", ex);
            return RefundResult.failure(errorMessage + " (响应解析异常)");
        }
    }

    @FunctionalInterface
    private interface InventorySupplier {
        InventoryResult get();
    }

    @FunctionalInterface
    private interface StoreListSupplier {
        StoreListResult get();
    }

    @FunctionalInterface
    private interface OrderSupplier {
        OrderResult get();
    }

    @FunctionalInterface
    private interface RefundSupplier {
        RefundResult get();
    }
}
