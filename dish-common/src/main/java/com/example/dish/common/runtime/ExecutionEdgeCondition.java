package com.example.dish.common.runtime;

/**
 * 边触发条件。
 */
public enum ExecutionEdgeCondition {
    ALWAYS,
    ON_SUCCESS,
    ON_FAILURE,
    ON_TIMEOUT,
    ON_APPROVED,
    ON_REJECTED
}
