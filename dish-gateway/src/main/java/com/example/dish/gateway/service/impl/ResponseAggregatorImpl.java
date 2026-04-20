package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.ResponseAggregator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结果聚合服务
 * 将多个Agent的响应聚合为最终响应
 */
@Service
public class ResponseAggregatorImpl implements ResponseAggregator {

    /**
     * 聚合单个Agent的响应
     */
    @Override
    public GatewayResponse aggregate(AgentResponse response, RoutingDecision routing) {
        GatewayResponse.GatewayResponseBuilder builder = new GatewayResponse.GatewayResponseBuilder()
                .success(response.isSuccess())
                .content(response.getContent())
                .agentName(response.getAgentName())
                .intent(routing != null ? routing.intent().name() : null)
                .sessionId(response.getContext() != null ? response.getContext().getSessionId() : null)
                .traceId(traceId(routing))
                .planId(routing != null ? routing.planId() : null)
                .executionMode(routing != null ? routing.executionMode() : null)
                .executedStepCount(response != null ? 1 : 0)
                .memoryHit(memoryHit(routing))
                .memorySnippets(memorySnippets(routing))
                .approvalId(approvalId(routing));

        if (response.getFollowUpHints() != null && !response.getFollowUpHints().isEmpty()) {
            builder.followUpHints(response.getFollowUpHints());
        }

        return builder.build();
    }

    @Override
    public GatewayResponse aggregate(List<AgentResponse> responses, RoutingDecision routing) {
        if (responses == null || responses.isEmpty()) {
            GatewayResponse empty = new GatewayResponse();
            empty.setSuccess(false);
            empty.setContent("编排结果为空");
            empty.setAgentName("Gateway-Orchestrator");
            empty.setIntent(routing != null && routing.intent() != null ? routing.intent().name() : null);
            empty.setSessionId(routing != null && routing.context() != null ? routing.context().getSessionId() : null);
            return empty;
        }

        AgentResponse primary = responses.get(0);
        List<String> mergedHints = new ArrayList<>();
        List<String> supportContents = new ArrayList<>();
        boolean allSuccess = true;

        for (int i = 0; i < responses.size(); i++) {
            AgentResponse current = responses.get(i);
            if (!current.isSuccess()) {
                allSuccess = false;
            }
            if (current.getFollowUpHints() != null && !current.getFollowUpHints().isEmpty()) {
                mergedHints.addAll(current.getFollowUpHints());
            }
            if (i > 0 && current.getContent() != null && !current.getContent().isBlank()) {
                supportContents.add("[" + current.getAgentName() + "] " + current.getContent());
            }
        }

        String mergedContent = primary.getContent();
        if (!supportContents.isEmpty()) {
            mergedContent = mergedContent + "\n\n补充信息：\n- " + String.join("\n- ", supportContents);
        }

        GatewayResponse.GatewayResponseBuilder builder = new GatewayResponse.GatewayResponseBuilder()
                .success(allSuccess)
                .content(mergedContent)
                .agentName(primary.getAgentName())
                .intent(routing != null && routing.intent() != null ? routing.intent().name() : null)
                .sessionId(primary.getContext() != null ? primary.getContext().getSessionId() : null)
                .traceId(traceId(routing))
                .planId(routing != null ? routing.planId() : null)
                .executionMode(routing != null ? routing.executionMode() : null)
                .executedStepCount(responses.size())
                .memoryHit(memoryHit(routing))
                .memorySnippets(memorySnippets(routing))
                .approvalId(approvalId(routing));

        if (!mergedHints.isEmpty()) {
            builder.followUpHints(mergedHints.stream().distinct().toList());
        }

        return builder.build();
    }

    /**
     * 聚合响应（简化版）
     */
    @Override
    public GatewayResponse aggregate(AgentResponse response) {
        return aggregate(response, null);
    }

    private String traceId(RoutingDecision routing) {
        Object value = metadata(routing).get("traceId");
        return value instanceof String text ? text : null;
    }

    private boolean memoryHit(RoutingDecision routing) {
        Object value = metadata(routing).get("memoryHit");
        return value instanceof Boolean flag && flag;
    }

    private String approvalId(RoutingDecision routing) {
        Object value = metadata(routing).get("approvalId");
        return value instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> memorySnippets(RoutingDecision routing) {
        Object value = metadata(routing).get("memorySnippets");
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private Map<String, Object> metadata(RoutingDecision routing) {
        if (routing == null || routing.context() == null || routing.context().getMetadata() == null) {
            return Map.of();
        }
        return routing.context().getMetadata();
    }
}
