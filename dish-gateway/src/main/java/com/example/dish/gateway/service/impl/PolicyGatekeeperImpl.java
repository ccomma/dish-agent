package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionContext;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.PolicyDecision;
import com.example.dish.common.runtime.PolicyDecisionType;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.service.PolicyDecisionService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 策略门禁实现。
 * 通过 Dubbo 调用 policy 服务评估每个 step 的执行许可。
 */
@Component
class PolicyGatekeeperImpl implements PolicyGatekeeper {

    private static final Logger log = LoggerFactory.getLogger(PolicyGatekeeperImpl.class);

    @DubboReference(timeout = 8000, retries = 0, check = false)
    private PolicyDecisionService policyDecisionService;

    @Override
    public List<AgentExecutionStep> filterAllowedSteps(List<AgentExecutionStep> steps,
                                                        RoutingDecision routing,
                                                        String traceId) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        List<AgentExecutionStep> allowed = new ArrayList<>();
        for (AgentExecutionStep step : steps) {
            PolicyDecision decision = evaluate(step, routing, traceId);
            if (decision != null && decision.decision() == PolicyDecisionType.ALLOW) {
                allowed.add(step);
            } else {
                log.warn("step blocked by policy: stepId={}, targetAgent={}, decision={}",
                        step.stepId(), step.targetAgent(), decision != null ? decision.decision() : null);
            }
        }
        return allowed;
    }

    @Override
    public AgentExecutionStep findFirstApprovalRequiredStep(List<AgentExecutionStep> steps,
                                                              RoutingDecision routing,
                                                              String traceId) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }

        for (AgentExecutionStep step : steps) {
            PolicyDecision decision = evaluate(step, routing, traceId);
            if (decision != null && decision.decision() == PolicyDecisionType.REQUIRE_APPROVAL) {
                return step;
            }
        }
        return null;
    }

    @Override
    public PolicyDecision evaluate(AgentExecutionStep step,
                                    RoutingDecision routing,
                                    String traceId) {
        ExecutionNode node = new ExecutionNode(
                step.stepId(),
                ExecutionNodeType.AGENT_CALL,
                step.targetAgent(),
                step.timeoutMs(),
                0,
                false,
                Map.of(),
                step.metadata()
        );
        ExecutionContext context = new ExecutionContext(
                traceId,
                routing != null ? routing.planId() : null,
                routing != null ? routing.context() : null,
                Map.of(),
                Map.of("executionMode", routing != null ? routing.executionMode() : null)
        );
        PolicyEvaluationRequest policyRequest = new PolicyEvaluationRequest(
                node,
                context,
                routing != null && routing.context() != null ? routing.context().getStoreId() : null,
                traceId
        );

        var result = policyDecisionService.evaluate(policyRequest);
        return result != null ? result.decision() : null;
    }
}
