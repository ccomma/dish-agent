package com.example.dish.service.provider;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.DishAgentService;
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
        return dishReActAgent.answer(userInput, context);
    }

    @Override
    public AgentResponse answerWithReflection(String userInput, AgentContext context) {
        return dishReActAgent.answerWithReflection(userInput, context);
    }
}
