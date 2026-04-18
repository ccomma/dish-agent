package com.example.dish.gateway.controller;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.AgentDispatchService;
import com.example.dish.gateway.service.ResponseAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * 聊天控制器 - HTTP 入口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String STORE_HEADER = "X-Store-Id";

    @Resource
    private RoutingAgent routingAgent;
    @Resource
    private AgentDispatchService agentDispatchService;
    @Resource
    private ResponseAggregator responseAggregator;

    /**
     * 处理聊天请求
     *
     * POST /api/chat/process
     * Body: {"message": "宫保鸡丁是什么菜系？", "sessionId": "可选"}
     */
    @PostMapping("/process")
    public GatewayResponse process(@RequestBody ChatRequest request, HttpServletRequest httpServletRequest) {
        String userInput = request.getMessage();
        if (userInput == null || userInput.trim().isEmpty()) {
            GatewayResponse response = new GatewayResponse();
            response.setSuccess(false);
            response.setContent("消息不能为空");
            return response;
        }

        String sessionId = request.getSessionId() != null && !request.getSessionId().isEmpty()
                ? request.getSessionId()
                : generateSessionId();
        String requestStoreId = httpServletRequest.getHeader(STORE_HEADER);

        // 1. 路由决策
        RoutingDecision routing = routingAgent.route(userInput, sessionId, requestStoreId, null);
        log.info("gateway dispatch: sessionId={}, targetAgent={}, intent={}",
                routing.context().getSessionId(), routing.targetAgent(), routing.intent());

        // 2. 调用对应Agent
        AgentResponse agentResponse = agentDispatchService.dispatch(routing);

        // 3. 聚合结果
        return responseAggregator.aggregate(agentResponse, routing);
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "dish-gateway");
    }


    private String generateSessionId() {
        return "SESSION_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 请求体
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
     * 健康响应
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
