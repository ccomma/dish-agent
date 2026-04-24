package com.example.dish.gateway.agent;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.ExtractedData;
import com.example.dish.gateway.service.IntentAndParameterExtractor;
import com.example.dish.gateway.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 前置路由 Agent。
 * 负责在 gateway 最前面完成意图识别、参数抽取、租户会话绑定和目标 Agent 选择。
 */
@Component
public class RoutingAgent {
    private static final Logger log = LoggerFactory.getLogger(RoutingAgent.class);

    // 意图 -> 目标 Agent 映射。
    private static final Map<IntentType, String> INTENT_TO_AGENT = Map.of(
        IntentType.GREETING, RoutingDecision.TARGET_CHAT,
        IntentType.GENERAL_CHAT, RoutingDecision.TARGET_CHAT,
        IntentType.DISH_QUESTION, RoutingDecision.TARGET_DISH_KNOWLEDGE,
        IntentType.DISH_INGREDIENT, RoutingDecision.TARGET_DISH_KNOWLEDGE,
        IntentType.DISH_COOKING_METHOD, RoutingDecision.TARGET_DISH_KNOWLEDGE,
        IntentType.POLICY_QUESTION, RoutingDecision.TARGET_DISH_KNOWLEDGE,
        IntentType.QUERY_INVENTORY, RoutingDecision.TARGET_WORK_ORDER,
        IntentType.QUERY_ORDER, RoutingDecision.TARGET_WORK_ORDER,
        IntentType.CREATE_REFUND, RoutingDecision.TARGET_WORK_ORDER
    );

    @Resource
    private IntentAndParameterExtractor extractor;

    @Resource
    private SessionService sessionService;

    public RoutingDecision route(String userInput, String sessionId, String requestStoreId, AgentContext existingContext) {
        // 1. 先用 LLM 一次性完成意图识别和关键参数抽取。
        ExtractedData extracted = extractor.extract(userInput);

        // 2. 再归一化 sessionId，并通过 SessionService 绑定最终 storeId。
        String resolvedSessionId = resolveSessionId(sessionId, existingContext);
        String storeId = sessionService.resolveStoreId(resolvedSessionId, requestStoreId);

        // 3. 构建 AgentContext，后续 planner、policy、agent provider 都复用这份上下文。
        AgentContext context = buildContext(userInput, extracted, resolvedSessionId, storeId, existingContext);

        // 4. 抽取失败时走安全兜底路径，统一回到 chat agent。
        if (extracted.extractionFailed()) {
            log.warn("routing extraction failed: sessionId={}, storeId={}", resolvedSessionId, storeId);
            AgentExecutionStep safeStep = AgentExecutionStep.builder()
                    .stepId("step-safe-chat")
                    .targetAgent(RoutingDecision.TARGET_CHAT)
                    .nodeType("AGENT_CALL")
                    .timeoutMs(4000)
                    .required(true)
                    .build();
            return RoutingDecision.builder()
                    .intent(IntentType.UNKNOWN)
                    .targetAgent(RoutingDecision.TARGET_CHAT)
                    .reason("意图抽取失败，走安全兜底路径")
                    .context(context)
                    .planId("plan-" + resolvedSessionId)
                    .executionMode("single")
                    .confidence(0.2)
                    .executionSteps(List.of(safeStep))
                    .metadata(Map.of("fallback", true, "source", "extraction_failed"))
                    .build();
        }

        log.info("routing request: sessionId={}, intent={}, storeId={}",
                resolvedSessionId, extracted.intent(), storeId);

        // 5. 根据意图映射目标 Agent，并生成最小执行步骤。
        String targetAgent = INTENT_TO_AGENT.getOrDefault(extracted.intent(), RoutingDecision.TARGET_CHAT);
        AgentExecutionStep step = AgentExecutionStep.builder()
                .stepId("step-1")
                .targetAgent(targetAgent)
                .nodeType("AGENT_CALL")
                .timeoutMs(5000)
                .required(true)
                .metadata(Map.of("intent", extracted.intent().name()))
                .build();

        // 6. 最后返回完整 RoutingDecision，供 gateway 主链路继续编排。
        return RoutingDecision.builder()
                .intent(extracted.intent())
                .targetAgent(targetAgent)
                .reason(generateRoutingReason(extracted.intent()))
                .context(context)
                .planId("plan-" + resolvedSessionId)
                .executionMode("single")
                .confidence(0.9)
                .executionSteps(List.of(step))
                .metadata(Map.of("planner", "routing-agent-v1"))
                .build();
    }

    public RoutingDecision route(String userInput, AgentContext existingContext) {
        return route(userInput, null, null, existingContext);
    }

    private String resolveSessionId(String sessionId, AgentContext existingContext) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        if (existingContext != null && existingContext.getSessionId() != null && !existingContext.getSessionId().isBlank()) {
            return existingContext.getSessionId();
        }
        return generateSessionId();
    }

    /**
     * 构建上下文。
     */
    private AgentContext buildContext(String input, ExtractedData extracted,
                                     String sessionId, String storeId, AgentContext existing) {
        return AgentContext.builder()
            .sessionId(sessionId)
            .intent(extracted.intent())
            .userInput(input)
            .storeId(storeId)  // 来自 Session
            .dishName(extracted.dishName())  // 来自 LLM 抽取
            .orderId(extracted.orderId())  // 来自 LLM 抽取
            .refundReason(extracted.refundReason())  // 来自 LLM 抽取
            .build();
    }

    private String generateSessionId() {
        return "SESSION_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateRoutingReason(IntentType intent) {
        return switch (intent) {
            case GREETING -> "问候语，直接进入闲聊模式";
            case GENERAL_CHAT -> "通用闲聊，无需特定知识库或工具";
            case DISH_QUESTION, DISH_INGREDIENT, DISH_COOKING_METHOD -> "菜品咨询，使用知识库检索";
            case POLICY_QUESTION -> "政策规则咨询，使用知识库检索";
            case QUERY_INVENTORY -> "库存查询，使用业务工具";
            case QUERY_ORDER -> "订单查询，使用业务工具";
            case CREATE_REFUND -> "退款申请，使用业务工具";
            case UNKNOWN -> "未知意图，通用处理";
        };
    }
}
