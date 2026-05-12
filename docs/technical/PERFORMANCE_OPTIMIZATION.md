# 性能优化策略

> **类型**：跨阶段系统级文档。覆盖 Phase 5（可观测性）、Phase 6（检索延迟）、Phase 7（LLM 推理优化）、Phase 8（缓存策略）等阶段的性能目标。各阶段实现时按需引用和更新。

## 1. 全链路延迟预算

```
端到端目标: P50 < 2s, P95 < 8s

延迟分配（简单查询 / 复杂多步）：
┌─────────────────────┬──────────┬──────────┐
│ 环节                 │ 简单查询  │ 复杂多步  │
├─────────────────────┼──────────┼──────────┤
│ 意图识别 (LLM)       │  300ms   │  500ms   │
│ 检索 (多路召回)       │  150ms   │  250ms   │
│ Reranker             │   80ms   │  100ms   │
│ 策略评估              │    5ms   │   10ms   │
│ ReAct 推理 (LLM×N)   │  500ms   │  4000ms  │
│ 工具调用 (外部API)    │    0ms   │  2000ms  │
│ 记忆读写              │   30ms   │   50ms   │
│ 结果聚合              │   20ms   │   30ms   │
├─────────────────────┼──────────┼──────────┤
│ 合计 (目标)           │ < 1.2s   │  < 7.0s  │
└─────────────────────┴──────────┴──────────┘
```

## 2. LLM 推理优化

### 2.1 Semantic Cache

```
策略：相似 query 直接返回缓存回答

实现：
  1. 新 query → 计算 embedding
  2. 在缓存中搜索相似 query（cosine > 0.98）
  3. 命中 → 直接返回缓存回答（延迟 < 5ms）
  4. 未命中 → 正常执行 → 结果写入缓存

存储：Redis, TTL = 1h, max_entries = 10000

适用场景：高频重复查询（"宫保鸡丁是什么"、"今天的特价菜"）
不适用：实时数据查询（"我的订单状态"、"今天还有几份水煮鱼"）
```

### 2.2 模型选择分层

```
意图类型 → 模型选择：
  GREETING, GENERAL_CHAT    → 轻量模型 (qwen-turbo, ~300ms)
  DISH_QUESTION             → 标准模型 (minimax-m2.7, ~500ms)
  QUERY_ORDER, INVENTORY    → 标准模型 (minimax-m2.7, ~500ms)
  CREATE_REFUND (复杂推理)   → 强模型 (deepseek-v3, ~800ms)
  POLICY_QUESTION (高风险)   → 强模型 (deepseek-v3, ~800ms)
```

### 2.3 Streaming

- 所有 LLM 调用默认开启 Streaming
- 首 token 到达即开始推送给用户
- 降低感知延迟：虽然总耗时不变，但用户 200ms 就看到第一个字

## 3. 检索优化

### 3.1 多级缓存

```
L1 - Embedding Cache（进程内存）:
  Query → MD5 → Embedding Vector
  命中率 ~40%，延迟 < 1ms

L2 - 结果缓存（Redis）:
  Query Embedding(量化) → 检索结果 ID 列表
  TTL = 10min, 命中率 ~15%

L3 - 热点文档预加载（Redis）:
  高频菜品的 chunk embedding 预缓存在 Redis
  检索时直接从缓存读取，跳过 Milvus
```

### 3.2 并行化

```
多路召回并行执行：
  ┌─ BM25 (ES) ──────────┐
  ├─ Vector (Milvus) ────┤
  ├─ Entity Filter ──────┤
  └─ SPLADE (可选) ──────┘
         │
    并行等待 → 聚合 → RRF 融合 → 粗排

总延迟 = max(各路延迟) + 聚合开销 ≈ max(10ms, 50ms, 5ms) + 5ms ≈ 55ms
（远优于串行 10+50+5 = 65ms + 额外开销）
```

### 3.3 Reranker 加速

```
- ONNX Runtime 推理（比 PyTorch 快 2-3x）
- 批处理：累积 10ms 内的请求，batch 推理
- 模型量化：FP16 或 INT8（精度损失 < 1%）
```

## 4. 工具调用优化

### 4.1 连接池

```yaml
# HTTP 工具调用
http:
  max_connections: 200
  max_connections_per_route: 50
  connect_timeout: 1000ms
  read_timeout: 5000ms
  keep_alive: 60s
  http2: true  # 多路复用

# Dubbo 工具调用
dubbo:
  connections: 10  # 单 host 连接数
  timeout: 5000
  retries: 0       # 幂等性不确定，不自动重试
```

### 4.2 超时与降级

```
工具调用超时策略：
  - 查询类: 3s 超时 → 返回上次缓存结果
  - 创建类: 5s 超时 → 返回 pending 状态，异步确认
  - 修改类: 5s 超时 → 返回失败 + 建议重试

降级策略：
  - 工具不可用 → 返回预设降级文案
  - 工具超时 → 返回部分结果 + "结果不完整"提示
  - 工具返回异常 → 返回错误信息 + 建议人工处理
```

### 4.3 预取

```
会话开始时预取：
  - 用户画像
  - 当前门店菜单
  - 用户最近 5 笔订单

预取数据存在 Redis：
  key: dish:prefetch:{sessionId}
  TTL: 会话时长
```

## 5. 数据库与存储优化

### 5.1 连接池配置

```yaml
# Redis (Lettuce)
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 2000ms

# Milvus
milvus:
  max_idle_connections: 10
  keep_alive_time_ms: 30000
  client_connect_timeout_ms: 5000
```

### 5.2 Pipeline 与批处理

```java
// Redis Pipeline 批量写入
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (MemoryEntry entry : entries) {
        connection.stringCommands().set(
            key(entry).getBytes(),
            encode(entry)
        );
    }
    return null;
});
```

## 6. 容量规划

### 6.1 资源估算

```
假设：1000 门店 × 100 单/天 = 100,000 请求/天

峰值 QPS（午餐/晚餐时段）：
  100,000 × 0.3 / (2 × 3600) ≈ 4 QPS (平均)
  峰值 QPS ≈ 4 × 3 = 12 QPS

资源需求（单实例）：
  Gateway: 2 CPU / 4GB RAM
  Agent (dish): 2 CPU / 4GB RAM
  Agent (workorder): 1 CPU / 2GB RAM
  Agent (chat): 1 CPU / 2GB RAM
  Planner: 1 CPU / 1GB RAM
  Policy: 1 CPU / 1GB RAM
  Memory: 2 CPU / 4GB RAM
  Milvus: 4 CPU / 8GB RAM
  Redis: 2 CPU / 4GB RAM
  ES: 2 CPU / 4GB RAM
  
总计：~18 CPU / 34GB RAM (基础)
加 50% 余量：~27 CPU / 51GB RAM
```

### 6.2 水平扩展

```
无状态服务（随意扩展）：
  - Gateway
  - Agent (dish/chat)
  - Planner
  - Policy

有状态服务（需集群方案）：
  - dish-agent-workorder (依赖后端连接池 → 连接数×实例数)
  - Memory (依赖 Redis/Milvus → 自身无状态)
  - Redis → Sentinel / Cluster
  - Milvus → 读写分离 / 分区
```

## 7. 压测与验证

### 7.1 压测场景

```
场景 1: 简单菜品查询 (QPS=10, 持续 5min)
  → 目标: P95 < 1s, 成功率 > 99%

场景 2: 复杂退款流程 (QPS=2, 持续 10min)
  → 目标: P95 < 5s, 成功率 > 95%

场景 3: 混合场景 (简单70% + 中等20% + 复杂10%, QPS=5, 持续 30min)
  → 目标: 综合 P95 < 3s, 成功率 > 97%

场景 4: 突发流量 (QPS 从 5 突增到 20, 持续 2min)
  → 目标: 成功率 > 90%，不允许雪崩
```

### 7.2 压测工具

```bash
# 使用 k6 或 JMeter
k6 run --vus 10 --duration 5m scripts/dish-query-test.js
```
