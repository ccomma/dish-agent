package com.example.dish.control.memory.service;

import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;

public interface MemoryReadService {

    MemoryReadResult read(MemoryReadRequest request);
}
