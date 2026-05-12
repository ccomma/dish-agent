# Phase 6 实现计划

## 执行前提

本计划只负责“怎么切实施顺序”，不重复 PRD 的需求边界，也不重复技术设计的架构论证。进入实施前应已具备：

- `docs/prd/phase-06-rag-2.0.md`
- `docs/technical/phase-06-rag-architecture.md`
- `docs/testing/phase-06-rag-2.0-test-plan.md`

## 实施顺序

### M1. 建立可扩展召回骨架

目标：先把现有单路检索改造成可插拔通道模型，不急着一次性接入所有能力。

- 定义 `RetrievalChannel`、统一 `ScoredDocument` / 融合输入输出结构
- 将现有向量检索整理为独立通道实现
- 保持 `MemoryReadService` Dubbo 契约不变
- 为后续 RRF、重排、动态 TopK 预留编排入口

完成信号：
- 现有向量检索链路回归通过
- 调用方无接口改动

### M2. 接入 BM25 与双路融合

目标：在不破坏现有行为的前提下，引入 Elasticsearch 通道并实现可降级双路召回。

- 集成 Elasticsearch 客户端与索引管理
- 提供 BM25 查询构建、结构化过滤与索引初始化
- 实现 `RrfFusion`
- 实现多通道编排、超时控制和单路故障降级

完成信号：
- ES 可用时双路召回工作
- ES 不可用时可回退为向量单路

### M3. 落级联重排与动态裁剪

目标：让召回结果从“能取回来”演进到“质量和成本都可控”。

- 粗排：融合通道分数、时效性、业务权重
- 精排：统一 cross-encoder reranker 入口
- 动态 TopK：基于分数分布和上下文预算裁剪结果
- 将级联管道接入 memory 检索主链路

完成信号：
- 新链路可输出稳定的最终候选集
- 延迟预算有可测路径

### M4. 落领域化 chunk 与索引富化

目标：把餐饮语料差异性真正映射到索引和检索层。

- 识别菜单、菜谱、政策、FAQ 等文档类型
- 建立分类型 chunker
- 抽取并写入领域元数据
- 支持按元数据过滤检索

完成信号：
- 知识入库链路支持按类型处理
- 检索层可消费元数据约束

### M5. 落缓存、索引优化与评测闭环

目标：为 Phase 6 收尾时的性能和回归证据做准备。

- Query Embedding Cache / Semantic Cache
- Milvus 索引参数与分区策略优化
- 离线评测与性能采样脚手架
- 补齐 acceptance 所需的命令、结果采样方式与指标口径

完成信号：
- 可稳定运行最小验证链路
- acceptance 有真实采集路径，而非纯占位

## 依赖关系

- `M2` 依赖 `M1`
- `M3` 依赖 `M1 + M2`
- `M4` 可在 `M2` 后并行推进，但接入主链路前需对齐 `M3`
- `M5` 依赖 `M2 + M3`，并部分复用 `M4`

## 每个里程碑的最小验证

```bash
# M1 / M2 / M3 优先
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false

# 涉及 dish-agent-dish 检索联动时
mvn test -pl dish-agent-dish,dish-memory -am -s settings-test.xml -DfailIfNoTests=false

# 收尾前
mvn test -s settings-test.xml
```

## 实施注意事项

1. Phase 6 是检索基础设施升级，不提前掺入 Agentic RAG 决策逻辑。
2. `IMPLEMENTATION_PLAN.md` 若发生调整，只更新执行切片和顺序，不把长期设计判断搬进来。
3. 若中途发现当前 scope 已突破 Phase 6 边界，应先回写 PRD / 技术设计 / roadmap，再调整本计划。
