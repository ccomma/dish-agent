package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.ResponseAggregator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 结果聚合服务。
 * 负责把单个或多个 AgentResponse 合并成 gateway 对外返回的统一响应。
 */
@Service
public class ResponseAggregatorImpl implements ResponseAggregator {

    @Override
    public GatewayResponse aggregate(AgentResponse response, RoutingDecision routing) {
        // 单响应场景直接透传主体内容，并补齐 trace / memory / approval 元数据。
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
        // 1. 没有任何响应时返回统一空编排结果。
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

        // 2. 以第一个响应作为主响应，其余响应合并为补充信息和 follow-up hints。
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

        // 3. 组装最终 gateway 响应。
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

    @Override
    public GatewayResponse aggregate(AgentResponse response) {
        return aggregate(response, null);
    }

    private String traceId(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return null;
        }
        return routing.context().getTraceId();
    }

    private boolean memoryHit(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return false;
        }
        Boolean value = routing.context().getMemoryHit();
        return value != null && value;
    }

    private String approvalId(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return null;
        }
        return routing.context().getApprovalId();
    }

    private List<String> memorySnippets(RoutingDecision routing) {
        if (routing == null || routing.context() == null) {
            return List.of();
        }
        List<String> value = routing.context().getMemorySnippets();
        return value != null ? value : List.of();
    }
}
