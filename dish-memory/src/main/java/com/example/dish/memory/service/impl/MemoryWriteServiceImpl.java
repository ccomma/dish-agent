package com.example.dish.memory.service.impl;

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

    @Override
    public boolean write(MemoryWriteRequest request) {
        if (request == null
                || request.content() == null
                || request.content().isBlank()
                || request.tenantId() == null
                || request.tenantId().isBlank()
                || request.sessionId() == null
                || request.sessionId().isBlank()) {
            return false;
        }

        MemoryReadServiceImpl.append(
                memoryMode,
                redisTemplate,
                vectorDim,
                request.tenantId(),
                request.sessionId(),
                request.memoryType(),
                request.content(),
                request.metadata(),
                request.traceId()
        );
        return true;
    }
}
