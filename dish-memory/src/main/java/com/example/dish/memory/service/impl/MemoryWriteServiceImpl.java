package com.example.dish.memory.service.impl;

import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import com.example.dish.control.memory.service.MemoryWriteService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@DubboService(interfaceClass = MemoryWriteService.class, timeout = 5000, retries = 0)
public class MemoryWriteServiceImpl implements MemoryWriteService {

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Value("${memory.retrieval.vector-dim:128}")
    private int vectorDim = 128;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private LongTermMemoryVectorStore longTermMemoryVectorStore;

    @Override
    public boolean write(MemoryWriteRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("memory.write", "dish-memory");
        try (spanScope) {
            if (request == null
                    || request.content() == null
                    || request.content().isBlank()
                    || request.memoryLayer() == null
                    || request.tenantId() == null
                    || request.tenantId().isBlank()
                    || request.sessionId() == null
                    || request.sessionId().isBlank()) {
                return false;
            }

            if (request.memoryLayer() == com.example.dish.control.memory.model.MemoryLayer.LONG_TERM_KNOWLEDGE) {
                if (longTermMemoryVectorStore == null) {
                    return false;
                }
                longTermMemoryVectorStore.index(request);
            }

            MemoryReadServiceImpl.append(
                    memoryMode,
                    redisTemplate,
                    vectorDim,
                    request.tenantId(),
                    request.sessionId(),
                    request.memoryLayer(),
                    request.memoryType(),
                    request.content(),
                    request.metadata(),
                    request.traceId()
            );
            return true;
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }
}
