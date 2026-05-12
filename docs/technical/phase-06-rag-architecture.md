# Phase 6 Technical Design: RAG 2.0

## 设计目标

本设计只覆盖 Phase 6 的检索基础设施升级，不承担 Agentic RAG、GraphRAG 或多模态阶段的详细设计。

目标是把当前检索主链路改造成：

```text
query
  -> multi-channel retrieval
  -> fusion
  -> cascade reranking
  -> dynamic top-k
  -> final retrieval context
```

## 现状与缺口

当前主链路以单路向量检索为中心，问题集中在三类：

1. 精确匹配不足：菜名、食材、政策条款等 query 无法利用关键词通道优势
2. 质量控制不足：召回后缺少统一融合和层级重排
3. 语料建模不足：菜单、菜谱、FAQ、政策文档的切分方式混在一起

## 架构范围

### In scope

- 多通道召回抽象
- BM25 与向量双路
- RRF 融合
- 粗排 + 精排 + 动态 TopK
- 领域化 chunk 和 metadata 富化
- 缓存与向量索引优化

### Out of scope

- Query 复杂度分类器
- Agent 自主改写 query / 反思追加检索
- GraphRAG
- 跨 Agent 检索流量治理

## 主链路设计

### 1. 召回通道抽象

新增统一通道边界，使不同召回方式都能进入同一编排层。

```java
public interface RetrievalChannel {
    List<ScoredDocument> retrieve(RetrievalQuery query);
}
```

设计要求：

- 通道输出统一分数与来源信息
- 通道层不直接承担最终排序
- 通道异常要能被编排层感知并降级

### 2. 多通道编排

第一阶段支持：

- `VectorRetrievalChannel`
- `Bm25RetrievalChannel`

编排层职责：

1. 并行调用通道
2. 处理超时与异常
3. 对结果去重
4. 将结果交给融合层

### 3. 融合策略

Phase 6 默认使用 RRF：

```text
score = Σ 1 / (k + rank_i), k = 60
```

原因：

- 不依赖不同通道分数归一化
- 对分布差异更稳健
- 初始调参成本较低

### 4. 级联重排

重排分两层：

- 粗排：使用通道分数、时效性、业务权重等轻量信号缩小候选集
- 精排：使用 cross-encoder 类 reranker 对少量候选做更精细排序

设计要求：

- 粗排与精排应解耦
- 精排只处理小规模候选集
- 若精排不可用，系统仍可回退到粗排结果

### 5. 动态 TopK

动态 TopK 根据以下因素决定最终保留数量：

- 分数断崖
- 分数分布离散程度
- query 类型预设范围
- context token budget

目标不是“永远更少”，而是避免：

- 明显无关结果被保留
- 明显相关结果因固定 K 被截断

## 语料与索引设计

### 文档类型

Phase 6 先覆盖：

- 菜单 / 菜品介绍
- 菜谱 / 制作步骤
- 食安 / 政策类文档
- FAQ

### Chunk 策略

| 类型 | 策略 | 设计意图 |
|------|------|----------|
| 菜单 | 按菜品边界切分 | 保持菜品信息完整 |
| 菜谱 | 父子文档或语义切分 | 兼顾步骤定位与上下文完整性 |
| 政策 | 按条款编号切分 | 保持规则引用精确 |
| FAQ | 一问一答 | 保持问答完整性 |

### Metadata 富化

优先支持下列餐饮领域字段：

- 菜系
- 口味 / 辣度
- 食材
- 价格区间
- 文档类型
- 适用门店 / 租户

这些 metadata 将用于：

- 召回前过滤
- 粗排附加权重
- 后续可解释性展示

## 缓存与性能设计

### Query Embedding Cache

- 目标：避免相同 query 重复算 embedding
- 位置：检索入口之前

### Semantic Cache

- 目标：高相似 query 直接复用已验证结果
- 注意：必须有 TTL 与 freshness 约束

### 向量索引优化

- 继续以 Milvus 为主
- Phase 6 内只做与当前数据量匹配的参数与分区优化
- 不在本阶段引入新的向量数据库类型

## 兼容性与降级

1. `MemoryReadService` 对外接口不变
2. 若 BM25 通道异常，系统可退化为向量单路
3. 若精排异常，系统可退化为粗排或融合结果
4. 若动态 TopK 决策失败，系统可退化为固定 K

## 测试与验证关注点

1. 检索质量回归：Recall@K、MRR、NDCG
2. 链路延迟：双路、重排、缓存开启后的 P95
3. 兼容性：现有调用方行为不崩
4. 故障降级：ES 不可用、精排不可用、缓存不可用时的回退

## 与后续阶段的关系

Phase 6 只提供基础设施，不定义未来策略层实现。后续 `Agentic RAG` 可以复用：

- 多通道召回
- 融合层
- 级联重排
- 动态 TopK
- 领域化 metadata

但 Query 路由、自反思、追加检索等能力，仍属于后续阶段文档管理范围，不在本设计中展开。
