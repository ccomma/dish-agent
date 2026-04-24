package com.example.dish.common.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dubbo OpenTelemetry 支撑工具。
 * 负责上下文注入、Provider 侧 trace 恢复和 span 创建。
 */
public final class DubboOpenTelemetrySupport {

    public static final String TRACE_ID_KEY = "traceId";

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    private DubboOpenTelemetrySupport() {
    }

    public static void injectCurrentContext() {
        // 1. 把当前 OpenTelemetry 上下文注入到 Dubbo attachment。
        Map<String, String> carrier = new LinkedHashMap<>();
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), carrier, MAP_SETTER);
        carrier.forEach((key, value) -> RpcContext.getServiceContext().setAttachment(key, value));

        // 2. 再把 MDC 里的业务 traceId 显式补到 attachment，兼容日志链路约定。
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            RpcContext.getServiceContext().setAttachment(TRACE_ID_KEY, traceId);
        }
    }

    public static RpcSpanScope openProviderSpan(String spanName, String componentName) {
        // 1. 从 Dubbo attachment 中提取传播上下文。
        Map<String, ?> attachments = RpcContext.getServiceContext().getAttachments();
        Map<String, String> carrier = new LinkedHashMap<>();
        if (attachments != null) {
            attachments.forEach((key, value) -> carrier.put(key, value != null ? String.valueOf(value) : null));
        }
        Context extracted = GlobalOpenTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), carrier, MAP_GETTER);

        // 2. 恢复业务 traceId 到 MDC，便于 provider 日志和 span 统一关联。
        String traceId = RpcContext.getServiceContext().getAttachment(TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }

        // 3. 创建 Provider span，并补齐 rpc/system/component 等属性。
        Span span = GlobalOpenTelemetry.getTracer("dish-agent")
                .spanBuilder(spanName)
                .setParent(extracted)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("rpc.system", "dubbo")
                .setAttribute("app.component", componentName)
                .setAttribute("app.trace_id", traceId != null ? traceId : "unknown")
                .startSpan();
        Scope scope = span.makeCurrent();
        return new RpcSpanScope(scope, span, traceId != null && !traceId.isBlank());
    }

    public static final class RpcSpanScope implements AutoCloseable {

        private final Scope scope;
        private final Span span;
        private final boolean clearMdcOnClose;

        private RpcSpanScope(Scope scope, Span span, boolean clearMdcOnClose) {
            this.scope = scope;
            this.span = span;
            this.clearMdcOnClose = clearMdcOnClose;
        }

        public Span span() {
            return span;
        }

        public void recordFailure(Throwable throwable) {
            if (throwable == null) {
                return;
            }
            // 统一记录异常信息，便于跨服务排查。
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
        }

        @Override
        public void close() {
            scope.close();
            span.end();
            if (clearMdcOnClose) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }
}
