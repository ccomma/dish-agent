package com.example.dish.service.provider;

import com.example.dish.common.context.AgentContext;
import com.example.dish.common.contract.AgentResponse;
import com.example.dish.common.rpc.DishAgentService;
import com.example.dish.service.DishReActAgent;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.MDC;
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

    private static final String TRACE_ID_KEY = "traceId";

    @Resource
    private DishReActAgent dishReActAgent;

    @Override
    public AgentResponse answer(String userInput, AgentContext context) {
        withTraceFromAttachment();
        try {
            return dishReActAgent.answer(userInput, context);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @Override
    public AgentResponse answerWithReflection(String userInput, AgentContext context) {
        withTraceFromAttachment();
        try {
            return dishReActAgent.answerWithReflection(userInput, context);
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
