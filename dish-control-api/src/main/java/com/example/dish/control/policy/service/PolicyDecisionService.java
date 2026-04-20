package com.example.dish.control.policy.service;

import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.model.PolicyEvaluationResult;

public interface PolicyDecisionService {

    PolicyEvaluationResult evaluate(PolicyEvaluationRequest request);
}
