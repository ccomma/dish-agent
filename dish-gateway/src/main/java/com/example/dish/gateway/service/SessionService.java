package com.example.dish.gateway.service;

/**
 * Session 服务接口
 *
 * 管理会话与租户店铺映射关系。
 */
public interface SessionService {

    /**
     * 解析会话对应的店铺ID。
     * 优先使用请求中显式传入的店铺ID并写入会话；若未传入则使用会话中已有值。
     */
    String resolveStoreId(String sessionId, String requestStoreId);
}
