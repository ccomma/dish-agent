package com.example.dish.control.memory.service;

import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;

public interface MemoryTimelineService {

    MemoryTimelineResult timeline(MemoryTimelineRequest request);
}
