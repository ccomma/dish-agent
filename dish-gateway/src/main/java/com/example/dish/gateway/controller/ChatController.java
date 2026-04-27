package com.example.dish.gateway.controller;

import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.ChatExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.UUID;

import static com.example.dish.gateway.config.TraceIdFilter.TRACE_HEADER;

/**
 * 聊天主链路控制器。
 * 负责接收用户 HTTP 请求，并串起 routing、planner、policy、agent dispatch、memory summary 和 execution runtime 事件写入。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final String STORE_HEADER = "X-Store-Id";

    @Resource
    private ChatExecutionService chatExecutionService;

    @PostMapping("/process")
    public GatewayResponse process(@RequestBody ChatRequest request, HttpServletRequest httpServletRequest) {
        // 1. HTTP 层只负责提取请求头和兜底 traceId，主执行链路交给服务层。
        String requestStoreId = httpServletRequest.getHeader(STORE_HEADER);
        String traceId = httpServletRequest.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = "TRC-GW-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return chatExecutionService.process(request.getMessage(), request.getSessionId(), requestStoreId, traceId);
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "dish-gateway");
    }
    /**
     * 聊天请求体。
     */
    public static class ChatRequest {
        private String message;
        private String sessionId;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    /**
     * 健康检查响应体。
     */
    public static class HealthResponse {
        private final String status;
        private final String service;

        public HealthResponse(String status, String service) {
            this.status = status;
            this.service = service;
        }

        public String getStatus() { return status; }
        public String getService() { return service; }
    }
}
