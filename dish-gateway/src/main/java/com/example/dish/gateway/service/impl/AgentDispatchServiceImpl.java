package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.rpc.ChatAgentService;
import com.example.dish.common.rpc.DishAgentService;
import com.example.dish.common.rpc.WorkOrderAgentService;
import com.example.dish.gateway.config.DubboTraceContextSupport;
import com.example.dish.gateway.service.AgentDispatchService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 分发服务
 * 封装对各 Agent 服务的 Dubbo RPC 调用
 */
@Service
public class AgentDispatchServiceImpl implements AgentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchServiceImpl.class);

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
        return dispatchToTarget(routing, routing.targetAgent());
    }

    @Override
    public List<AgentResponse> dispatchAll(RoutingDecision routing, List<AgentExecutionStep> steps) {
        if (routing == null) {
            throw new IllegalArgumentException("路由决策不能为空");
        }
        if (steps == null || steps.isEmpty()) {
            return List.of(dispatch(routing));
        }

        List<AgentResponse> responses = new ArrayList<>();
        for (AgentExecutionStep step : steps) {
            responses.add(dispatchToTarget(routing, step.targetAgent()));
        }
        return responses;
    }

    private AgentResponse dispatchToTarget(RoutingDecision routing, String targetAgent) {
        DubboTraceContextSupport.attachCurrentTraceId();

        String sessionId = routing.context() != null ? routing.context().getSessionId() : null;

        try {
            return switch (targetAgent) {
                case RoutingDecision.TARGET_DISH_KNOWLEDGE ->
                        dishAgentService.answerWithReflection(routing.context().getUserInput(), routing.context());
                case RoutingDecision.TARGET_WORK_ORDER -> workOrderAgentService.process(routing.context());
                case RoutingDecision.TARGET_CHAT ->
                        chatAgentService.chat(routing.context().getUserInput(), routing.context().getSessionId());
                default -> AgentResponse.failure("未知目标Agent: " + targetAgent, "Gateway", routing.context());
            };
        } catch (Exception ex) {
            log.error("agent dispatch failed: sessionId={}, targetAgent={}, message={}",
                    sessionId, targetAgent, ex.getMessage(), ex);
            return buildDegradedResponse(targetAgent, routing);
        }
    }

    private AgentResponse buildDegradedResponse(String targetAgent, RoutingDecision routing) {
        return switch (targetAgent) {
            case RoutingDecision.TARGET_DISH_KNOWLEDGE -> AgentResponse.builder()
                    .success(false)
                    .content("菜品服务暂时不可用，请稍后重试或换个问法")
                    .agentName("Gateway")
                    .context(routing.context())
                    .followUpHints(List.of("换个表述再问一次", "稍后再试", "改问其他菜品问题"))
                    .build();
            case RoutingDecision.TARGET_WORK_ORDER -> AgentResponse.builder()
                    .success(false)
                    .content("工单服务暂时不可用，请稍后重试或联系人工客服")
                    .agentName("Gateway")
                    .context(routing.context())
                    .followUpHints(List.of("稍后重试", "联系人工客服", "检查订单号后重试"))
                    .build();
            case RoutingDecision.TARGET_CHAT -> AgentResponse.builder()
                    .success(false)
                    .content("聊天服务暂时不可用，请稍后重试")
                    .agentName("Gateway")
                    .context(routing.context())
                    .followUpHints(List.of("稍后再试", "切换为业务查询", "重新发起会话"))
                    .build();
            default -> AgentResponse.failure("未知目标Agent: " + targetAgent, "Gateway", routing.context());
        };
    }
}
