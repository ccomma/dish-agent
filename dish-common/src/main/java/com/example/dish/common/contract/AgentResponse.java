package com.example.dish.common.contract;

import com.example.dish.common.context.AgentContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent统一响应封装
 */
public class AgentResponse implements java.io.Serializable {

    private final boolean success;
    private final String content;
    private final String agentName;
    private final AgentContext context;
    private final List<String> followUpHints;

    private AgentResponse(Builder builder) {
        this.success = builder.success;
        this.content = builder.content;
        this.agentName = builder.agentName;
        this.context = builder.context;
        this.followUpHints = builder.followUpHints != null ? new ArrayList<>(builder.followUpHints) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentResponse success(String content, String agentName, AgentContext context) {
        return builder()
            .success(true)
            .content(content)
            .agentName(agentName)
            .context(context)
            .build();
    }

    public static AgentResponse failure(String message, String agentName, AgentContext context) {
        return builder()
            .success(false)
            .content(message)
            .agentName(agentName)
            .context(context)
            .build();
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public String getAgentName() { return agentName; }
    public AgentContext getContext() { return context; }
    public List<String> getFollowUpHints() { return followUpHints; }

    /**
     * 格式化输出，包含followUpHints
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(content);
        if (!followUpHints.isEmpty()) {
            sb.append("\n\n您可能还想：");
            for (String hint : followUpHints) {
                sb.append("\n  • ").append(hint);
            }
        }
        return sb.toString();
    }

    public static class Builder {
        private boolean success;
        private String content;
        private String agentName;
        private AgentContext context;
        private List<String> followUpHints;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder agentName(String agentName) { this.agentName = agentName; return this; }
        public Builder context(AgentContext context) { this.context = context; return this; }
        public Builder followUpHints(List<String> followUpHints) { this.followUpHints = followUpHints; return this; }

        public Builder addFollowUpHint(String hint) {
            if (this.followUpHints == null) {
                this.followUpHints = new ArrayList<>();
            }
            this.followUpHints.add(hint);
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(this);
        }
    }
}
