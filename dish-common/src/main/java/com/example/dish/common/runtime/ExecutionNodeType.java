package com.example.dish.common.runtime;

/**
 * 执行图节点类型。
 */
public enum ExecutionNodeType {
    PLAN,
    TOOL_CALL,
    AGENT_CALL,
    MEMORY_READ,
    MEMORY_WRITE,
    HUMAN_APPROVAL,
    FINALIZE
}
