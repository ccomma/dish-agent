package com.example.dish.service;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.react.ReActEngine;
import com.example.dish.service.support.DishAgentResponseSupport;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 菜品知识 Agent 门面，负责将 ReAct 结果转换为统一响应。
 */
@Component
public class DishReActAgent {

    @Resource
    private DishReActEngine dishReActEngine;
    @Resource
    private DishAgentResponseSupport dishAgentResponseSupport;

    public AgentResponse answer(String userInput, AgentContext context) {
        // 1. 普通问答默认关闭 reflection，只执行一次标准检索流程。
        AgentContext runContext = dishAgentResponseSupport.buildExecutionContext(context, false);
        ReActEngine.ReActResult result = dishReActEngine.execute(userInput, runContext);
        // 2. 把 ReAct 结果包装成统一 AgentResponse。
        return dishAgentResponseSupport.toResponse(result, runContext);
    }

    public AgentResponse answerWithReflection(String userInput, AgentContext context) {
        // 1. 打开 reflection 标记，允许引擎在首次答案不足时自动扩写 query。
        AgentContext runContext = dishAgentResponseSupport.buildExecutionContext(context, true);
        ReActEngine.ReActResult result = dishReActEngine.execute(userInput, runContext);
        // 2. 统一返回给 gateway 聚合层。
        return dishAgentResponseSupport.toResponse(result, runContext);
    }
}
