package com.example.dish.control.execution.service;

import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionLatestQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayResult;

public interface ExecutionRuntimeReadService {

    ExecutionGraphViewResult latest(ExecutionLatestQueryRequest request);

    ExecutionGraphViewResult graph(ExecutionGraphQueryRequest request);

    ExecutionReplayResult replay(ExecutionReplayQueryRequest request);
}
