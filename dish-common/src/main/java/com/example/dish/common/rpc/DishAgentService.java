package com.example.dish.common.rpc;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.context.AgentContext;

/**
 * 菜品知识Agent Dubbo服务接口
 */
public interface DishAgentService {

    /**
     * 回答用户问题
     *
     * @param userInput 用户输入
     * @param context Agent上下文
     * @return Agent响应
     */
    AgentResponse answer(String userInput, AgentContext context);

    /**
     * 带自反思的回答
     *
     * @param userInput 用户输入
     * @param context Agent上下文
     * @return Agent响应
     */
    AgentResponse answerWithReflection(String userInput, AgentContext context);
}
