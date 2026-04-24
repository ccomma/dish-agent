package com.example.dish.control.policy.service;

import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.model.PolicyEvaluationResult;

/**
 * 策略评估服务契约。
 */
public interface PolicyDecisionService {

    PolicyEvaluationResult evaluate(PolicyEvaluationRequest request);
}
