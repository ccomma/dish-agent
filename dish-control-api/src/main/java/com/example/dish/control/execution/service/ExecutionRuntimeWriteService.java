package com.example.dish.control.execution.service;

import com.example.dish.control.execution.model.ExecutionEventAppendRequest;
import com.example.dish.control.execution.model.ExecutionGraphSnapshotWriteRequest;

/**
 * execution runtime 写服务契约。
 */
public interface ExecutionRuntimeWriteService {

    boolean initialize(ExecutionGraphSnapshotWriteRequest request);

    boolean appendEvent(ExecutionEventAppendRequest request);
}
