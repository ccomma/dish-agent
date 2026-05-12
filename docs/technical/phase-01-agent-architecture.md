# Agent 架构深度设计

## 1. 架构总览

```
                     ┌─────────────────────┐
                     │    dish-gateway      │
                     │  意图路由 + 编排调度   │
                     └──────────┬───────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          │                     │                     │
    ┌─────▼──────┐     ┌───────▼───────┐     ┌──────▼───────┐
    │  Planner   │     │    Policy     │     │   Memory     │
    │  DAG 规划   │     │   风险门禁    │     │   记忆服务    │
    └────────────┘     └───────────────┘     └──────────────┘
          │
    ┌─────┼─────────────┐
    │     │             │
┌───▼──┐ ┌▼──────┐ ┌───▼──────┐
│ Dish │ │WorkOrd│ │  Chat    │
│ RAG  │ │+ReAct │ │  Agent   │
│+ReAct│ │       │ │          │
└──────┘ └───────┘ └──────────┘
```

## 2. ReAct 多步推理

### 2.1 标准 ReAct 循环

```
┌─────────────────────────────────────────┐
│              ReAct Loop                  │
│                                          │
│  ┌──────────┐    ┌──────────┐           │
│  │ Thought  │ →  │  Action  │           │
│  │ 分析现状  │    │ 执行操作  │           │
│  └──────────┘    └────┬─────┘           │
│        ↑               │                │
│        │         ┌─────▼─────┐          │
│        │         │Observation│          │
│        └─────────│  观察结果  │          │
│                  └───────────┘          │
│                      │                  │
│                ┌─────▼─────┐           │
│                │  Final?   │──否──→ 继续 │
│                └─────┬─────┘           │
│                      │是                │
│                ┌─────▼─────┐           │
│                │  Answer   │           │
│                └───────────┘           │
└─────────────────────────────────────────┘
```

### 2.2 ReAct 变体

| 变体 | 特点 | 适用场景 |
|------|------|---------|
| **ReAct** | Thought→Action→Observe→循环 | 通用多步推理 |
| **ReWOO** | 规划全部 Action → 并行执行 → 合并 | 独立子任务可并行 |
| **Reflexion** | 执行后反思 → 自我批评 → 重试 | 复杂推理，首次可能出错 |
| **Plan-and-Execute** | 先生成完整计划 → 逐步执行 | 步骤明确的业务流程 |

**当前实现**：标准 ReAct（`AbstractReActEngine`），子类实现 `think()` / `decideAction()` / `executeAction()`。

**推荐演进**：ReWOO 用于可并行的多步查询（如同时查库存+查订单），Reflexion 用于复杂退款审核。

## 3. 反循环机制（Anti-Loop）

### 3.1 四层防护

```
L1 - 硬限制（总在生效）：
  maxSteps = 10
  maxTokens = 8000
  maxDuration = 30s
  任一触发 → 强制终止，返回中间结果 + "部分完成"标记

L2 - 循环检测（在线判断）：
  - 连续 3 步 action 和 observation 高度相似
    → 用 embedding 计算 cos(action[i], action[i-1]) > 0.95
  - Observation 无实质进展（如重复"未找到"、"请重试"）
    → 降级策略：简化 query / 更换检索通道 / 直接回答

L3 - 反思节点（每 3 步）：
  插入反思 Prompt：
  "到目前为止我们获取了什么信息？离目标还有多远？
   当前方向对吗？需要调整策略吗？"

L4 - 熔断器（跨请求）：
  同一 session 连续 K 次超限 → 熔断
  → 后续请求降级为简单直接回答（不做 ReAct）
  → 熔断 5 分钟后半开尝试
```

### 3.2 实现细节

```java
// 循环检测伪代码
class LoopDetector {
    private final List<double[]> recentActionEmbeddings;

    boolean isLooping(double[] currentActionEmbedding) {
        for (double[] prev : recentActionEmbeddings) {
            if (cosine(currentActionEmbedding, prev) > 0.95) {
                consecutiveLoopCount++;
            }
        }
        return consecutiveLoopCount >= 3;
    }
}
```

## 4. Graph-based 执行引擎

### 4.1 借鉴 LangGraph 思想

LangGraph 的核心是 **StateGraph + 条件边 + 人机协作**。当前项目的 `ExecutionPlan(nodes, edges)` 已经是图结构，可增强为：

```
当前：
  n1 ──ON_SUCCESS──→ n2 ──ON_SUCCESS──→ n3

增强后：
       n1 ──ON_SUCCESS──→ n2 ──ON_SUCCESS──→ n3
        │                  │                  │
        ├──ON_FAILURE──→ n1-fallback         ├──NEEDS_APPROVAL──→ pause → resume
        │                                     │
        └──ON_RETRY(3)──→ n1                  └──ON_FALLBACK──→ n3-simple
```

### 4.2 增强项

| 增强 | 当前 | 目标 |
|------|------|------|
| **边类型** | `ON_SUCCESS`, `ON_FAILURE` | + `ON_RETRY`, `ON_FALLBACK`, `NEEDS_APPROVAL`, `ON_TIMEOUT` |
| **节点类型** | `AGENT_CALL` | + `SUB_PLAN`（嵌套执行）, `PARALLEL_FANOUT`（并行扇出）, `HUMAN_INPUT`（人机交互） |
| **动态路由** | 静态（编译时确定边） | 支持运行时根据结果动态选边 |
| **子图** | 不支持 | 一个 plan 节点展开为另一个 plan |

### 4.3 为什么不用 LangGraph 本身

- LangGraph 是 Python 生态，与项目 Java/Spring 技术栈不兼容
- 核心思想（状态图、条件边、人机协作）可以直接在现有 ExecutionPlan 上实现
- 当前 DAG + SSE 实现已具备图执行的基础，增强比替换成本低

## 5. 对话状态管理（Dialog State Machine）

### 5.1 状态模型

```java
record DialogState(
    String sessionId,
    int turnCount,
    Instant startedAt,
    Instant lastActivityAt,

    // 槽位填充
    Map<String, Object> slots,       // {dishName: "水煮鱼", quantity: 2, spiciness: "微辣"}

    // 对话阶段
    DialogStage stage,               // INTENT_CLARIFY | DISH_SELECT | ORDER_CONFIRM | ...

    // 任务栈（支持嵌套意图）
    Deque<Task> taskStack,           // 当前任务 + 待处理任务

    // 已确认事实（不可回退）
    Map<String, Object> committedFacts,  // {storeId: "STORE-1", tableId: "TABLE-5"}

    // 待确认假设（可修正）
    List<Assumption> pendingAssumptions, // "上次那个" → 已消歧为"水煮鱼"或未消歧

    // 上下文窗口
    List<Message> contextWindow      // 最近 N 轮对话（摘要 + 原文）
) {}
```

### 5.2 对话阶段流转

```
IDLE → INTENT_CLARIFY → DISH_SELECT → ORDER_CONFIRM → PAYMENT → COMPLETED
  │         │                │              │            │
  └─────────┴────────────────┴──────────────┴────────────┘
                      ↓ 任意阶段
                   TIMEOUT / USER_CANCEL
```

### 5.3 模糊点餐处理示例

```
Input: "上次那道还不错的水煮鱼，再加两碗米饭"

Step 1 - 意图识别: ORDER_FOOD + CONTEXT_DEPENDENT
Step 2 - 指代消解: "上次那个" → 查用户历史订单 → 找到"微辣水煮鱼(中份) ¥68"
Step 3 - 槽位填充:
  slots: {dishName: "水煮鱼", spiciness: "微辣", size: "中份", price: 68}
  slots: {addons: ["米饭 × 2"]}
Step 4 - 歧义确认（如有多个候选）:
  System: "您上次点过水煮鱼(¥68)和宫保鸡丁(¥38)，请问要哪个？"
Step 5 - 订单预览:
  System: "订单确认：微辣水煮鱼(中份) ×1...¥68 + 米饭 ×2...¥4 = ¥72。确认下单吗？"
Step 6 - 确认 → createOrder()
```

## 6. 安全与可控性

### 6.1 多层防护

```
L1 - 输入校验:
  Prompt Injection 检测 / SQL注入检测 / 敏感信息过滤

L2 - 策略引擎 (PolicyGatekeeper):
  操作风险评级: QUERY(LOW) | CREATE_ORDER(MEDIUM) | CREATE_REFUND(HIGH) | DELETE(HIGH)
  RBAC: 服务员不可退款 > ¥200
  阈值: 退款 > ¥500 → 强制审批
  频率: 同一用户 10次/分钟退款 → 熔断

L3 - 输出校验:
  Faithfulness 检查: 回答是否与检索文档一致
  敏感信息脱敏: 手机号、身份证
  业务规则校验: 推荐的菜是否在售

L4 - 审计:
  所有操作写入审计日志（不可变）
  完整 traceId 链路可追溯
```

### 6.2 PolicyDecision 类型扩展

```
当前: ALLOW / DENY / REQUIRE_APPROVAL
扩展:
  ALLOW_WITH_WARNING  → 放行但记录告警
  ALLOW_WITH_LIMIT    → 放行但有额度/次数限制
  DEFER_TO_HUMAN      → 转人工
  RATE_LIMITED        → 被限流
```

## 7. 工具治理

### 7.1 工具注册模型

```java
interface StandardTool {
    String name();                    // "order_query"
    String description();             // "查询指定订单的详情和状态"
    JsonSchema inputSchema();         // 结构化输入定义
    JsonSchema outputSchema();        // 结构化输出定义
    Duration timeout();               // 超时
    RiskLevel riskLevel();            // READ_ONLY | MUTATION | DANGEROUS
    Set<String> requiredPermissions(); // 权限
    String version();                 // 语义版本
}
```

### 7.2 工具市场（Tool Marketplace）

```
┌──────────────────────────────────────────────┐
│              Tool Registry (Nacos)            │
│                                               │
│  tool:order_query     v1.2.0   →  work-order │
│  tool:inventory_check v1.0.0   →  work-order │
│  tool:refund_create   v2.0.0   →  work-order │
│  tool:dish_search     v1.5.0   →  dish-rag   │
│  tool:menu_photo      v1.0.0   →  multimodal │
│                                               │
│  Permissions:                                  │
│    dish-brand-a:*    →  A 品牌全部工具         │
│    dish-brand-b:*    →  B 品牌全部工具         │
│    dish-global:*     →  全局共享（需审批）      │
└──────────────────────────────────────────────┘
```

### 7.3 工具调用链路

```
Agent 决定使用工具
  → Gateway 查 Tool Registry 获取工具元信息
  → PolicyGatekeeper 校验权限 + 风险
  → 通过 → Dubbo 调用工具
  → 记录调用日志（时间、参数、结果、耗时）
  → 返回结果给 Agent
```

## 8. 性能瓶颈与优化

| 环节 | 典型延迟 | 优化手段 |
|------|---------|---------|
| 意图识别 LLM | 300-800ms | 小模型 + Semantic Cache |
| 检索 | 50-200ms | 多路并行 + 索引优化 + 缓存 |
| Reranker | 20-100ms | ONNX Runtime / TensorRT |
| ReAct 推理 | 500ms-2s/步 | Streaming + 提前终止 |
| 工具调用 | 100ms-2s | 连接池 + 超时 + 降级 |
| 记忆读写 | 5-50ms | Redis Pipeline + 本地缓存 |
| **端到端目标** | P50 < 2s, P95 < 8s | - |

详见 `docs/technical/PERFORMANCE_OPTIMIZATION.md`。
