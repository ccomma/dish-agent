package com.example.dish.gateway.service.impl;

import com.example.dish.gateway.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session 服务内存实现。
 * 负责维护 session -> storeId 绑定关系，供单机/本地模式使用。
 */
@Component
@ConditionalOnProperty(prefix = "session.store", name = "type", havingValue = "memory", matchIfMissing = true)
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    @Value("${session.store.default-store-id:STORE_001}")
    private String defaultStoreId = "STORE_001";

    @Value("${session.store.ttl-hours:12}")
    private long ttlHours = 12;

    @Value("${session.store.conflict-strategy:keep_existing}")
    private String conflictStrategy = "keep_existing";

    private final Map<String, SessionRecord> sessionStore = new ConcurrentHashMap<>();

    @Override
    public String resolveStoreId(String sessionId, String requestStoreId) {
        // 1. 没有 sessionId 时直接按请求头或默认门店返回。
        if (sessionId == null || sessionId.isEmpty()) {
            String normalized = normalizeStoreId(requestStoreId);
            return normalized != null ? normalized : defaultStoreId;
        }

        // 2. 命中已过期记录时先清理，再继续后续冲突处理。
        long now = System.currentTimeMillis();
        SessionRecord record = sessionStore.get(sessionId);
        if (record != null && record.expireAtMs <= now) {
            sessionStore.remove(sessionId);
            record = null;
        }

        String incomingStoreId = normalizeStoreId(requestStoreId);
        if (incomingStoreId == null && record != null) {
            incomingStoreId = record.storeId;
        }
        if (incomingStoreId == null) {
            incomingStoreId = defaultStoreId;
        }

        // 3. 已有绑定和新请求冲突时，按配置决定保留旧值还是采纳新值。
        if (record != null && !incomingStoreId.equals(record.storeId)) {
            if (isPreferRequestStrategy()) {
                log.info("session store mismatch: sessionId={}, existing={}, incoming={}, apply prefer_request",
                        sessionId, record.storeId, incomingStoreId);
            } else {
                log.warn("session store mismatch: sessionId={}, existing={}, incoming={}, keep existing",
                        sessionId, record.storeId, incomingStoreId);
                incomingStoreId = record.storeId;
            }
        }

        // 4. 回写最新绑定和过期时间。
        sessionStore.put(sessionId, new SessionRecord(incomingStoreId, now + Duration.ofHours(ttlHours).toMillis()));
        return incomingStoreId;
    }

    private String normalizeStoreId(String storeId) {
        if (storeId == null) {
            return null;
        }
        String trimmed = storeId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isPreferRequestStrategy() {
        return "prefer_request".equalsIgnoreCase(conflictStrategy);
    }

    private record SessionRecord(String storeId, long expireAtMs) {
    }
}
