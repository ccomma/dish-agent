package com.example.dish.common.runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 节点执行前的策略判定结果。
 */
public record PolicyDecision(
        PolicyDecisionType decision,
        String reason,
        String policyId,
        String riskLevel,
        Map<String, Object> metadata
) implements Serializable {

    public PolicyDecision {
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }

    public static PolicyDecision allow(String policyId, String reason) {
        return new PolicyDecision(PolicyDecisionType.ALLOW, reason, policyId, "low", Collections.emptyMap());
    }

    public static PolicyDecision deny(String policyId, String reason) {
        return new PolicyDecision(PolicyDecisionType.DENY, reason, policyId, "high", Collections.emptyMap());
    }

    public static PolicyDecision requireApproval(String policyId, String reason, String riskLevel) {
        return new PolicyDecision(PolicyDecisionType.REQUIRE_APPROVAL, reason, policyId, riskLevel, Collections.emptyMap());
    }
}
