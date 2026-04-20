package com.example.dish.common.runtime;

/**
 * 执行图节点状态。
 */
public enum ExecutionNodeStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    RETRYING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
