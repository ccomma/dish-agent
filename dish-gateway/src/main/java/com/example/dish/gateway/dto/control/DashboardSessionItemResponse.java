package com.example.dish.gateway.dto.control;

import java.time.Instant;

public record DashboardSessionItemResponse(
        String sessionId,
        String lastMemoryType,
        String lastTraceId,
        Instant lastUpdatedAt,
        int eventCount
) {
}
