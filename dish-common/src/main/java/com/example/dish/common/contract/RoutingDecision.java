package com.example.dish.common.contract;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.constants.AgentTargets;
import com.example.dish.common.constants.ExecutionModes;
import com.example.dish.common.context.AgentContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 路由决策 - RoutingAgent的输出
 * 支持单Agent兼容模式与多步骤执行计划。
 */
public class RoutingDecision implements Serializable {

    private final IntentType intent;
    private final String targetAgent;
    private final String reason;
    private final AgentContext context;
    private final String planId;
    private final List<AgentExecutionStep> executionSteps;
    private final String executionMode;
    private final double confidence;
    private final Map<String, Object> metadata;

    /**
     * 兼容构造器：保留现有单目标路由调用方式。
     */
    public RoutingDecision(IntentType intent, String targetAgent, String reason, AgentContext context) {
        this(
                intent,
                targetAgent,
                reason,
                context,
                null,
                targetAgent == null ? Collections.emptyList() : List.of(AgentExecutionStep.singleAgent("step-1", targetAgent, 5000)),
                ExecutionModes.SINGLE,
                1.0,
                Collections.emptyMap()
        );
    }

    public RoutingDecision(IntentType intent,
                           String targetAgent,
                           String reason,
                           AgentContext context,
                           String planId,
                           List<AgentExecutionStep> executionSteps,
                           String executionMode,
                           double confidence,
                           Map<String, Object> metadata) {
        this.intent = intent;
        this.targetAgent = targetAgent;
        this.reason = reason;
        this.context = context;
        this.planId = planId;
        this.executionSteps = executionSteps != null ? new ArrayList<>(executionSteps) : Collections.emptyList();
        this.executionMode = executionMode;
        this.confidence = confidence;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
    }

    /**
     * 预定义的目标Agent常量
     */
    public static final String TARGET_DISH_KNOWLEDGE = AgentTargets.DISH_KNOWLEDGE;
    public static final String TARGET_WORK_ORDER = AgentTargets.WORK_ORDER;
    public static final String TARGET_CHAT = AgentTargets.CHAT;

    public IntentType intent() { return intent; }

    /**
     * 兼容单目标访问器。
     * 当 targetAgent 为空且 executionSteps 非空时，返回第一个步骤的目标Agent。
     */
    public String targetAgent() {
        if (targetAgent != null) {
            return targetAgent;
        }
        if (!executionSteps.isEmpty()) {
            return executionSteps.get(0).targetAgent();
        }
        return null;
    }

    public String reason() { return reason; }
    public AgentContext context() { return context; }
    public String planId() { return planId; }
    public List<AgentExecutionStep> executionSteps() { return executionSteps; }
    public String executionMode() { return executionMode; }
    public double confidence() { return confidence; }
    public Map<String, Object> metadata() { return metadata; }

    /**
     * 是否为单步骤执行计划。
     */
    public boolean isSingleStepPlan() {
        return executionSteps.size() <= 1;
    }

    /**
     * 判断是否为闲聊类路由
     */
    public boolean isChatRouting() {
        return TARGET_CHAT.equals(targetAgent());
    }

    /**
     * 判断是否为菜品知识路由
     */
    public boolean isDishKnowledgeRouting() {
        return TARGET_DISH_KNOWLEDGE.equals(targetAgent());
    }

    /**
     * 判断是否为工单处理路由
     */
    public boolean isWorkOrderRouting() {
        return TARGET_WORK_ORDER.equals(targetAgent());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private IntentType intent;
        private String targetAgent;
        private String reason;
        private AgentContext context;
        private String planId;
        private List<AgentExecutionStep> executionSteps;
        private String executionMode = ExecutionModes.SINGLE;
        private double confidence = 1.0;
        private Map<String, Object> metadata;

        public Builder intent(IntentType intent) {
            this.intent = intent;
            return this;
        }

        public Builder targetAgent(String targetAgent) {
            this.targetAgent = targetAgent;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder context(AgentContext context) {
            this.context = context;
            return this;
        }

        public Builder planId(String planId) {
            this.planId = planId;
            return this;
        }

        public Builder executionSteps(List<AgentExecutionStep> executionSteps) {
            this.executionSteps = executionSteps;
            return this;
        }

        public Builder executionMode(String executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RoutingDecision build() {
            String resolvedTarget = targetAgent;
            if (resolvedTarget == null && executionSteps != null && !executionSteps.isEmpty()) {
                resolvedTarget = executionSteps.get(0).targetAgent();
            }
            List<AgentExecutionStep> resolvedSteps = executionSteps;
            if ((resolvedSteps == null || resolvedSteps.isEmpty()) && resolvedTarget != null) {
                resolvedSteps = List.of(AgentExecutionStep.singleAgent("step-1", resolvedTarget, 5000));
            }
            return new RoutingDecision(intent, resolvedTarget, reason, context, planId, resolvedSteps, executionMode, confidence, metadata);
        }
    }
}
