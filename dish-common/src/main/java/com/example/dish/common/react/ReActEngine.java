package com.example.dish.common.react;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.context.AgentContext;

/**
 * ReAct 引擎接口 - 通用封装
 *
 * 实现 Thought → Action → Observation 循环
 */
public interface ReActEngine {

    /**
     * 执行 ReAct 循环
     *
     * @param userInput 用户输入
     * @param context Agent上下文
     * @return ReAct执行结果
     */
    ReActResult execute(String userInput, AgentContext context);

    /**
     * 判断是否应该继续循环
     */
    boolean shouldContinue(ReActState state);

    /**
     * 获取最大迭代次数
     */
    int getMaxIterations();

    /**
     * ReAct 执行结果
     */
    record ReActResult(
        String finalResponse,
        ReActState state,
        boolean success
    ) {}
}
