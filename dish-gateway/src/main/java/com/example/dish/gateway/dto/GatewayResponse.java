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
    public List<String> getFollowUpHints() { return followUpHints; }
    public void setFollowUpHints(List<String> followUpHints) { this.followUpHints = followUpHints; }

    public static class GatewayResponseBuilder {
        private boolean success;
        private String content;
        private String agentName;
        private String intent;
        private String sessionId;
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
            response.setFollowUpHints(followUpHints);
            return response;
        }
    }
}
