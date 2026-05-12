## Product Brief: Agentic RAG 渐进式演进

> 状态：已消费
> 消费日期：2026-05-05
> 吸收去向：
> - `docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md` 中“Phase 6 完成后的后续方向”
> - `docs/adr/ADR-006-agentic-rag.md`
> - `docs/discovery/AGENTIC_RAG_DESIGN.md` 作为未来阶段输入草稿
>
> 本文档是上游探索输入，不是当前执行态文档，也不直接替代当前 phase 的 PRD、技术设计或 handoff。

### Problem Statement

当前 dish-agent 的 RAG pipeline 对所有 query 采用固定流程，简单 query 成本偏高，复杂 query 的检索与推理又不够充分，因此需要探索一种更能按 query 复杂度调节策略的后续演进方向。

### Target Users

- Primary：依赖 `dish-memory` 检索能力的业务 Agent
- Secondary：需要按策略分层观察成本、质量和延迟的平台维护者

### Core Value Proposition

在保持现有基础设施可复用的前提下，让后续检索系统能够根据 query 复杂度决定是否追加策略路由、自反思和多轮检索，从而在质量和成本之间取得更好的平衡。

### Key Decisions Captured

1. Agentic RAG 是后续演进方向，不提前并入当前 Phase 6。
2. 未来策略层应建立在 Phase 6 的多路召回、重排和动态裁剪之上。
3. 后续阶段需要明确反循环保护和策略观测能力。

### Out of Scope For Current Phase

- 不把 Agentic RAG 提前作为当前 phase 的实现目标
- 不把 discovery 结论直接写入 `CURRENT_HANDOFF.md` 作为执行指令

### Parked Questions

- Query 复杂度分类器采用规则、prompt 还是轻量模型
- 各层流量分布与成本阈值如何校准
- 与 GraphRAG、Multi-Agent 协作的边界如何切分
