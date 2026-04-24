package com.example.dish.gateway.service;

/**
 * execution 恢复/取消服务接口。
 */
public interface ExecutionResumeService {

    void resumeApprovedExecution(String storeId, String sessionId, String executionId, String traceId);

    void rejectExecution(String storeId, String sessionId, String executionId, String traceId, String reason);
}
