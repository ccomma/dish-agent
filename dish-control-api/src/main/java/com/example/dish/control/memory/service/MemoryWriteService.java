package com.example.dish.control.memory.service;

import com.example.dish.control.memory.model.MemoryWriteRequest;

/**
 * 记忆写入服务契约。
 */
public interface MemoryWriteService {

    boolean write(MemoryWriteRequest request);
}
