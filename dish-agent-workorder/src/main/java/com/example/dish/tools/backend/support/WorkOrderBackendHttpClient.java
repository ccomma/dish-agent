package com.example.dish.tools.backend.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 工单后端 HTTP 客户端。
 * 负责封装 baseUrl、超时和 GET/POST 调用细节，避免业务网关类直接操作 RestTemplate。
 */
@Component
@ConditionalOnProperty(prefix = "backend", name = "mode", havingValue = "http")
public class WorkOrderBackendHttpClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WorkOrderBackendHttpClient(
            @Value("${backend.base-url}") String baseUrl,
            @Value("${backend.connect-timeout-ms:${backend.timeout-ms:2000}}") int connectTimeoutMs,
            @Value("${backend.read-timeout-ms:${backend.timeout-ms:2000}}") int readTimeoutMs) {
        this.baseUrl = trimEndSlash(baseUrl);
        this.restTemplate = buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String encode(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }

    public <T> T get(String path, Class<T> responseType) {
        return exchange(path, HttpMethod.GET, null, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return exchange(path, HttpMethod.POST, body, responseType);
    }

    private <T> T exchange(String path, HttpMethod method, Object body, Class<T> responseType) {
        HttpEntity<?> entity = body == null ? HttpEntity.EMPTY : new HttpEntity<>(body);
        ResponseEntity<T> response = restTemplate.exchange(baseUrl + path, method, entity, responseType);
        return response.getBody();
    }

    private RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(positiveTimeout(connectTimeoutMs));
        requestFactory.setReadTimeout(positiveTimeout(readTimeoutMs));
        return new RestTemplate(requestFactory);
    }

    private int positiveTimeout(int timeoutMs) {
        return timeoutMs > 0 ? timeoutMs : 2000;
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
}
