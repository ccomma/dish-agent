package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.ExecutionEventPublisher;
import com.example.dish.gateway.service.OrchestrationControlService;
import com.example.dish.gateway.service.ResponseAggregator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ChatExecutionServiceImplTest {

    @Test
    void shouldRejectBlankMessageBeforeRouting() throws Exception {
        ChatExecutionServiceImpl service = new ChatExecutionServiceImpl();
        CountingRoutingAgent routingAgent = new CountingRoutingAgent();
        inject(service, "routingAgent", routingAgent);

        GatewayResponse response = service.process("  ", "SESSION-1", "STORE-1", "trace-1");

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("消息不能为空", response.getContent());
        Assertions.assertEquals(0, routingAgent.routeCount);
    }

    @Test
    void shouldExecutePlannedStepAndPublishSummary() throws Exception {
        ChatExecutionServiceImpl service = new ChatExecutionServiceImpl();
        AgentExecutionStep step = AgentExecutionStep.builder()
                .stepId("s1")
                .targetAgent(RoutingDecision.TARGET_CHAT)
                .nodeType("AGENT_CALL")
                .build();
        RoutingDecision routing = RoutingDecision.builder()
                .intent(IntentType.GENERAL_CHAT)
                .targetAgent(RoutingDecision.TARGET_CHAT)
                .reason("test")
                .context(AgentContext.builder()
                        .sessionId("SESSION-2")
                        .storeId("STORE-2")
                        .userInput("你好")
                        .intent(IntentType.GENERAL_CHAT)
                        .build())
                .planId("plan-2")
                .executionMode("single")
                .build();
        FakeOrchestrationControlService orchestration = new FakeOrchestrationControlService(step);
        FakeExecutionEventPublisher events = new FakeExecutionEventPublisher();

        inject(service, "routingAgent", new CountingRoutingAgent(routing));
        inject(service, "orchestrationControlService", orchestration);
        inject(service, "executionEventPublisher", events);
        inject(service, "executionStepRunner", new SuccessfulStepRunner());
        inject(service, "responseAggregator", new FakeResponseAggregator());

        GatewayResponse response = service.process("你好", "SESSION-2", "STORE-2", "trace-2");

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals("step-ok", response.getContent());
        Assertions.assertEquals(1, orchestration.summaryWrites);
        Assertions.assertEquals(List.of(ExecutionNodeStatus.SUCCEEDED), events.nodeStatuses);
        Assertions.assertEquals(ExecutionNodeStatus.SUCCEEDED, events.summaryStatus);
    }

    private static class CountingRoutingAgent extends RoutingAgent {
        private final RoutingDecision routing;
        private int routeCount;

        CountingRoutingAgent() {
            this.routing = null;
        }

        CountingRoutingAgent(RoutingDecision routing) {
            this.routing = routing;
        }

        @Override
        public RoutingDecision route(String userInput, String sessionId, String requestStoreId, AgentContext existingContext) {
            routeCount++;
            return routing;
        }
    }

    private static class SuccessfulStepRunner extends ExecutionStepRunner {
        @Override
        public StepRunResult run(ExecutionGraphViewResult graph,
                                 RoutingDecision routing,
                                 AgentExecutionStep step,
                                 int stepIndex,
                                 int stepCount,
                                 String traceId,
                                 String spanName,
                                 String runningReason) {
            return new StepRunResult(AgentResponse.success("step-ok", "chat-agent", routing.context()), 12L);
        }
    }

    private static class FakeOrchestrationControlService implements OrchestrationControlService {
        private final AgentExecutionStep step;
        private int summaryWrites;

        FakeOrchestrationControlService(AgentExecutionStep step) {
            this.step = step;
        }

        @Override
        public List<AgentExecutionStep> planSteps(RoutingDecision routing, String traceId) {
            return List.of(step);
        }

        @Override
        public List<AgentExecutionStep> filterAllowedSteps(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId) {
            return steps;
        }

        @Override
        public AgentExecutionStep findFirstApprovalRequiredStep(List<AgentExecutionStep> steps, RoutingDecision routing, String traceId) {
            return null;
        }

        @Override
        public void writeExecutionSummary(RoutingDecision routing, int executedStepCount, boolean success, String traceId) {
            summaryWrites++;
        }

        @Override
        public String createApprovalTicket(RoutingDecision routing, String executionId, AgentExecutionStep step, String traceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GatewayResponse buildApprovalPendingResponse(RoutingDecision routing, String traceId, String approvalId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GatewayResponse buildPolicyBlockedResponse(RoutingDecision routing, String traceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.example.dish.gateway.dto.control.PlanPreviewResponse preview(RoutingDecision routing, String traceId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeExecutionEventPublisher implements ExecutionEventPublisher {
        private final List<ExecutionNodeStatus> nodeStatuses = new ArrayList<>();
        private ExecutionNodeStatus summaryStatus;

        @Override
        public ExecutionGraphViewResult startExecution(RoutingDecision routing, List<AgentExecutionStep> steps, String traceId) {
            return new ExecutionGraphViewResult(
                    "exec-2",
                    routing.planId(),
                    routing.context().getSessionId(),
                    routing.context().getStoreId(),
                    traceId,
                    routing.intent().name(),
                    routing.executionMode(),
                    ExecutionNodeStatus.PENDING,
                    Instant.now(),
                    null,
                    0L,
                    Map.of(),
                    List.of(),
                    List.of(),
                    0
            );
        }

        @Override
        public void publishNodeStatus(ExecutionGraphViewResult graph,
                                      AgentExecutionStep step,
                                      ExecutionNodeStatus status,
                                      int stepIndex,
                                      int stepCount,
                                      String traceId,
                                      String statusReason,
                                      long latencyMs,
                                      AgentResponse response,
                                      String approvalId) {
            nodeStatuses.add(status);
        }

        @Override
        public void publishExecutionSummary(ExecutionGraphViewResult graph,
                                            ExecutionNodeStatus status,
                                            String traceId,
                                            String statusReason,
                                            long durationMs,
                                            int executedSteps) {
            summaryStatus = status;
        }

        @Override
        public SseEmitter subscribe(String executionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeResponseAggregator implements ResponseAggregator {

        @Override
        public GatewayResponse aggregate(AgentResponse response, RoutingDecision routing) {
            return build(response, routing);
        }

        @Override
        public GatewayResponse aggregate(List<AgentResponse> responses, RoutingDecision routing) {
            return build(responses.get(0), routing);
        }

        @Override
        public GatewayResponse aggregate(AgentResponse response) {
            return build(response, null);
        }

        private GatewayResponse build(AgentResponse agentResponse, RoutingDecision routing) {
            GatewayResponse response = new GatewayResponse();
            response.setSuccess(true);
            response.setContent(agentResponse.getContent());
            response.setSessionId(routing != null ? routing.context().getSessionId() : null);
            return response;
        }
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
