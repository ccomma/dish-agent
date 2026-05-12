# PROJECT_DEVELOPMENT_PLAN.md

## 路线图原则

本路线图维护的是阶段顺序、阶段目标、退出条件和下一阶段前置依赖，不承担日常任务拆解。具体实现顺序由各 phase 的 `IMPLEMENTATION_PLAN.md` 管理。

## 当前总览

| 阶段 | 主题 | 状态 | 结果定位 |
|------|------|------|---------|
| Phase 1 | 微服务底座与 Agent 主链路 | 已完成 | v2.0 |
| Phase 2 | Control Plane 与审批闭环 | 已完成 | v2.1 |
| Phase 3 | 双层记忆与召回基础 | 已完成 | v2.2 |
| Phase 4 | Execution Runtime 与回放 | 已完成 | v2.3 |
| Phase 5 | 全链路可观测与 Trace Bridge | 已完成 | v2.4-v2.5 |
| Phase 6 | RAG 2.0 | 当前规划 / 待实施 | v3.0 |
| Phase 7 | 智能对话引擎 | 后续 | v3.5 |
| Phase 8 | 记忆系统升级 | 后续 | v3.5 |
| Phase 9 | 多模态与知识图谱 | 远期 | v4.0 |
| Phase 10 | 平台化与评测体系 | 远期 | v4.0+ |

## 已完成阶段摘要

### Phase 1: 微服务底座与 Agent 主链路

完成了 Gateway、Agent Cluster、Dubbo RPC、ReAct 主循环等基础能力，建立了平台最初的执行主干。

退出结果：

- 微服务主架构可运行
- Agent 调用链路成型
- 会话与工具调用主路径可验证

### Phase 2: Control Plane 与审批闭环

把 Planner / Policy / Memory 从执行链路中抽出，形成独立控制面。

退出结果：

- 独立 `dish-control-api` 契约层
- 编排预览、策略门禁、审批恢复链路
- 控制台入口成型

### Phase 3: 双层记忆与召回基础

完成 Redis 时间线 + Milvus 向量库的双层记忆结构，并建立召回解释能力。

退出结果：

- 记忆分层写入策略
- 混合检索基础
- 长期知识预热与召回解释

### Phase 4: Execution Runtime 与回放

补齐执行图、事件流、回放与 Mission Control 观测入口。

退出结果：

- Execution Graph 持久化
- Event Stream 与 replay
- DAG 可视化控制台

### Phase 5: 全链路可观测与 Trace Bridge

把指标、追踪和 Grafana drill-down 链接起来，形成平台级观测底座。

退出结果：

- Actuator / Prometheus / Grafana / Tempo 主链路
- Gateway 手工 span
- Mission Control 到 Grafana 的 trace bridge

## 当前阶段：Phase 6 — RAG 2.0

### 阶段目标

把 `dish-memory` 的检索层从“单路向量检索”升级为“多路召回 + 级联重排 + 动态 TopK + 领域化 chunk 策略”的可扩展底座。

### 输入

- 已完成的 Phase 3 记忆与召回基础
- 当前 `dish-memory` 的单路向量检索实现
- 已消费的 Agentic RAG 发现结论，作为 future direction input

### 输出

- 稳定的多通道召回基础设施
- 可回退的融合与重排主链路
- 可被后续 phase 复用的 chunk / metadata / cache / index 能力
- 与 Phase 6 对应的真实 acceptance 证据

### 核心能力

1. BM25 + 向量双路召回
2. RRF 融合
3. 粗排 + 精排 + 动态 TopK
4. 菜单 / 菜谱 / 政策 / FAQ 分类型 chunk 与 metadata
5. 缓存与向量索引优化

### 退出条件

- 主检索链路保持对上兼容
- 双路召回和单路降级都可验证
- 重排与动态 TopK 已接入主链路
- 关键质量与性能指标有真实采样路径
- Phase 6 acceptance 文档能记录真实结果，而非只留模板

### 验收关注点

- Recall / ranking / latency 的验证口径明确
- `MemoryReadService` 调用方无感兼容
- phase 分支、phase package 与 acceptance evidence 保持一致

### 下一阶段前置依赖

Phase 7 或后续 Agentic RAG 相关工作，默认建立在 Phase 6 已提供的检索基础设施之上。

## 后续阶段方向

### Phase 7: 智能对话引擎

目标：把简单问答、多轮澄清、上下文承接和模糊指代处理做成更稳定的对话执行层。

前置依赖：

- 稳定的检索底座
- 更清晰的执行状态与上下文表达

### Phase 8: 记忆系统升级

目标：把记忆从“会话级可检索”推进到“用户级可复用、可治理、可清理”。

前置依赖：

- Phase 3 的基础记忆能力
- Phase 6 的检索质量提升

### Phase 9: 多模态与知识图谱

目标：引入图谱、多跳关系推理和图文知识输入。

前置依赖：

- 更成熟的检索与评测体系
- 对复杂 query 的清晰收益判断

### Phase 10: 平台化与评测体系

目标：把 Agent 生命周期、工具治理、质量评估和多租户能力平台化。

前置依赖：

- 稳定的执行、记忆、检索与观测底座
- 可复用的指标与验收口径

## 关于 Agentic RAG 的位置

Agentic RAG 是 Phase 6 完成后的后续方向，不是当前 phase 的执行内容。现有 discovery 文档已经被吸收为未来规划输入：

- 产品输入：`docs/discovery/PRODUCT_BRIEF.md`
- 探索性技术草稿：`docs/discovery/AGENTIC_RAG_DESIGN.md`
- 长期决策：`docs/adr/ADR-006-agentic-rag.md`

它的定位是“复用 Phase 6 的检索底座，叠加检索决策层”，而不是与 Phase 6 并行混做。

任何关于 Agentic RAG 的进一步扩展，都应先进入新的 phase planning，而不是回写当前 Phase 6 的执行边界。
