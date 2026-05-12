# ADR-004: Graph-based 执行引擎而非纯线性 ReAct

## 状态
提议中（v3.5 计划）

## 背景
当前 v2.x 使用标准 ReAct 循环执行多步推理。对于简单查询（单步检索 + 回答）足够，但以下场景受限于线性流程：
- 需要并行查询（同时查库存 + 查订单状态）
- 需要条件分支（检索结果不足 → 换通道重试；退款成功 → 通知用户 + 打印小票）
- 需要人工审批节点挂起和恢复
- 需要嵌套子任务（查退款历史 → 对每笔退款评估是否合理）

## 决策
在现有 `ExecutionPlan(nodes, edges)` 基础上增强为 **Graph-based 执行引擎**，借鉴 LangGraph 的核心思想但不引入 Python 依赖。

## 增强项

| 能力 | 当前 | 目标 |
|------|------|------|
| 边类型 | `ON_SUCCESS`, `ON_FAILURE` | + `ON_RETRY`, `ON_FALLBACK`, `NEEDS_APPROVAL`, `ON_TIMEOUT` |
| 节点类型 | `AGENT_CALL` | + `SUB_PLAN`, `PARALLEL_FANOUT`, `HUMAN_INPUT` |
| 子图 | 不支持 | 一个 plan 节点展开为子 plan |
| 动态路由 | 静态 | 运行时根据结果动态选择边 |

## 替代方案

**直接使用 LangGraph（Python）**：AI Agent 领域最活跃的图执行框架。但在 Java/Spring 项目中引入 Python 服务会带来运维复杂度（两种语言、两套部署、跨语言 RPC），且当前 LangGraph 的图执行能力在 Java 侧可复现核心部分。

**保持纯 ReAct**：简单但无法处理并行和复杂条件分支。

## 后果

- ExecutionPlan 的 node/edge 模型需要扩展
- 需要实现基于运行时状态的动态路由
- SSE 流式推送需支持子图嵌套
- 现有 ReAct 循环作为 ExecutionNode 的一个实现，不废弃
