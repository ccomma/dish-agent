package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionEdge;
import com.example.dish.common.runtime.ExecutionEdgeCondition;
import com.example.dish.common.runtime.ExecutionNode;
import com.example.dish.common.runtime.ExecutionNodeType;
import com.example.dish.common.runtime.ExecutionPlan;
import com.example.dish.common.runtime.PolicyDecision;
import com.example.dish.common.runtime.PolicyDecisionType;
import com.example.dish.control.approval.model.ApprovalTicketCommandResult;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryResult;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryRetrievalHit;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.memory.service.MemoryWriteService;
import com.example.dish.control.planner.model.PlanningResult;
import com.example.dish.control.planner.service.ExecutionPlannerService;
import com.example.dish.control.policy.model.PolicyEvaluationResult;
import com.example.dish.control.policy.service.PolicyDecisionService;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;
import com.example.dish.gateway.service.StepEvaluation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class OrchestrationControlServiceImplTest {

    @Test
    void shouldPreferPlannerGraphOverRoutingSteps() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        inject(service, "executionPlannerService", (ExecutionPlannerService) request -> new PlanningResult(
                true,
                "planner-v1",
                "ok",
                ExecutionPlan.builder()
                        .planId("plan-from-planner")
                        .intent("DISH_QUESTION")
                        .nodes(List.of(
                                new ExecutionNode("n1", ExecutionNodeType.AGENT_CALL, "dish-knowledge", 7000, 0, false, Map.of(), Map.of()),
                                new ExecutionNode("n2", ExecutionNodeType.AGENT_CALL, "chat", 4000, 0, false, Map.of(), Map.of())
                        ))
                        .edges(List.of(
                                new ExecutionEdge("e1", "n1", "n2", ExecutionEdgeCondition.ON_SUCCESS, Map.of())
                        ))
                        .build()
        ));

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.DISH_QUESTION)
                .targetAgent(RoutingDecision.TARGET_DISH_KNOWLEDGE)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-10").intent(IntentType.DISH_QUESTION).build())
                .executionSteps(List.of(
                        AgentExecutionStep.builder().stepId("legacy-step").targetAgent("chat").nodeType("AGENT_CALL").build()
                ))
                .build();

        List<AgentExecutionStep> steps = service.planSteps(routing, "trace-10");

        Assertions.assertEquals(2, steps.size());
        Assertions.assertEquals("n1", steps.get(0).stepId());
        Assertions.assertEquals("dish-knowledge", steps.get(0).targetAgent());
        Assertions.assertEquals("n2", steps.get(1).stepId());
        Assertions.assertEquals(List.of("n1"), steps.get(1).dependsOn());
    }

    @Test
    void shouldFallbackToRoutingStepsWhenPlannerReturnsEmptyPlan() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        inject(service, "executionPlannerService", (ExecutionPlannerService) request -> new PlanningResult(
                true,
                "planner-v1",
                "empty",
                ExecutionPlan.builder().planId("plan-empty").intent("GENERAL_CHAT").build()
        ));

        AgentExecutionStep legacyStep = AgentExecutionStep.builder()
                .stepId("legacy-step")
                .targetAgent("chat")
                .nodeType("AGENT_CALL")
                .build();

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.GENERAL_CHAT)
                .targetAgent(RoutingDecision.TARGET_CHAT)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-11").intent(IntentType.GENERAL_CHAT).build())
                .executionSteps(List.of(legacyStep))
                .build();

        List<AgentExecutionStep> steps = service.planSteps(routing, "trace-11");

        Assertions.assertEquals(1, steps.size());
        Assertions.assertEquals("legacy-step", steps.get(0).stepId());
        Assertions.assertEquals("chat", steps.get(0).targetAgent());
    }

    @Test
    void shouldDetectApprovalRequiredDecision() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        PolicyGatekeeperImpl gatekeeper = new PolicyGatekeeperImpl();
        inject(gatekeeper, "policyDecisionService", (PolicyDecisionService) request -> new PolicyEvaluationResult(
                PolicyDecision.requireApproval("p1", "needs approval", "high"),
                "policy-v1"
        ));
        inject(service, "policyGatekeeper", gatekeeper);

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.CREATE_REFUND)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-12").intent(IntentType.CREATE_REFUND).storeId("STORE-1").build())
                .build();

        AgentExecutionStep step = AgentExecutionStep.builder().stepId("s1").targetAgent("work-order").nodeType("AGENT_CALL").build();

        StepEvaluation eval = service.evaluateStep(step, routing, "trace-12");

        Assertions.assertEquals(StepEvaluation.REQUIRES_APPROVAL, eval);
    }

    @Test
    void shouldBuildApprovalPendingResponse() throws Exception {
        OrchestrationControlServiceImpl service = newService();

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.CREATE_REFUND)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-13").intent(IntentType.CREATE_REFUND).build())
                .build();

        GatewayResponse response = service.buildApprovalPendingResponse(routing, "trace-13", "APR-12345678");

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("Gateway-Policy", response.getAgentName());
        Assertions.assertTrue(response.getContent().contains("人工审批"));
        Assertions.assertEquals("S-13", response.getSessionId());
        Assertions.assertTrue(response.getFollowUpHints().get(0).startsWith("approvalId=APR-"));
    }

    @Test
    void shouldCreateApprovalTicketAndWriteMemory() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        AtomicBoolean created = new AtomicBoolean(false);
        inject(service, "approvalTicketService", new ApprovalTicketService() {
            @Override
            public ApprovalTicketCommandResult create(ApprovalTicketCreateRequest request) {
                created.set(true);
                return new ApprovalTicketCommandResult(true, null, "ok");
            }

            @Override
            public ApprovalTicketCommandResult decide(ApprovalTicketDecisionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApprovalTicketQueryResult get(ApprovalTicketQueryRequest request) {
                throw new UnsupportedOperationException();
            }
        });

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.CREATE_REFUND)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-15").intent(IntentType.CREATE_REFUND).storeId("STORE-15").build())
                .build();

        AgentExecutionStep step = AgentExecutionStep.builder()
                .stepId("s-approval")
                .targetAgent("work-order")
                .nodeType("AGENT_CALL")
                .build();

        String approvalId = service.createApprovalTicket(routing, "exec-15", step, "trace-15");

        Assertions.assertTrue(approvalId.startsWith("APR-"));
        Assertions.assertTrue(created.get());
    }

    @Test
    void shouldReturnNullWhenNoApprovalRequired() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        PolicyGatekeeperImpl gatekeeper = new PolicyGatekeeperImpl();
        inject(gatekeeper, "policyDecisionService", (PolicyDecisionService) request -> new PolicyEvaluationResult(
                PolicyDecision.allow("p-allow", "ok"),
                "policy-v1"
        ));
        inject(service, "policyGatekeeper", gatekeeper);

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.QUERY_ORDER)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-16").intent(IntentType.QUERY_ORDER).storeId("STORE-16").build())
                .build();

        AgentExecutionStep step = AgentExecutionStep.builder().stepId("s1").targetAgent("work-order").nodeType("AGENT_CALL").build();

        StepEvaluation eval = service.evaluateStep(step, routing, "trace-16");

        Assertions.assertEquals(StepEvaluation.ALLOWED, eval);
    }

    @Test
    void shouldFilterOutNonAllowDecisions() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        AtomicReference<PolicyDecisionType> mode = new AtomicReference<>(PolicyDecisionType.REQUIRE_APPROVAL);
        PolicyGatekeeperImpl gatekeeper = new PolicyGatekeeperImpl();
        inject(gatekeeper, "policyDecisionService", (PolicyDecisionService) request -> new PolicyEvaluationResult(
                switch (mode.get()) {
                    case ALLOW -> PolicyDecision.allow("p-allow", "ok");
                    case DENY -> PolicyDecision.deny("p-deny", "deny");
                    case REQUIRE_APPROVAL -> PolicyDecision.requireApproval("p-approval", "approval", "high");
                },
                "policy-v1"
        ));
        inject(service, "policyGatekeeper", gatekeeper);

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.CREATE_REFUND)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("test")
                .context(AgentContext.builder().sessionId("S-14").intent(IntentType.CREATE_REFUND).storeId("STORE-1").build())
                .build();

        AgentExecutionStep step = AgentExecutionStep.builder().stepId("s1").targetAgent("work-order").nodeType("AGENT_CALL").build();

        StepEvaluation blocked = service.evaluateStep(step, routing, "trace-14");
        Assertions.assertEquals(StepEvaluation.REQUIRES_APPROVAL, blocked);

        mode.set(PolicyDecisionType.ALLOW);
        StepEvaluation allowed = service.evaluateStep(step, routing, "trace-14");
        Assertions.assertEquals(StepEvaluation.ALLOWED, allowed);
    }

    @Test
    void shouldBuildPolicyBlockedResponse() throws Exception {
        OrchestrationControlServiceImpl service = newService();

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.QUERY_ORDER)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("policy blocked")
                .context(AgentContext.builder().sessionId("S-17").storeId("STORE-17").intent(IntentType.QUERY_ORDER).build())
                .build();

        GatewayResponse response = service.buildPolicyBlockedResponse(routing, "trace-17");

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("Gateway-Policy", response.getAgentName());
        Assertions.assertTrue(response.getContent().contains("策略引擎阻断"));
    }

    @Test
    void shouldPreviewPlanWithMemoryAndPolicyState() throws Exception {
        OrchestrationControlServiceImpl service = newService();
        inject(service, "executionPlannerService", (ExecutionPlannerService) request -> new PlanningResult(
                true,
                "planner-v1",
                "ok",
                ExecutionPlan.builder()
                        .planId("plan-preview")
                        .intent("QUERY_ORDER")
                        .nodes(List.of(
                                new ExecutionNode("n1", ExecutionNodeType.AGENT_CALL, "work-order", 7000, 0, false, Map.of(), Map.of()),
                                new ExecutionNode("n2", ExecutionNodeType.AGENT_CALL, "chat", 4000, 0, false, Map.of(), Map.of())
                        ))
                        .edges(List.of(new ExecutionEdge("e1", "n1", "n2", ExecutionEdgeCondition.ON_SUCCESS, Map.of())))
                        .build()
        ));
        inject(service, "policyGatekeeper", policyGatekeeperForPreview());
        inject(service, "memoryReadService", (MemoryReadService) request -> new MemoryReadResult(
                List.of("用户上次问过订单状态"),
                "test-memory",
                true,
                List.of(new MemoryRetrievalHit(
                        "execution_summary",
                        MemoryLayer.SHORT_TERM_SESSION,
                        "用户上次问过订单状态",
                        Map.of("sessionId", "S-18"),
                        "trace-memory",
                        java.time.Instant.parse("2026-04-21T10:15:30Z"),
                        8L,
                        "redis-short-term",
                        0.88,
                        0.52,
                        0.91,
                        0.04,
                        "layer=SHORT_TERM_SESSION, source=redis-short-term, keyword=0.520, vector=0.910, recency=0.040"
                ))
        ));

        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.QUERY_ORDER)
                .targetAgent(RoutingDecision.TARGET_WORK_ORDER)
                .reason("preview")
                .context(AgentContext.builder().sessionId("S-18").storeId("STORE-18").intent(IntentType.QUERY_ORDER).userInput("查询订单123").build())
                .confidence(0.87)
                .build();

        PlanPreviewResponse preview = service.preview(routing, "trace-18");

        Assertions.assertEquals("trace-18", preview.traceId());
        Assertions.assertTrue(preview.memoryHit());
        Assertions.assertEquals("test-memory", preview.memorySource());
        Assertions.assertEquals(1, preview.memoryHits().size());
        Assertions.assertEquals(1, preview.approvalRequiredCount());
        Assertions.assertEquals(2, preview.steps().size());
        Assertions.assertEquals(2, preview.graphNodes().size());
        Assertions.assertEquals(1, preview.graphEdges().size());
        Assertions.assertEquals("REQUIRE_APPROVAL", preview.steps().get(0).policyDecision());
        Assertions.assertEquals(List.of("n1"), preview.steps().get(1).dependsOn());
    }

    @Test
    void shouldKeepOrchestrationServiceFocusedOnControlFlow() {
        String source = fileContent("src/main/java/com/example/dish/gateway/service/impl/OrchestrationControlServiceImpl.java");

        Assertions.assertTrue(Files.exists(Path.of("src/main/java/com/example/dish/gateway/service/impl/PlanningStepMapper.java")));
        Assertions.assertTrue(Files.exists(Path.of("src/main/java/com/example/dish/gateway/service/impl/PlanPreviewAssembler.java")));
        Assertions.assertTrue(Files.exists(Path.of("src/main/java/com/example/dish/gateway/service/impl/ExecutionSummaryWriter.java")));
        Assertions.assertFalse(source.contains("new StepPolicyPreview("));
        Assertions.assertFalse(source.contains("new MemoryWriteRequest("));
    }

    private OrchestrationControlServiceImpl newService() throws Exception {
        OrchestrationControlServiceImpl service = new OrchestrationControlServiceImpl();
        inject(service, "planningStepMapper", new PlanningStepMapper());
        inject(service, "planPreviewAssembler", new PlanPreviewAssembler());
        inject(service, "executionSummaryWriter", new ExecutionSummaryWriter());
        inject(service, "policyGatekeeper", new PolicyGatekeeperImpl());
        return service;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private PolicyGatekeeperImpl policyGatekeeperForPreview() throws Exception {
        PolicyGatekeeperImpl gatekeeper = new PolicyGatekeeperImpl();
        inject(gatekeeper, "policyDecisionService", (PolicyDecisionService) request -> new PolicyEvaluationResult(
                "work-order".equals(request.node().target())
                        ? PolicyDecision.requireApproval("p-preview", "refund needs approval", "high")
                        : PolicyDecision.allow("p-allow", "safe"),
                "policy-v1"
        ));
        return gatekeeper;
    }

    private String fileContent(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
