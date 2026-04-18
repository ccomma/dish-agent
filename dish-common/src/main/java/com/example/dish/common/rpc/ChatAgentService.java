package com.example.dish.common.rpc;

import com.example.dish.common.contract.AgentResponse;

/**
 * 闲聊Agent Dubbo服务接口
 */
public interface ChatAgentService {

    /**
     * 处理闲聊
     *
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @return Agent响应
     */
    AgentResponse chat(String userInput, String sessionId);
}
