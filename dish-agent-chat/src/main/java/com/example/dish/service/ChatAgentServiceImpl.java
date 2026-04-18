package com.example.dish.service;

import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.rpc.ChatAgentService;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 闲聊Agent Dubbo服务实现
 */
@Component
@DubboService(
    interfaceClass = ChatAgentService.class,
    timeout = 30000,
    retries = 0
)
public class ChatAgentServiceImpl implements ChatAgentService {

    private static final String TRACE_ID_KEY = "traceId";

    @Resource
    private ChatModel chatModel;

    @Override
    public AgentResponse chat(String userInput, String sessionId) {
        withTraceFromAttachment();
        try {
            AgentContext context = AgentContext.builder()
                    .sessionId(sessionId)
                    .build();

            String response = chatModel.chat(userInput);

            return AgentResponse.success(response, "ChatAgent", context);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private void withTraceFromAttachment() {
        String traceId = RpcContext.getServiceContext().getAttachment(TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }
}
