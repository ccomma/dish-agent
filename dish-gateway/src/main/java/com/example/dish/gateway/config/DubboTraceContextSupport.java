package com.example.dish.gateway.config;

import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.MDC;

/**
 * Dubbo traceId 透传支持。
 */
public final class DubboTraceContextSupport {

    public static final String TRACE_ID_KEY = "traceId";

    private DubboTraceContextSupport() {
    }

    public static void attachCurrentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            RpcContext.getServiceContext().setAttachment(TRACE_ID_KEY, traceId);
        }
    }
}
