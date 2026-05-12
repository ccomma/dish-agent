# ADR-006: 渐进式引入 Agentic RAG

## 状态
已接受（Phase 6 完成后的演进方向。2026-05-05 经 product-discover 探索，grill-with-docs 技术验证通过。实现顺序调整为：Phase 6 多路召回 + Cascade Reranking 基础设施就绪后启动。）

## 实施决策（2026-05-05）

基于 product-discover 发散分析，确定执行路径 A：自底向上渐进演进。

**第一阶段：Agentic RAG 三层全部落地（Phase 6 完成后启动）**
- Layer 1（固定 Pipeline）：保持现有 RAG 流程，~70% 流量
- Layer 2（Agent 增强检索）：Query 分类器 + 检索通道选择 + Query 改写重试
- Layer 3（全 Agentic 模式）：退款审核、政策解释、合规检查场景
- 反循环保护：4 层（硬限制 + 循环检测 + 反思质量门禁 + 熔断器）

**第二阶段：GraphRAG 集成（后续）**
- Neo4j 知识图谱 + 向量-图融合检索 + 多跳推理

**第三阶段：Multi-Agent 辩论协作（远期）**
- 多 Agent 投票/辩论 + 仲裁器 + 安全关键场景应用

**详细设计见**：[Agentic RAG 技术设计](../../docs/discovery/AGENTIC_RAG_DESIGN.md)
**实现计划见**：待 Phase 6 完成后创建

## 背景
传统 RAG Pipeline 是固定的（query → embed → retrieve → rerank → generate），对所有 query 一视同仁。但这忽略了 query 类型的巨大差异：
- "宫保鸡丁怎么做" → 固定 pipeline 就够
- "水煮鱼用什么鱼，那个鱼还有什么做法" → 需要多步推理和追加检索
- "上次那个不错的水煮鱼，再加两碗米饭" → 需要查历史 + 消歧 + 下单

## 决策
引入 **Agentic RAG**，让 Agent 自主决策检索策略，但不要一刀切替换所有场景。

## 分层启用

**Layer 1：固定 Pipeline（默认）**
- 适用：精确查找、简单问答（~70% 流量）
- 延迟低，成本低

**Layer 2：Agent 增强检索（按需）**
- 触发条件：Query 被分类为多跳推理或上下文依赖
- Agent 可决策：
  - 检索通道选择
  - Query 改写重试
  - 结果自反思和追加检索
- 额外延迟：+0.5-2s

**Layer 3：全 Agentic 模式（高风险场景）**
- 适用：退款审核、政策解释、合规检查
- Agent 完全控制检索 + 验证 + 生成流程
- 额外延迟：+2-5s，但 Faithfulness 更有保障

## Trade-off 分析

| 维度 | 固定 Pipeline | Agentic RAG |
|------|-------------|------------|
| 延迟 | 低 (< 1s) | 高 (3-8s) |
| 成本 | 低 (1-2 LLM 调用) | 高 (3-6 LLM 调用) |
| 召回率 | 中等 (85-92%) | 高 (95-98%) |
| 复杂 Query | 差 | 好 |
| 运维复杂度 | 低 | 中高 |
| 可解释性 | 低 | 高 |

## 后果

- 需要 Query 复杂度分类器
- LLM 调用次数增加 2-3x（复杂 query）
- 需要更严格的反循环机制（Agent 可能陷入自反思死循环）
- 监控需要区分固定/Agentic 两类的指标
