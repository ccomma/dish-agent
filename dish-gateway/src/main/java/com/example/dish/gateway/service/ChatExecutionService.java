package com.example.dish.gateway.service;

import com.example.dish.gateway.dto.GatewayResponse;

/**
 * 聊天执行服务。
 * 负责承接 HTTP 层归一化后的聊天请求，并完成路由、编排、执行、聚合与运行态写入。
 */
public interface ChatExecutionService {

    GatewayResponse process(String userInput, String requestedSessionId, String requestStoreId, String traceId);
}
