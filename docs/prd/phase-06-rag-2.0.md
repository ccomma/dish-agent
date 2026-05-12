# PRD: Phase 6 — RAG 2.0 多路召回与智能检索

## Why now

当前检索链路以“单路向量召回 + 单一重排”为主，已经不足以支撑 `dish-agent` 下一阶段的质量目标：

- 精确菜名、食材名、政策条款等 query 仍被迫走语义向量主路径
- 多跳或复杂 query 的召回质量不稳定
- 检索结果质量与延迟成本缺少更细粒度的调度空间

Phase 6 的任务不是直接做 Agentic RAG，而是先把未来会复用的检索底座补齐。

## Users

### Primary users

- `dish-agent-dish` 与 `dish-agent-workorder` 的检索调用链
- 负责 `dish-memory` 检索质量的研发与运维人员

### Non-target users

- `dish-agent-chat` 这类不依赖垂直知识检索的闲聊路径

## Problem statement

系统当前缺少：

1. 精确匹配与语义匹配并行存在的召回体系
2. 召回结果的层次化排序与裁剪能力
3. 与餐饮语料特征对齐的 chunk / metadata 策略
4. 可支撑后续更复杂检索决策层的基础设施

## Goals

1. 建立双路召回基础设施：向量 + BM25
2. 用 RRF 实现稳定融合，并保留单路故障降级能力
3. 建立粗排 + 精排 + 动态 TopK 的质量控制链路
4. 让知识切分和索引富化具备领域感知能力
5. 为性能和成本目标增加缓存与索引优化手段

## Out of scope

- Agentic RAG 三层自主策略路由
- GraphRAG / 知识图谱
- 多模态检索
- 跨 Agent 的共享检索治理机制

## Success metrics

| 指标 | 当前基线 | Phase 6 目标 |
|------|---------|-------------|
| Recall@5 | ~85% | >95% |
| MRR | ~0.75 | >0.85 |
| NDCG@5 | ~0.72 | >0.82 |
| 检索 P95 | ~500ms | <200ms |
| Faithfulness | ~0.85 | >0.90 |

## Product requirements

### R1. 双路召回

- 系统必须支持至少两种可编排召回通道
- 第一批通道为向量检索与 BM25 检索
- 单一通道异常时，系统必须可降级

### R2. 融合与排序

- 召回结果需要经过统一融合
- 默认融合策略采用 RRF
- 主链路需要支持粗排、精排与动态裁剪

### R3. 领域化知识处理

- 入库链路需识别不同文档类型
- 分类型 chunk 策略与元数据富化要能被检索链路消费

### R4. 兼容性

- `MemoryReadService` 对外 Dubbo 契约不变
- 调用方不需要知道底层是否启用了多路召回

### R5. 可演进性

- Phase 6 的通道、排序与裁剪能力，要能被后续 Agentic RAG 复用
- 但后续阶段能力不能提前侵入当前 phase 范围

## Constraints

- 技术主栈保持 Java / Spring / Dubbo 体系
- Elasticsearch 作为 BM25 主方案候选
- 现有 memory 检索链路必须可回归验证

## Open questions

- Elasticsearch 在本项目中的部署方式与索引维护成本如何控制
- Embedding 微调是否在本阶段就值得投入完整训练闭环
- Phase 6 的离线评测集如何沉淀为后续阶段可复用资产
