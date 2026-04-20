package com.example.dish.gateway.dto.control;

import java.util.List;

public record SessionMemoryTimelineResponse(
        String sessionId,
        String storeId,
        String source,
        int total,
        List<SessionMemoryEntryResponse> entries
) {
}
