package com.example.dish.gateway.service.impl;

import com.example.dish.gateway.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;

/**
 * Redis 会话实现，用于生产多实例部署。
 */
@Component
@ConditionalOnProperty(prefix = "session.store", name = "type", havingValue = "redis")
public class RedisSessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionServiceImpl.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String STORE_ID_FIELD = "storeId";

    @Resource
    private StringRedisTemplate redisTemplate;

    @Value("${session.store.default-store-id:STORE_001}")
    private String defaultStoreId;

    @Value("${session.store.ttl-hours:12}")
    private long ttlHours;

    @Override
    public String resolveStoreId(String sessionId, String requestStoreId) {
        String incomingStoreId = normalizeStoreId(requestStoreId);
        if (sessionId == null || sessionId.isBlank()) {
            return incomingStoreId != null ? incomingStoreId : defaultStoreId;
        }

        String redisKey = SESSION_KEY_PREFIX + sessionId;
        try {
            String existingStoreId = redisTemplate.opsForHash().get(redisKey, STORE_ID_FIELD) instanceof String value
                    ? value : null;
            if (existingStoreId != null && incomingStoreId != null && !existingStoreId.equals(incomingStoreId)) {
                log.warn("redis session store mismatch: sessionId={}, existing={}, incoming={}, keep existing",
                        sessionId, existingStoreId, incomingStoreId);
                incomingStoreId = existingStoreId;
            } else if (incomingStoreId == null) {
                incomingStoreId = existingStoreId;
            }
            if (incomingStoreId == null) {
                incomingStoreId = defaultStoreId;
            }

            redisTemplate.opsForHash().put(redisKey, STORE_ID_FIELD, incomingStoreId);
            redisTemplate.expire(redisKey, Duration.ofHours(ttlHours));
            return incomingStoreId;
        } catch (Exception ex) {
            log.error("redis session resolve failed, fallback to default store: sessionId={}", sessionId, ex);
            return incomingStoreId != null ? incomingStoreId : defaultStoreId;
        }
    }

    private String normalizeStoreId(String storeId) {
        if (storeId == null) {
            return null;
        }
        String trimmed = storeId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
