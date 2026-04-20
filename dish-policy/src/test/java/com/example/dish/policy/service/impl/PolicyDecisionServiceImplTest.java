package com.example.dish.policy.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.runtime.ExecutionContext;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.PolicyDecisionType;
import com.example.dish.control.policy.model.PolicyEvaluationRequest;
import com.example.dish.control.policy.model.PolicyEvaluationResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PolicyDecisionServiceImplTest {

    @Test
    void shouldDenyWhenRequestInvalid() {
        PolicyDecisionServiceImpl service = new PolicyDecisionServiceImpl();

        PolicyEvaluationResult result = service.evaluate(null);

        Assertions.assertEquals(PolicyDecisionType.DENY, result.decision().decision());
    }

    @Test
    void shouldDenyWorkOrderWhenTenantMissing() {
        PolicyDecisionServiceImpl service = new PolicyDecisionServiceImpl();
        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                node("work-order", false),
                context(IntentType.QUERY_ORDER),
                null,
                "trace-1"
        );

        PolicyEvaluationResult result = service.evaluate(request);

        Assertions.assertEquals(PolicyDecisionType.DENY, result.decision().decision());
    }

    @Test
    void shouldRequireApprovalForRefundWorkflow() {
        PolicyDecisionServiceImpl service = new PolicyDecisionServiceImpl();
        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                node("work-order", false),
                context(IntentType.CREATE_REFUND),
                "STORE-1",
                "trace-2"
        );

        PolicyEvaluationResult result = service.evaluate(request);

        Assertions.assertEquals(PolicyDecisionType.REQUIRE_APPROVAL, result.decision().decision());
    }

    @Test
    void shouldAllowNormalQueryWithTenant() {
        PolicyDecisionServiceImpl service = new PolicyDecisionServiceImpl();
        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                node("work-order", false),
                context(IntentType.QUERY_ORDER),
                "STORE-1",
                "trace-3"
        );

        PolicyEvaluationResult result = service.evaluate(request);

        Assertions.assertEquals(PolicyDecisionType.ALLOW, result.decision().decision());
    }

    private ExecutionNode node(String target, boolean requiresApproval) {
        return new ExecutionNode(
                "n1",
                ExecutionNodeType.AGENT_CALL,
                target,
                5000,
                0,
                requiresApproval,
                Map.of(),
                Map.of()
        );
    }

    private ExecutionContext context(IntentType intent) {
        return new ExecutionContext(
                "exec-1",
                "plan-1",
                AgentContext.builder().sessionId("S-1").intent(intent).build(),
                Map.of(),
                Map.of()
        );
    }
}
