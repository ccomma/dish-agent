package com.example.dish.tools.backend;

import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.StoreListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HTTP 后端实现，接入真实业务系统。
 */
@Component
@ConditionalOnProperty(prefix = "backend", name = "mode", havingValue = "http")
public class HttpWorkOrderBackendGateway implements WorkOrderBackendGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkOrderBackendGateway.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public HttpWorkOrderBackendGateway(
            @Value("${backend.base-url}") String baseUrl,
            @Value("${backend.timeout-ms:2000}") int timeoutMs) {
        this.baseUrl = trimEndSlash(baseUrl);
        this.restTemplate = new RestTemplate();
        // RestTemplate timeout is intentionally kept default here to avoid adding extra factory deps.
        log.info("http backend adapter enabled: baseUrl={}, timeoutMs={}", this.baseUrl, timeoutMs);
    }

    @Override
    public InventoryResult queryAllInventory(String storeId) {
        String url = baseUrl + "/api/backend/inventory/all?storeId=" + safe(storeId);
        return withHttpFallback(
                () -> mapInventory(call(url, HttpMethod.GET, null, InventoryResponse.class)),
                "库存查询失败：后端服务不可用"
        );
    }

    @Override
    public InventoryResult queryInventory(String storeId, String dishName) {
        String url = baseUrl + "/api/backend/inventory?storeId=" + safe(storeId) + "&dishName=" + safe(dishName);
        return withHttpFallback(
                () -> mapInventory(call(url, HttpMethod.GET, null, InventoryResponse.class)),
                "库存查询失败：后端服务不可用"
        );
    }

    @Override
    public StoreListResult getStoreList() {
        String url = baseUrl + "/api/backend/stores";
        return withHttpFallback(
                () -> mapStoreList(call(url, HttpMethod.GET, null, StoreListResponse.class)),
                new StoreListResult(List.of())
        );
    }

    @Override
    public OrderResult queryOrderStatus(String orderId) {
        String url = baseUrl + "/api/backend/orders/" + safe(orderId);
        return withHttpFallback(
                () -> mapOrder(call(url, HttpMethod.GET, null, OrderResponse.class)),
                "查询失败：后端服务不可用"
        );
    }

    @Override
    public RefundResult createRefundTicket(String orderId, String reason) {
        String url = baseUrl + "/api/backend/refunds";
        RefundCreateRequest request = new RefundCreateRequest(orderId, reason);
        return withHttpFallback(
                () -> mapRefund(call(url, HttpMethod.POST, request, RefundResponse.class)),
                "退款申请失败：后端服务不可用"
        );
    }

    @Override
    public RefundResult queryRefundStatus(String ticketId) {
        String url = baseUrl + "/api/backend/refunds/" + safe(ticketId);
        return withHttpFallback(
                () -> mapRefund(call(url, HttpMethod.GET, null, RefundResponse.class)),
                "退款申请失败：后端服务不可用"
        );
    }

    private <T> T call(String url, HttpMethod method, Object body, Class<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(url, method, new HttpEntity<>(body), responseType);
        return response.getBody();
    }

    private InventoryResult mapInventory(InventoryResponse response) {
        if (response == null) {
            return InventoryResult.failure("库存查询失败：后端返回空响应");
        }
        if (!response.success) {
            return InventoryResult.failure(defaultIfBlank(response.errorMessage, "库存查询失败"));
        }
        List<InventoryResult.InventoryItem> items = new ArrayList<>();
        if (response.items != null) {
            for (InventoryItemDto item : response.items) {
                if (item != null && item.dishName != null && item.quantity != null) {
                    items.add(new InventoryResult.InventoryItem(item.dishName, item.quantity));
                }
            }
        }
        return InventoryResult.success(response.storeId, items);
    }

    private StoreListResult mapStoreList(StoreListResponse response) {
        if (response == null || response.stores == null) {
            return new StoreListResult(List.of());
        }
        List<StoreListResult.StoreInfo> stores = new ArrayList<>();
        for (StoreInfoDto store : response.stores) {
            if (store != null) {
                stores.add(new StoreListResult.StoreInfo(store.storeId, store.name, store.address));
            }
        }
        return new StoreListResult(stores);
    }

    private OrderResult mapOrder(OrderResponse response) {
        if (response == null) {
            return OrderResult.failure("查询失败：后端返回空响应");
        }
        if (!response.success) {
            return OrderResult.failure(defaultIfBlank(response.errorMessage, "查询失败"));
        }
        LocalDateTime createTime = parseTime(response.createTime);
        return OrderResult.success(response.orderId, response.storeId, response.items, response.status, createTime);
    }

    private RefundResult mapRefund(RefundResponse response) {
        if (response == null) {
            return RefundResult.failure("退款申请失败：后端返回空响应");
        }
        if (!response.success) {
            return RefundResult.failure(defaultIfBlank(response.errorMessage, "退款申请失败"));
        }
        LocalDateTime createTime = parseTime(response.createTime);
        return RefundResult.success(response.ticketId, response.orderId, response.reason, response.status, createTime);
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String trimEndSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String safe(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }

    private InventoryResult withHttpFallback(InventorySupplier supplier, String errorMessage) {
        try {
            return supplier.get();
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return InventoryResult.failure(errorMessage);
        }
    }

    private StoreListResult withHttpFallback(StoreListSupplier supplier, StoreListResult fallback) {
        try {
            return supplier.get();
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return fallback;
        }
    }

    private OrderResult withHttpFallback(OrderSupplier supplier, String errorMessage) {
        try {
            return supplier.get();
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return OrderResult.failure(errorMessage);
        }
    }

    private RefundResult withHttpFallback(RefundSupplier supplier, String errorMessage) {
        try {
            return supplier.get();
        } catch (RestClientException ex) {
            log.error("http backend call failed", ex);
            return RefundResult.failure(errorMessage);
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

    private static class InventoryResponse {
        public boolean success;
        public String errorMessage;
        public String storeId;
        public List<InventoryItemDto> items;
    }

    private static class InventoryItemDto {
        public String dishName;
        public Integer quantity;
    }

    private static class StoreListResponse {
        public List<StoreInfoDto> stores;
    }

    private static class StoreInfoDto {
        public String storeId;
        public String name;
        public String address;
    }

    private static class OrderResponse {
        public boolean success;
        public String errorMessage;
        public String orderId;
        public String storeId;
        public String items;
        public String status;
        public String createTime;
    }

    private static class RefundResponse {
        public boolean success;
        public String errorMessage;
        public String ticketId;
        public String orderId;
        public String reason;
        public String status;
        public String createTime;
    }

    private static class RefundCreateRequest {
        public String orderId;
        public String reason;

        public RefundCreateRequest(String orderId, String reason) {
            this.orderId = orderId;
            this.reason = reason;
        }
    }
}
