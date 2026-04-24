package com.example.dish.common.telemetry;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dubbo Provider tracing 切面。
 */
@Aspect
@Component
public class DubboProviderSpanAspect {

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Around("@annotation(dubboProviderSpan)")
    public Object traceProviderMethod(ProceedingJoinPoint joinPoint, DubboProviderSpan dubboProviderSpan) throws Throwable {
        // 1. 先根据注解和应用名确定当前 provider span 的组件名。
        String component = StringUtils.isNotBlank(dubboProviderSpan.component())
                ? dubboProviderSpan.component()
                : applicationName;
        // 2. 统一创建 span 并执行原始业务方法。
        DubboOpenTelemetrySupport.RpcSpanScope spanScope = DubboOpenTelemetrySupport.openProviderSpan(
                dubboProviderSpan.value(),
                component
        );
        try (spanScope) {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            // 3. 异常时统一记录到 span，再继续抛出给业务层。
            spanScope.recordFailure(throwable);
            throw throwable;
        }
    }
}
