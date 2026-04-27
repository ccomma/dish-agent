package com.example.dish.common.constants;

/**
 * 策略规则标识常量。
 * 统一维护 PolicyDecision.policyId 和策略引擎来源标识，便于控制面稳定展示与排障。
 */
public final class PolicyIds {

    public static final String INVALID_REQUEST = "policy-v1";
    public static final String DEFAULT_ALLOW = "policy-v1-default";
    public static final String TENANT_REQUIRED = "policy-v1-tenant";
    public static final String NODE_APPROVAL = "policy-v1-node-approval";
    public static final String REFUND_APPROVAL = "policy-v1-refund";
    public static final String RULE_ENGINE_SOURCE = "policy-v1-rule-engine";

    private PolicyIds() {
    }
}
