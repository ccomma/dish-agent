package com.example.dish.service.provider;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.DishAgentService;
import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
import com.example.dish.service.DishReActAgent;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 菜品知识Agent Dubbo服务实现
 */
@Component
@DubboService(
        interfaceClass = DishAgentService.class,
        timeout = 60000,
        retries = 0
)
public class DishAgentServiceImpl implements DishAgentService {
    @Resource
    private DishReActAgent dishReActAgent;

    @Override
    public AgentResponse answer(String userInput, AgentContext context) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("dish-agent.answer", "dish-agent-dish");
        try (spanScope) {
            return dishReActAgent.answer(userInput, context);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    @Override
    public AgentResponse answerWithReflection(String userInput, AgentContext context) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("dish-agent.answer_with_reflection", "dish-agent-dish");
        try (spanScope) {
            return dishReActAgent.answerWithReflection(userInput, context);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }
}
