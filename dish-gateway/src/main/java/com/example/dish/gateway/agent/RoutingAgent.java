package com.example.dish.gateway.agent;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.ExtractedData;
import com.example.dish.gateway.service.IntentAndParameterExtractor;
import com.example.dish.gateway.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;

/**
 * 前置路由Agent（网关层）
 *
 * 职责：
 * 1. 接收用户原始输入
 * 2. LLM 识别意图
 * 3. LLM 抽取参数（菜名、订单号、退款原因等）
 * 4. 从 Session 获取店铺ID（多租户隔离）
 * 5. 做出路由决策
 */
@Component
public class RoutingAgent {
    private static final Logger log = LoggerFactory.getLogger(RoutingAgent.class);

    // 意图 -> 目标Agent 映射
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

    /**
     * 执行路由决策
     *
     * @param userInput 用户输入
     * @param existingContext 已存在的上下文（用于传递已抽取的参数）
     * @return 路由决策
     */
    public RoutingDecision route(String userInput, String sessionId, String requestStoreId, AgentContext existingContext) {
        // 1. LLM 同时识别意图和抽取参数
        ExtractedData extracted = extractor.extract(userInput);

        // 2. 获取店铺ID（从 Session，多租户隔离）
        String resolvedSessionId = resolveSessionId(sessionId, existingContext);
        String storeId = sessionService.resolveStoreId(resolvedSessionId, requestStoreId);

        // 3. 构建上下文（合并 LLM 抽取的参数 + Session 中的店铺ID）
        AgentContext context = buildContext(userInput, extracted, resolvedSessionId, storeId, existingContext);

        if (extracted.extractionFailed()) {
            log.warn("routing extraction failed: sessionId={}, storeId={}", resolvedSessionId, storeId);
            return new RoutingDecision(
                    IntentType.UNKNOWN,
                    RoutingDecision.TARGET_CHAT,
                    "意图抽取失败，走安全兜底路径",
                    context
            );
        }

        log.info("routing request: sessionId={}, intent={}, storeId={}",
                resolvedSessionId, extracted.intent(), storeId);

        // 4. 确定目标Agent
        String targetAgent = INTENT_TO_AGENT.getOrDefault(extracted.intent(), RoutingDecision.TARGET_CHAT);

        // 5. 生成路由决策
        return new RoutingDecision(
            extracted.intent(),
            targetAgent,
            generateRoutingReason(extracted.intent()),
            context
        );
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
     * 构建上下文
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
