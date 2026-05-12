# Phase 6 Test Plan: RAG 2.0

## 测试目标

验证 Phase 6 在四个维度上成立：

1. 功能正确：双路召回、融合、重排、动态 TopK、chunk 策略和缓存都按预期工作
2. 契约稳定：`MemoryReadService` 对外接口兼容
3. 质量可量化：召回与排序指标有可复现的验证路径
4. 性能可接受：延迟和降级行为可测

## 测试范围

### 单元测试

- RRF 融合计算
- 多通道编排降级逻辑
- 粗排 / 精排 / 动态 TopK 决策
- 各类 chunker 与 metadata 富化
- cache key、TTL、命中逻辑

### 集成测试

- Elasticsearch 与向量通道双路运行
- Memory 检索主链路接入多通道后行为稳定
- 元数据过滤检索
- 缓存开启后的读链路

### 回归测试

- `dish-memory` 现有测试持续通过
- 依赖 memory 检索的 `dish-agent-dish` 主链路不被破坏
- 单路降级时仍保持可用

## 固定样例策略

### 语料样本

- 菜单类文档
- 菜谱类文档
- 政策 / 食安类文档
- FAQ 类文档
- 覆盖精确匹配、模糊匹配、多跳需求的 query 集

### 外部依赖

- Elasticsearch：优先使用 Testcontainers
- Redis：缓存相关测试使用容器或等效可控环境
- Milvus：按现有项目测试能力决定是否走容器集成，至少保留兼容性回归路径

## 测试矩阵

| 能力 | 单元 | 集成 | 回归重点 |
|------|------|------|---------|
| Vector 通道抽象化 | 是 | 是 | 旧链路不变 |
| BM25 通道 | 是 | 是 | ES 不可用降级 |
| RRF 融合 | 是 | 是 | 去重与排序稳定 |
| 粗排 / 精排 | 是 | 是 | 精排失败回退 |
| 动态 TopK | 是 | 是 | 极端分布下稳定 |
| Chunk / Metadata | 是 | 是 | 入库与过滤一致 |
| Cache | 是 | 是 | TTL 与 freshness |

## 验证命令

```bash
# Phase 6 主验证
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false

# 联动回归
mvn test -pl dish-agent-dish,dish-memory -am -s settings-test.xml -DfailIfNoTests=false

# 收尾全量验证
mvn test -s settings-test.xml
```

## 风险点与额外关注

1. 双路召回引入后，延迟可能先变差，需结合缓存与裁剪一起评估
2. chunk / metadata 改造可能影响已有知识入库行为，需要保留回归样本
3. 若微调能力在本阶段推进，训练与上线验证要与主 Java 测试面区分记录，避免混入常规单测口径
