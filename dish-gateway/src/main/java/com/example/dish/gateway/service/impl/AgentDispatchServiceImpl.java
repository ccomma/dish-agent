package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.rpc.ChatAgentService;
import com.example.dish.common.rpc.DishAgentService;
import com.example.dish.common.rpc.WorkOrderAgentService;
import com.example.dish.gateway.config.DubboTraceContextSupport;
import com.example.dish.gateway.service.AgentDispatchService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

/**
 * Agent 分发服务
 * 封装对各 Agent 服务的 Dubbo RPC 调用
 */
@Service
public class AgentDispatchServiceImpl implements AgentDispatchService {

    @DubboReference(timeout = 60000, retries = 0)
    private DishAgentService dishAgentService;

    @DubboReference(timeout = 30000, retries = 0)
    private WorkOrderAgentService workOrderAgentService;

    @DubboReference(timeout = 30000, retries = 0)
    private ChatAgentService chatAgentService;

    /**
     * 根据路由决策分发请求到对应的Agent
     */
    @Override
    public AgentResponse dispatch(RoutingDecision routing) {
        if (routing == null) {
            throw new IllegalArgumentException("路由决策不能为空");
        }

        DubboTraceContextSupport.attachCurrentTraceId();

        String targetAgent = routing.targetAgent();
        AgentResponse response;

        switch (targetAgent) {
            case RoutingDecision.TARGET_DISH_KNOWLEDGE ->
                    response = dishAgentService.answerWithReflection(routing.context().getUserInput(), routing.context());
            case RoutingDecision.TARGET_WORK_ORDER -> response = workOrderAgentService.process(routing.context());
            case RoutingDecision.TARGET_CHAT ->
                    response = chatAgentService.chat(routing.context().getUserInput(), routing.context().getSessionId());
            default -> response = AgentResponse.failure("未知目标Agent: " + targetAgent, "Gateway", routing.context());
        }

        return response;
    }
}
