package com.example.dish.gateway.service;

public interface ExecutionResumeService {

    void resumeApprovedExecution(String storeId, String sessionId, String executionId, String traceId);

    void rejectExecution(String storeId, String sessionId, String executionId, String traceId, String reason);
}
