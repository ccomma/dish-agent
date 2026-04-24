package com.example.dish.policy.support;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.runtime.PolicyDecision;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import org.springframework.stereotype.Component;

/**
 * 策略规则引擎。
 * 负责承接 policy 模块里的规则判断，让 Dubbo 门面只保留请求接收和结果包装职责。
 */
@Component
public class PolicyRuleEngine {

    public PolicyDecision evaluate(PolicyEvaluationRequest request) {
        // 1. 缺少最基本的请求边界时直接拒绝，避免无上下文放行。
        if (request == null || request.node() == null || request.context() == null) {
            return PolicyDecision.deny("policy-v1", "invalid policy request");
        }

        // 2. 先检查需要立即拒绝的前置约束。
        PolicyDecision denyDecision = denyDecision(request);
        if (denyDecision != null) {
            return denyDecision;
        }

        // 3. 再判断是否需要人工审批。
        PolicyDecision approvalDecision = approvalDecision(request);
        if (approvalDecision != null) {
            return approvalDecision;
        }

        // 4. 其余场景默认放行。
        return PolicyDecision.allow("policy-v1-default", "rule check passed");
    }

    private PolicyDecision denyDecision(PolicyEvaluationRequest request) {
        if (!hasTenantScope(request) && "work-order".equals(request.node().target())) {
            return PolicyDecision.deny("policy-v1-tenant", "work-order call requires tenant scope");
        }
        return null;
    }

    private PolicyDecision approvalDecision(PolicyEvaluationRequest request) {
        if (request.node().requiresApproval()) {
            return PolicyDecision.requireApproval(
                    "policy-v1-node-approval",
                    "node marked as requiresApproval",
                    "high"
            );
        }

        IntentType intent = request.context().agentContext() != null ? request.context().agentContext().getIntent() : null;
        if (intent == IntentType.CREATE_REFUND && "work-order".equals(request.node().target())) {
            return PolicyDecision.requireApproval("policy-v1-refund", "refund workflow requires manual approval", "high");
        }
        return null;
    }

    private boolean hasTenantScope(PolicyEvaluationRequest request) {
        return request.tenantId() != null && !request.tenantId().isBlank();
    }
}
