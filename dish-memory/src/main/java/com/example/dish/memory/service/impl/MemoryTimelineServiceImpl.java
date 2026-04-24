package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.service.MemoryTimelineService;
import com.example.dish.memory.model.MemoryEntry;
import com.example.dish.memory.storage.MemoryEntryStorage;
import com.example.dish.memory.storage.MemoryTimelineQueryStorage;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆时间线查询门面。
 *
 * <p>该类对外提供会话级和租户级时间线视图，
 * 真正的筛选与取数逻辑委托给时间线查询存储组件。</p>
 */
@Service
@DubboService(interfaceClass = MemoryTimelineService.class, timeout = 5000, retries = 0)
public class MemoryTimelineServiceImpl implements MemoryTimelineService {

    @Autowired
    private MemoryEntryStorage memoryEntryStorage;

    @Autowired
    private MemoryTimelineQueryStorage memoryTimelineQueryStorage;

    @Override
    @DubboProviderSpan("memory.timeline")
    public MemoryTimelineResult timeline(MemoryTimelineRequest request) {
        // 1. 根据当前存储模式查询会话或租户级时间线。
        List<MemoryEntry> entries = memoryTimelineQueryStorage.queryEntries(request);

        // 2. 将内部存储模型转换为 control-api 暴露的时间线 DTO。
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

        // 3. 返回时间线来源和总数，便于 Dashboard 展示。
        return new MemoryTimelineResult(timelineEntries, memoryEntryStorage.source(), timelineEntries.size());
    }
}
