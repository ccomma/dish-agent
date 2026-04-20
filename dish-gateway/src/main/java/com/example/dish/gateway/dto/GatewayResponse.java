package com.example.dish.gateway.dto;

import com.example.dish.gateway.service.impl.ResponseAggregatorImpl;

import java.util.List;

/**
 * 网关响应封装
 */
public class GatewayResponse {
    private boolean success;
    private String content;
    private String agentName;
    private String intent;
    private String sessionId;
    private String traceId;
    private String planId;
    private String executionMode;
    private int executedStepCount;
    private boolean memoryHit;
    private List<String> memorySnippets;
    private String approvalId;
    private List<String> followUpHints;

    public GatewayResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public int getExecutedStepCount() { return executedStepCount; }
    public void setExecutedStepCount(int executedStepCount) { this.executedStepCount = executedStepCount; }
    public boolean isMemoryHit() { return memoryHit; }
    public void setMemoryHit(boolean memoryHit) { this.memoryHit = memoryHit; }
    public List<String> getMemorySnippets() { return memorySnippets; }
    public void setMemorySnippets(List<String> memorySnippets) { this.memorySnippets = memorySnippets; }
    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
    public List<String> getFollowUpHints() { return followUpHints; }
    public void setFollowUpHints(List<String> followUpHints) { this.followUpHints = followUpHints; }

    public static class GatewayResponseBuilder {
        private boolean success;
        private String content;
        private String agentName;
        private String intent;
        private String sessionId;
        private String traceId;
        private String planId;
        private String executionMode;
        private int executedStepCount;
        private boolean memoryHit;
        private List<String> memorySnippets;
        private String approvalId;
        private List<String> followUpHints;

        public GatewayResponseBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public GatewayResponseBuilder content(String content) {
            this.content = content;
            return this;
        }

        public GatewayResponseBuilder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public GatewayResponseBuilder intent(String intent) {
            this.intent = intent;
            return this;
        }

        public GatewayResponseBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public GatewayResponseBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public GatewayResponseBuilder planId(String planId) {
            this.planId = planId;
            return this;
        }

        public GatewayResponseBuilder executionMode(String executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public GatewayResponseBuilder executedStepCount(int executedStepCount) {
            this.executedStepCount = executedStepCount;
            return this;
        }

        public GatewayResponseBuilder memoryHit(boolean memoryHit) {
            this.memoryHit = memoryHit;
            return this;
        }

        public GatewayResponseBuilder memorySnippets(List<String> memorySnippets) {
            this.memorySnippets = memorySnippets;
            return this;
        }

        public GatewayResponseBuilder approvalId(String approvalId) {
            this.approvalId = approvalId;
            return this;
        }

        public GatewayResponseBuilder followUpHints(List<String> followUpHints) {
            this.followUpHints = followUpHints;
            return this;
        }

        public GatewayResponse build() {
            GatewayResponse response = new GatewayResponse();
            response.setSuccess(success);
            response.setContent(content);
            response.setAgentName(agentName);
            response.setIntent(intent);
            response.setSessionId(sessionId);
            response.setTraceId(traceId);
            response.setPlanId(planId);
            response.setExecutionMode(executionMode);
            response.setExecutedStepCount(executedStepCount);
            response.setMemoryHit(memoryHit);
            response.setMemorySnippets(memorySnippets);
            response.setApprovalId(approvalId);
            response.setFollowUpHints(followUpHints);
            return response;
        }
    }
}
