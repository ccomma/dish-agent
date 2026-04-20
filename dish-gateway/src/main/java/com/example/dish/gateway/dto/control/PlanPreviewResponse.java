package com.example.dish.gateway.dto.control;

import java.util.List;

public record PlanPreviewResponse(
        String traceId,
        String sessionId,
        String storeId,
        String planId,
        String intent,
        String executionMode,
        double routingConfidence,
        boolean memoryHit,
        List<String> memorySnippets,
        int approvalRequiredCount,
        int blockedStepCount,
        List<StepPolicyPreview> steps
) {
}
