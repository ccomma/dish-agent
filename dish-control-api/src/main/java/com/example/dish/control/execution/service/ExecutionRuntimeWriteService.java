package com.example.dish.control.execution.service;

import com.example.dish.control.execution.model.ExecutionEventAppendRequest;
import com.example.dish.control.execution.model.ExecutionGraphSnapshotWriteRequest;

public interface ExecutionRuntimeWriteService {

    boolean initialize(ExecutionGraphSnapshotWriteRequest request);

    boolean appendEvent(ExecutionEventAppendRequest request);
}
