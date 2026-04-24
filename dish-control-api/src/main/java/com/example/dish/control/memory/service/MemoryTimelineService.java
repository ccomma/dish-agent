package com.example.dish.control.memory.service;

import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;

/**
 * 记忆时间线查询服务契约。
 */
public interface MemoryTimelineService {

    MemoryTimelineResult timeline(MemoryTimelineRequest request);
}
