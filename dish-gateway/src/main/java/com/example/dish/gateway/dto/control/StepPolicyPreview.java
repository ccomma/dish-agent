package com.example.dish.gateway.dto.control;

import java.util.List;

public record StepPolicyPreview(
        String stepId,
        String targetAgent,
        String nodeType,
        List<String> dependsOn,
        String policyDecision,
        String riskLevel,
        boolean approvalRequired,
        boolean executable,
        String reason
) {
}
