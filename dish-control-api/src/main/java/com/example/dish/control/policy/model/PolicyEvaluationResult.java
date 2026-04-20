package com.example.dish.control.policy.model;

import com.example.dish.common.runtime.PolicyDecision;

import java.io.Serializable;

public record PolicyEvaluationResult(
        PolicyDecision decision,
        String evaluatorVersion
) implements Serializable {
}
