package com.example.dish.common.context;

import com.example.dish.common.classifier.IntentType;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent上下文 - 跨Agent状态传递载体
 * 用于在多Agent协作时共享状态信息
 */
public class AgentContext implements Serializable {

    private String sessionId;              // 会话ID
    private IntentType intent;             // 当前意图
    private String userInput;              // 用户输入
    private String storeId;                // 门店ID
    private String orderId;               // 订单ID
    private String dishName;               // 菜品名称
    private String refundReason;           // 退款原因
    private Map<String, Object> metadata;  // 扩展元数据

    private AgentContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.intent = builder.intent;
        this.userInput = builder.userInput;
        this.storeId = builder.storeId;
        this.orderId = builder.orderId;
        this.dishName = builder.dishName;
        this.refundReason = builder.refundReason;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentContext createDefault() {
        return builder()
            .sessionId(generateSessionId())
            .build();
    }

    public static AgentContext forSession(String sessionId) {
        return builder()
            .sessionId(sessionId)
            .build();
    }

    /**
     * 构造 Agent 请求上下文，供 RPC 快捷入口复用同一套字段装配约定。
     */
    public static AgentContext fromRequest(String sessionId,
                                           IntentType intent,
                                           String storeId,
                                           String orderId,
                                           String dishName,
                                           String refundReason,
                                           String userInput) {
        return builder()
            .sessionId(sessionId)
            .intent(intent)
            .storeId(storeId)
            .orderId(orderId)
            .dishName(dishName)
            .refundReason(refundReason)
            .userInput(userInput)
            .build();
    }

    private static String generateSessionId() {
        return "SESSION_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public IntentType getIntent() { return intent; }
    public String getUserInput() { return userInput; }
    public String getStoreId() { return storeId; }
    public String getOrderId() { return orderId; }
    public String getDishName() { return dishName; }
    public String getRefundReason() { return refundReason; }
    public Map<String, Object> getMetadata() { return metadata; }

    // Setters (returning new builder for immutable pattern)
    public AgentContext withIntent(IntentType intent) {
        return copyBuilder().intent(intent).build();
    }

    public AgentContext withUserInput(String userInput) {
        return copyBuilder().userInput(userInput).build();
    }

    public AgentContext withStoreId(String storeId) {
        return copyBuilder().storeId(storeId).build();
    }

    public AgentContext withMetadataValue(String key, Object value) {
        Map<String, Object> copiedMetadata = new HashMap<>(this.metadata);
        copiedMetadata.put(key, value);
        return copyBuilder().metadata(copiedMetadata).build();
    }

    @Override
    public String toString() {
        return "AgentContext{" +
            "sessionId='" + sessionId + '\'' +
            ", intent=" + intent +
            ", storeId='" + storeId + '\'' +
            ", orderId='" + orderId + '\'' +
            ", dishName='" + dishName + '\'' +
            ", refundReason='" + refundReason + '\'' +
            '}';
    }

    /**
     * 基于当前上下文复制一份 builder，便于局部覆写字段。
     */
    public Builder copyBuilder() {
        return builder()
            .sessionId(this.sessionId)
            .intent(this.intent)
            .userInput(this.userInput)
            .storeId(this.storeId)
            .orderId(this.orderId)
            .dishName(this.dishName)
            .refundReason(this.refundReason)
            .metadata(this.metadata);
    }

    public static class Builder {
        private String sessionId;
        private IntentType intent;
        private String userInput;
        private String storeId;
        private String orderId;
        private String dishName;
        private String refundReason;
        private Map<String, Object> metadata;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder intent(IntentType intent) { this.intent = intent; return this; }
        public Builder userInput(String userInput) { this.userInput = userInput; return this; }
        public Builder storeId(String storeId) { this.storeId = storeId; return this; }
        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder dishName(String dishName) { this.dishName = dishName; return this; }
        public Builder refundReason(String refundReason) { this.refundReason = refundReason; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public AgentContext build() {
            return new AgentContext(this);
        }
    }
}
