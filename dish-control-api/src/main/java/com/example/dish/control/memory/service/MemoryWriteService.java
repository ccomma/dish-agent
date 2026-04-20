package com.example.dish.control.memory.service;

import com.example.dish.control.memory.model.MemoryWriteRequest;

public interface MemoryWriteService {

    boolean write(MemoryWriteRequest request);
}
