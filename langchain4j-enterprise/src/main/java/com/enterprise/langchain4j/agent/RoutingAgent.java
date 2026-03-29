package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.classifier.IntentClassifier;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.RoutingDecision;

import java.util.UUID;

/**
 * 前置路由Agent
 *
 * 职责边界：
 * 1. 接收用户原始输入
 * 2. 调用IntentClassifier识别意图
 * 3. 执行参数抽取（门店ID、订单号、菜名等）
 * 4. 做出路由决策，决定下一个处理的Agent
 * 5. 返回RoutingDecision给编排层
 */
public class RoutingAgent {

    private final IntentClassifier intentClassifier;

    // 意图 -> 目标Agent 映射
    private static final java.util.Map<IntentType, String> INTENT_TO_AGENT = java.util.Map.of(
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

    public RoutingAgent(IntentClassifier intentClassifier) {
        this.intentClassifier = intentClassifier;
    }

    public RoutingAgent() {
        this(new IntentClassifier());
    }

    /**
     * 执行路由决策
     */
    public RoutingDecision route(String userInput, AgentContext existingContext) {
        // 1. 意图识别
        IntentType intent = intentClassifier.classify(userInput);

        // 2. 构建/更新上下文
        AgentContext context = buildContext(userInput, intent, existingContext);

        // 3. 确定目标Agent
        String targetAgent = INTENT_TO_AGENT.getOrDefault(intent, RoutingDecision.TARGET_CHAT);

        // 4. 生成路由决策
        return new RoutingDecision(
            intent,
            targetAgent,
            generateRoutingReason(intent),
            context
        );
    }

    /**
     * 抽取关键参数到上下文
     */
    private AgentContext buildContext(String input, IntentType intent, AgentContext existing) {
        String sessionId = existing != null ? existing.getSessionId() : generateSessionId();

        AgentContext.Builder builder = AgentContext.builder()
            .sessionId(sessionId)
            .intent(intent)
            .userInput(input);

        // 根据意图类型抽取对应参数
        switch (intent) {
            case QUERY_INVENTORY -> {
                builder.storeId(extractStoreId(input));
                builder.dishName(extractDishName(input));
            }
            case QUERY_ORDER, CREATE_REFUND -> {
                builder.orderId(extractOrderId(input));
                if (intent == IntentType.CREATE_REFUND) {
                    builder.refundReason(extractReason(input));
                }
            }
            default -> {
                if (existing != null) {
                    builder.storeId(existing.getStoreId());
                    builder.orderId(existing.getOrderId());
                    builder.dishName(existing.getDishName());
                }
            }
        }

        return builder.build();
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

    // ===== 参数抽取辅助方法 =====

    private String extractStoreId(String input) {
        if (input.contains("门店") || input.contains("店")) {
            return "STORE_001";
        }
        return "STORE_001";
    }

    private String extractDishName(String input) {
        String[] dishes = {"宫保鸡丁", "麻婆豆腐", "红烧肉", "糖醋里脊", "鱼香肉丝"};
        for (String dish : dishes) {
            if (input.contains(dish)) return dish;
        }
        return null;
    }

    private String extractOrderId(String input) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\d{5,}");
        java.util.regex.Matcher m = p.matcher(input);
        if (m.find()) return m.group();
        return null;
    }

    private String extractReason(String input) {
        if (input.contains("因为")) {
            return input.substring(input.indexOf("因为") + 2).trim();
        }
        if (input.contains("原因")) {
            int idx = input.indexOf("原因");
            return input.substring(idx + 2).trim();
        }
        return "用户主动申请";
    }
}
