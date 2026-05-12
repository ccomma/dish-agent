# 记忆系统架构

## 1. 五层记忆模型

```
┌─────────────────────────────────────────────────────────┐
│                    Layer 4: Long-Term Knowledge          │
│                    跨会话、跨用户共享                      │
│                    Milvus 向量库 + Neo4j 知识图谱         │
├─────────────────────────────────────────────────────────┤
│                    Layer 3: User Profile                 │
│                    用户画像（偏好、忌口、历史）             │
│                    DB + 定期更新、90 天 TTL               │
├─────────────────────────────────────────────────────────┤
│                    Layer 2: Session Summary              │
│                    会话摘要（结束后 LLM 生成）             │
│                    Redis + 向量库、跨会话可检索           │
├─────────────────────────────────────────────────────────┤
│                    Layer 1: Short-Term Memory            │
│                    当前会话已确认事实、槽位                 │
│                    Redis Hash, TTL = 会话时长 + 24h      │
├─────────────────────────────────────────────────────────┤
│                    Layer 0: Conversation Buffer          │
│                    最近 N 轮对话（原文）                   │
│                    Redis List, TTL = 会话时长 + 24h      │
└─────────────────────────────────────────────────────────┘
```

## 2. 各层详设

### 2.1 Layer 0: Conversation Buffer

- **存储**：Redis List，按时间顺序保存最近 N 轮消息（原文）
- **Key**：`dish:conv:{sessionId}:buffer`
- **TTL**：会话结束时 + 24 小时
- **容量**：最多保留 20 轮（可配置）
- **用途**：每次 LLM 调用时直接拼入上下文
- **读写**：每次对话追加，每次 LLM 调用读取全部

### 2.2 Layer 1: Short-Term Memory

- **存储**：Redis Hash
- **Key**：`dish:memory:{tenantId}:session:{sessionId}:short`
- **字段**：
  - `committedFacts`：已确认事实 JSON
  - `slots`：当前槽位状态 JSON
  - `taskStack`：任务栈 JSON
  - `lastIntent`：最近一次意图
  - `turnCount`：当前轮次
- **TTL**：会话结束 + 24h
- **用途**：指代消解、槽位填充、上下文补全
- **更新频率**：每轮对话可能更新

### 2.3 Layer 2: Session Summary

- **生成时机**：会话结束（或每 N 轮触发一次）
- **生成方式**：LLM 摘要生成 + 向量化
- **存储**：
  - Redis：`dish:memory:{tenantId}:user:{userId}:summaries` (Sorted Set，按时间)
  - Milvus：向量索引（用于语义检索）
- **摘要模板**：
  ```
  用户在 {门店} 点了 {菜品}，偏好 {口味}，
  特别要求 {备注}。会话结果：{完成/未完成}。
  ```
- **检索**："上次那个水煮鱼" → 向量检索 → 命中最近一次含水煮鱼的摘要
- **TTL**：30 天（可配置）

### 2.4 Layer 3: User Profile

- **存储**：关系数据库 + Redis 缓存
- **内容**：
  ```json
  {
    "userId": "U-12345",
    "preferences": {
      "favoriteCuisines": ["川菜", "湘菜"],
      "spiceLevel": 2,
      "allergies": ["花生"],
      "usualDishes": ["水煮鱼", "宫保鸡丁"],
      "usualStore": "STORE-001"
    },
    "stats": {
      "totalOrders": 47,
      "avgOrderAmount": 86.50,
      "lastOrderTime": "2026-05-01T12:30:00Z"
    },
    "updatedAt": "2026-05-01T12:35:00Z"
  }
  ```
- **更新策略**：每次订单完成后异步更新
- **TTL**：90 天无活动 → 标记冷数据，180 天 → 删除
- **隐私**：脱敏存储，不含 PII（手机号、姓名等）

### 2.5 Layer 4: Long-Term Knowledge

- **存储**：Milvus 向量库 + Neo4j 知识图谱
- **内容**：菜品知识、食安政策、操作规范、FAQ
- **更新**：知识文件更新 → 重新索引（通过 classpath 预热或管理接口）
- **检索**：多路召回融合（BM25 + 向量 + 实体 + Graph）
- **当前实现**：`LongTermMemoryVectorStore` + `LongTermMemoryDocumentAssembler`

## 3. 跨 Agent 记忆（Central Memory Bus）

### 3.1 设计动机

```
场景：用户在 A 门店点餐 → 去 B 门店时说"和上次一样"
问题：B 门店的 Agent 看不到 A 门店的记忆
方案：Central Memory Bus 以 userId 为索引跨 session 共享记忆
```

### 3.2 架构

```
┌─────────────────────────────────────────────┐
│        Central Memory Service                │
│        (dish-memory 扩展)                     │
│                                              │
│  User-level API:                             │
│  - GET  /memory/user/{userId}/profile        │
│  - GET  /memory/user/{userId}/history?k=10   │
│  - PUT  /memory/user/{userId}/preferences    │
│  - POST /memory/user/{userId}/search         │
│                                              │
│  Tenant-level isolation:                     │
│  - 品牌 A 的用户数据对品牌 B 不可见            │
│  - 同一品牌下不同门店共享用户画像               │
└─────────────────────────────────────────────┘
        ↑              ↑              ↑
    Gateway A      Gateway B      Gateway C
   (门店1)         (门店2)         (外卖)
```

## 4. 记忆生命周期

```
┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│ Conversation │ →  │    Session    │ →  │    User      │
│   Buffer     │    │   Summary     │    │   Profile    │
│              │    │               │    │              │
│ TTL: 24h     │    │ TTL: 30d     │    │ TTL: 90d     │
│              │    │               │    │              │
│ 会话结束触发   │    │ 会话结束触发    │    │ 订单完成触发   │
│ 过期删除      │    │ LLM 生成摘要   │    │ 定期更新      │
└──────────────┘    └───────────────┘    └──────────────┘
                                           │
                                    ┌──────▼──────┐
                                    │   Long-Term │
                                    │  Knowledge  │
                                    │             │
                                    │ TTL: 永久    │
                                    │             │
                                    │ 人工审核更新  │
                                    └─────────────┘
```

## 5. 记忆检索策略

### 5.1 分层检索

```
query → 意图分析 → 需要什么类型的记忆？
  ↓
  ├─ 当前会话上下文 → Layer 0 + Layer 1（确定性读取）
  ├─ 用户偏好/历史 → Layer 3（Key-Value 读取）
  ├─ 跨会话经验 → Layer 2（向量检索）
  └─ 领域知识 → Layer 4（多路召回）

最终融合：按相关性 + 时效性 + 类型权重 排序
```

### 5.2 召回融合公式（当前实现）

```
final_score = keyword_weight × keyword_score
            + vector_weight × vector_similarity
            + recency_weight × recency_decay

recency_decay = 1 / (1 + days_since_creation / 30)
```

### 5.3 可解释召回

每个命中的记忆都附带解释：
```
"layer=LONG_TERM_KNOWLEDGE, source=milvus:dish_memory_long_term,
 keyword=0.480, vector=0.970, recency=0.060"
```

方便控制台排查和面试展示。

## 6. 隐私与合规

| 策略 | 实现 |
|------|------|
| 数据最小化 | 仅存储业务必需的记忆字段 |
| 用户可控 | 用户可请求删除自己的记忆数据 |
| 脱敏存储 | 手机号、身份证等 PII 不进入记忆层 |
| 审计日志 | 所有记忆访问记录 traceId + 时间 + 操作人 |
| 租户隔离 | 不同品牌的记忆数据物理/逻辑隔离 |
| 过期清理 | 各层严格 TTL，过期自动清理 |
