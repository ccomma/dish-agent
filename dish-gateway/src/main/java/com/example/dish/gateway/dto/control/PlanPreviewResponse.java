package com.example.dish.gateway.dto.control;

import com.example.dish.control.execution.model.ExecutionEdgeView;
import com.example.dish.control.execution.model.ExecutionNodeView;

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
        String memorySource,
        List<String> memorySnippets,
        List<SessionMemoryRetrievalHitResponse> memoryHits,
        int approvalRequiredCount,
        int blockedStepCount,
        List<ExecutionNodeView> graphNodes,
        List<ExecutionEdgeView> graphEdges,
        List<StepPolicyPreview> steps
) {
}
