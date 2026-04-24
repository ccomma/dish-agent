package com.example.dish.policy.service.impl;

import com.example.dish.common.telemetry.DubboProviderSpan;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.model.PolicyEvaluationResult;
import com.example.dish.control.policy.service.PolicyDecisionService;
import com.example.dish.policy.support.PolicyRuleEngine;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 策略评估服务门面。
 * 负责接收评估请求，并把规则判断委托给策略规则引擎。
 */
@Service
@DubboService(interfaceClass = PolicyDecisionService.class, timeout = 8000, retries = 0)
public class PolicyDecisionServiceImpl implements PolicyDecisionService {

    private final PolicyRuleEngine policyRuleEngine;

    public PolicyDecisionServiceImpl() {
        this(new PolicyRuleEngine());
    }

    public PolicyDecisionServiceImpl(PolicyRuleEngine policyRuleEngine) {
        this.policyRuleEngine = policyRuleEngine;
    }

    @Override
    @DubboProviderSpan("policy.evaluate")
    public PolicyEvaluationResult evaluate(PolicyEvaluationRequest request) {
        // 1. 把规则判断交给独立规则引擎，门面自身只保留 RPC 适配职责。
        return new PolicyEvaluationResult(policyRuleEngine.evaluate(request), "policy-v1-rule-engine");
    }
}
