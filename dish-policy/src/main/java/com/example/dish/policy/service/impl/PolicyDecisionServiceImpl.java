package com.example.dish.policy.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.runtime.PolicyDecision;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.model.PolicyEvaluationResult;
import com.example.dish.control.policy.service.PolicyDecisionService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService(interfaceClass = PolicyDecisionService.class, timeout = 8000, retries = 0)
public class PolicyDecisionServiceImpl implements PolicyDecisionService {

    @Override
    public PolicyEvaluationResult evaluate(PolicyEvaluationRequest request) {
        if (request == null || request.node() == null || request.context() == null) {
            return new PolicyEvaluationResult(
                    PolicyDecision.deny("policy-v1", "invalid policy request"),
                    "policy-v1-rule-engine"
            );
        }

        if ((request.tenantId() == null || request.tenantId().isBlank()) && "work-order".equals(request.node().target())) {
            return new PolicyEvaluationResult(
                    PolicyDecision.deny("policy-v1-tenant", "work-order call requires tenant scope"),
                    "policy-v1-rule-engine"
            );
        }

        if (request.node().requiresApproval()) {
            return new PolicyEvaluationResult(
                    PolicyDecision.requireApproval("policy-v1-node-approval", "node marked as requiresApproval", "high"),
                    "policy-v1-rule-engine"
            );
        }

        IntentType intent = request.context().agentContext() != null ? request.context().agentContext().getIntent() : null;
        if (intent == IntentType.CREATE_REFUND && "work-order".equals(request.node().target())) {
            return new PolicyEvaluationResult(
                    PolicyDecision.requireApproval("policy-v1-refund", "refund workflow requires manual approval", "high"),
                    "policy-v1-rule-engine"
            );
        }

        return new PolicyEvaluationResult(
                PolicyDecision.allow("policy-v1-default", "rule check passed"),
                "policy-v1-rule-engine"
        );
    }
}
