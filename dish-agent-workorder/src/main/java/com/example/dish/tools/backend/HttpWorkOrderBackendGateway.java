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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
            @Value("${backend.connect-timeout-ms:${backend.timeout-ms:2000}}") int connectTimeoutMs,
            @Value("${backend.read-timeout-ms:${backend.timeout-ms:2000}}") int readTimeoutMs) {
        this.baseUrl = trimEndSlash(baseUrl);
        this.restTemplate = buildRestTemplate(connectTimeoutMs, readTimeoutMs);
        log.info("http backend adapter enabled: baseUrl={}, connectTimeoutMs={}, readTimeoutMs={}",
                this.baseUrl, connectTimeoutMs, readTimeoutMs);
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
        if (isBlank(orderId)) {
            return OrderResult.failure("查询失败：订单号不能为空");
        }
        String url = baseUrl + "/api/backend/orders/" + safe(orderId);
        return withHttpFallback(
                () -> mapOrder(call(url, HttpMethod.GET, null, OrderResponse.class)),
                "查询失败：后端服务不可用"
        );
    }

    @Override
    public RefundResult createRefundTicket(String orderId, String reason) {
        if (isBlank(orderId)) {
            return RefundResult.failure("退款申请失败：订单号不能为空");
        }
        String finalReason = isBlank(reason) ? "用户主动申请" : reason.trim();
        String url = baseUrl + "/api/backend/refunds";
        RefundCreateRequest request = new RefundCreateRequest(orderId, finalReason);
        return withHttpFallback(
                () -> mapRefund(call(url, HttpMethod.POST, request, RefundResponse.class)),
                "退款申请失败：后端服务不可用"
        );
    }

    @Override
    public RefundResult queryRefundStatus(String ticketId) {
        if (isBlank(ticketId)) {
            return RefundResult.failure("退款申请失败：工单号不能为空");
        }
        String url = baseUrl + "/api/backend/refunds/" + safe(ticketId);
        return withHttpFallback(
                () -> mapRefund(call(url, HttpMethod.GET, null, RefundResponse.class)),
                "退款申请失败：后端服务不可用"
        );
    }

    private <T> T call(String url, HttpMethod method, Object body, Class<T> responseType) {
        HttpEntity<?> entity = body == null ? HttpEntity.EMPTY : new HttpEntity<>(body);
        ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
        return response.getBody();
    }

    private InventoryResult mapInventory(InventoryResponse response) {
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
        for (InventoryItemDto item : response.items) {
            items.add(new InventoryResult.InventoryItem(item.dishName.trim(), item.quantity));
        }
        return InventoryResult.success(response.storeId, items);
    }

    private StoreListResult mapStoreList(StoreListResponse response) {
        if (response == null) {
            log.warn("store list response is null");
            return new StoreListResult(List.of());
        }
        if (Boolean.FALSE.equals(response.success)) {
            log.warn("store list backend returned failure: {}", response.errorMessage);
            return new StoreListResult(List.of());
        }
        String validationError = validateStoreListResponse(response);
        if (validationError != null) {
            log.warn("store list response invalid: {}", validationError);
            return new StoreListResult(List.of());
        }

        List<StoreListResult.StoreInfo> stores = new ArrayList<>();
        for (StoreInfoDto store : response.stores) {
            stores.add(new StoreListResult.StoreInfo(store.storeId.trim(), store.name.trim(), normalizeText(store.address)));
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
        String validationError = validateOrderSuccessResponse(response);
        if (validationError != null) {
            return OrderResult.failure("查询失败：后端响应无效（" + validationError + "）");
        }
        LocalDateTime createTime = parseTime(response.createTime);
        return OrderResult.success(
                response.orderId.trim(),
                response.storeId.trim(),
                response.items.trim(),
                response.status.trim(),
                createTime
        );
    }

    private RefundResult mapRefund(RefundResponse response) {
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
        LocalDateTime createTime = parseTime(response.createTime);
        return RefundResult.success(
                response.ticketId.trim(),
                response.orderId.trim(),
                response.reason.trim(),
                response.status.trim(),
                createTime
        );
    }

    private String validateInventorySuccessResponse(InventoryResponse response) {
        if (isBlank(response.storeId)) {
            return "storeId 为空";
        }
        if (response.items == null) {
            return "items 为空";
        }
        for (int i = 0; i < response.items.size(); i++) {
            InventoryItemDto item = response.items.get(i);
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

    private String validateStoreListResponse(StoreListResponse response) {
        if (response.stores == null) {
            return "stores 为空";
        }
        for (int i = 0; i < response.stores.size(); i++) {
            StoreInfoDto store = response.stores.get(i);
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

    private String validateOrderSuccessResponse(OrderResponse response) {
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

    private String validateRefundSuccessResponse(RefundResponse response) {
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

    private RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(positiveTimeout(connectTimeoutMs));
        requestFactory.setReadTimeout(positiveTimeout(readTimeoutMs));
        return new RestTemplate(requestFactory);
    }

    private int positiveTimeout(int timeoutMs) {
        if (timeoutMs <= 0) {
            return 2000;
        }
        return timeoutMs;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
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
        public Boolean success;
        public String errorMessage;
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
