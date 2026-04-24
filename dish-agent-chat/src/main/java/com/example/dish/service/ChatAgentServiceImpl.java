package com.example.dish.service;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.rpc.ChatAgentService;
import com.example.dish.common.telemetry.DubboProviderSpan;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 闲聊 Agent Dubbo 门面。
 * 负责把简单对话请求转交给 ChatModel，并包装成统一 AgentResponse。
 */
@Component
@DubboService(
    interfaceClass = ChatAgentService.class,
    timeout = 30000,
    retries = 0
)
public class ChatAgentServiceImpl implements ChatAgentService {
    @Resource
    private ChatModel chatModel;

    @Override
    @DubboProviderSpan("chat-agent.chat")
    public AgentResponse chat(String userInput, String sessionId) {
        // 1. 先构造最小上下文，保证 gateway 能继续沿用统一 AgentResponse 协议。
        AgentContext context = AgentContext.builder()
                .sessionId(sessionId)
                .build();

        // 2. 直接调用底层 chatModel 完成闲聊回复。
        String response = chatModel.chat(userInput);

        // 3. 转换成统一 AgentResponse，便于网关做结果聚合。
        return AgentResponse.success(response, "ChatAgent", context);
    }
}
