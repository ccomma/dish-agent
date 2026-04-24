package com.example.dish.gateway.observability;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

/**
 * Gateway 执行链路 tracing 支撑。
 * 统一封装 step 执行与恢复场景下的 span 创建、属性补齐和异常记录。
 */
public final class GatewayExecutionTracing {

    private GatewayExecutionTracing() {
    }

    public static SpanScope openExecutionSpan(String spanName,
                                              RoutingDecision routing,
                                              ExecutionGraphViewResult graph,
                                              AgentExecutionStep step,
                                              String traceId) {
        // 1. 创建 gateway 内部 span。
        Span span = GlobalOpenTelemetry.getTracer("dish-agent-gateway")
                .spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        // 2. 补齐 trace / session / execution / step 等业务属性。
        enrich(span, routing, graph, step, traceId);
        return new SpanScope(span.makeCurrent(), span);
    }

    private static void enrich(Span span,
                               RoutingDecision routing,
                               ExecutionGraphViewResult graph,
                               AgentExecutionStep step,
                               String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            span.setAttribute("app.trace_id", traceId);
        }
        if (routing != null && routing.context() != null) {
            if (routing.context().getSessionId() != null) {
                span.setAttribute("app.session_id", routing.context().getSessionId());
            }
            if (routing.context().getStoreId() != null) {
                span.setAttribute("app.store_id", routing.context().getStoreId());
            }
        }
        if (routing != null && routing.intent() != null) {
            span.setAttribute("app.intent", routing.intent().name());
        }
        if (routing != null && routing.executionMode() != null) {
            span.setAttribute("app.execution_mode", routing.executionMode());
        }
        if (graph != null) {
            if (graph.executionId() != null) {
                span.setAttribute("app.execution_id", graph.executionId());
            }
            if (graph.planId() != null) {
                span.setAttribute("app.plan_id", graph.planId());
            }
        }
        if (step != null) {
            if (step.stepId() != null) {
                span.setAttribute("app.step_id", step.stepId());
            }
            if (step.targetAgent() != null) {
                span.setAttribute("app.target_agent", step.targetAgent());
            }
            if (step.nodeType() != null) {
                span.setAttribute("app.node_type", step.nodeType());
            }
        }
    }

    public static final class SpanScope implements AutoCloseable {

        private final Scope scope;
        private final Span span;

        private SpanScope(Scope scope, Span span) {
            this.scope = scope;
            this.span = span;
        }

        public Span span() {
            return span;
        }

        public void recordFailure(Throwable throwable) {
            if (throwable == null) {
                return;
            }
            // 统一把异常记录到 span，方便 Tempo/Grafana 中直接定位失败原因。
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
