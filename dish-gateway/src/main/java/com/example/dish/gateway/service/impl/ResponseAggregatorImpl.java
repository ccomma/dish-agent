package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.gateway.dto.GatewayResponse;
import com.example.dish.gateway.service.ResponseAggregator;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
                .sessionId(response.getContext() != null ? response.getContext().getSessionId() : null);

        if (response.getFollowUpHints() != null && !response.getFollowUpHints().isEmpty()) {
            builder.followUpHints(response.getFollowUpHints());
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

}
