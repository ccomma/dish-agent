package com.example.dish.service.provider;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.DishAgentService;
import com.example.dish.common.telemetry.DubboProviderSpan;
import com.example.dish.service.DishReActAgent;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 菜品知识 Agent Dubbo 门面。
 * 对外暴露标准问答和带反思重试的问答入口，真正的执行流程委托给 DishReActAgent。
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
    @DubboProviderSpan("dish-agent.answer")
    public AgentResponse answer(String userInput, AgentContext context) {
        // 标准问答入口：直接走一次 RAG + ReAct 流程。
        return dishReActAgent.answer(userInput, context);
    }

    @Override
    @DubboProviderSpan("dish-agent.answer_with_reflection")
    public AgentResponse answerWithReflection(String userInput, AgentContext context) {
        // 反思入口：允许在首次检索不足时自动扩写 query 再试一次。
        return dishReActAgent.answerWithReflection(userInput, context);
    }
}
