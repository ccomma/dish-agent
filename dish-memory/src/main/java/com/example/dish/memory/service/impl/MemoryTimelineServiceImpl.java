package com.example.dish.memory.service.impl;

import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.service.MemoryTimelineService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService(interfaceClass = MemoryTimelineService.class, timeout = 5000, retries = 0)
public class MemoryTimelineServiceImpl implements MemoryTimelineService {

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public MemoryTimelineResult timeline(MemoryTimelineRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("memory.timeline", "dish-memory");
        try (spanScope) {
            List<MemoryReadServiceImpl.MemoryEntry> entries = MemoryReadServiceImpl.queryEntries(memoryMode, redisTemplate, request);
            List<MemoryTimelineEntry> timelineEntries = entries.stream()
                    .map(entry -> new MemoryTimelineEntry(
                            entry.memoryType(),
                            entry.memoryLayer(),
                            entry.content(),
                            entry.metadata(),
                            entry.traceId(),
                            entry.createdAt(),
                            entry.sequence(),
                            entry.storageSource()
                    ))
                    .toList();
            return new MemoryTimelineResult(timelineEntries, MemoryReadServiceImpl.source(memoryMode, redisTemplate), timelineEntries.size());
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }
}
