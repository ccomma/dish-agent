package com.example.dish.common.contract;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 路由后的执行步骤定义。
 */
public class AgentExecutionStep implements Serializable {

    private final String stepId;
    private final String targetAgent;
    private final String nodeType;
    private final List<String> dependsOn;
    private final String parallelGroup;
    private final long timeoutMs;
    private final boolean required;
    private final Map<String, Object> metadata;

    private AgentExecutionStep(Builder builder) {
        this.stepId = builder.stepId;
        this.targetAgent = builder.targetAgent;
        this.nodeType = builder.nodeType;
        this.dependsOn = builder.dependsOn != null ? new ArrayList<>(builder.dependsOn) : Collections.emptyList();
        this.parallelGroup = builder.parallelGroup;
        this.timeoutMs = builder.timeoutMs;
        this.required = builder.required;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentExecutionStep singleAgent(String stepId, String targetAgent, long timeoutMs) {
        return builder()
                .stepId(stepId)
                .targetAgent(targetAgent)
                .nodeType("AGENT_CALL")
                .timeoutMs(timeoutMs)
                .required(true)
                .build();
    }

    public String stepId() {
        return stepId;
    }

    public String targetAgent() {
        return targetAgent;
    }

    public String nodeType() {
        return nodeType;
    }

    public List<String> dependsOn() {
        return dependsOn;
    }

    public String parallelGroup() {
        return parallelGroup;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public boolean required() {
        return required;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static class Builder {
        private String stepId;
        private String targetAgent;
        private String nodeType;
        private List<String> dependsOn;
        private String parallelGroup;
        private long timeoutMs = 5000;
        private boolean required = true;
        private Map<String, Object> metadata;

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder targetAgent(String targetAgent) {
            this.targetAgent = targetAgent;
            return this;
        }

        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder dependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
            return this;
        }

        public Builder parallelGroup(String parallelGroup) {
            this.parallelGroup = parallelGroup;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AgentExecutionStep build() {
            return new AgentExecutionStep(this);
        }
    }
}
