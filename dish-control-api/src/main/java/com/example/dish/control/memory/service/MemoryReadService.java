package com.example.dish.control.memory.service;

import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;

/**
 * 记忆召回查询服务契约。
 */
public interface MemoryReadService {

    MemoryReadResult read(MemoryReadRequest request);
}
