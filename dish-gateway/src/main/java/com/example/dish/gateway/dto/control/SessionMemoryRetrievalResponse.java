package com.example.dish.gateway.dto.control;

import java.util.List;

public record SessionMemoryRetrievalResponse(
        String sessionId,
        String storeId,
        String source,
        int total,
        List<SessionMemoryRetrievalHitResponse> hits
) {
}
