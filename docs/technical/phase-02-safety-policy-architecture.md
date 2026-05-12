# 安全与工具治理

## 1. 安全架构总览

```
                     ┌─────────────────┐
                     │   Input Filter  │ ← 注入检测、格式校验
                     └────────┬────────┘
                              │
                     ┌────────▼────────┐
                     │  Policy Engine  │ ← 风险评级、权限校验
                     └────────┬────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
     ┌───────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
     │   ALLOW      │ │  APPROVAL  │ │    DENY     │
     │   继续执行    │ │  挂起审批   │ │   阻断返回   │
     └──────────────┘ └────────────┘ └─────────────┘
              │               │
     ┌───────▼──────┐ ┌─────▼──────┐
     │  Output Check │ │Human Review│
     │ 脱敏/一致性    │ └─────┬──────┘
     └───────┬──────┘       │
             │        ┌─────▼──────┐
     ┌───────▼──────┐ │   Resume   │
     │   返回用户    │ │  继续执行   │
     └──────────────┘ └────────────┘
```

## 2. 输入安全

### 2.1 Prompt Injection 检测

```
检测策略：

1. 模式匹配（正则）：
   - "忽略.*指令" / "ignore.*instruction"
   - "你是.*现在你是" / "you are.*now you are"
   - "system:" / "system prompt:"
   - "输出.*system prompt" / "print.*system prompt"

2. 分隔符滥用检测：
   - 用户输入中包含 "###"、"```"、"---" 等可能被 LLM 解析为分隔符的字符
   - 检测后做转义处理

3. 长度/复杂度异常：
   - 单条消息超过 5000 字符 → 截断 + 告警
   - 包含过多 JSON/代码块 → 标记可疑

4. 语义检测（未来）：
   - 专用小型分类模型判断输入是否有注入意图
```

### 2.2 输入校验

```
- SQL 注入模式检测（虽然 Agent 不直接写 SQL，但工具可能解析参数）
- XSS 模式检测（输出到控制台时的风险）
- PII 检测：如果用户意外输入了身份证、银行卡号 → 脱敏后处理
```

## 3. 策略引擎（Policy Engine）

### 3.1 风险评级矩阵

| 操作 | 风险等级 | 默认策略 | 审批条件 |
|------|---------|---------|---------|
| 查询菜品信息 | LOW | ALLOW | 无需 |
| 查询订单状态 | LOW | ALLOW | 无需 |
| 查询库存 | LOW | ALLOW | 无需 |
| 创建订单 | MEDIUM | ALLOW | 金额 > ¥1000 时审批 |
| 修改订单 | MEDIUM | ALLOW_WITH_WARNING | 金额变动 > 20% 时审批 |
| 创建退款 | HIGH | REQUIRE_APPROVAL | 金额 > ¥200 时审批 |
| 批量退款 | CRITICAL | REQUIRE_APPROVAL | 所有批量操作 |
| 修改菜品信息 | HIGH | REQUIRE_APPROVAL | 所有修改操作 |
| 删除数据 | CRITICAL | DENY（转人工） | 不自动执行 |

### 3.2 RBAC 权限模型

```
角色层级：
  customer    → 只读自己订单 + 创建订单 + 小额退款
  waiter      → 读本门店菜单/库存 + 帮顾客下单
  manager     → waiter + 审批退款(≤¥500) + 查看报表
  admin       → 全部操作 + 修改菜品/政策 + 管理 Agent
  super_admin → admin + 跨品牌管理 + Agent 注册/退役
```

### 3.3 频率限制（Rate Limiting）

```
Per User:
  - 创建订单: 20 次/分钟
  - 退款申请: 5 次/分钟
  - 查询: 100 次/分钟

Per Session:
  - ReAct 循环: 10 步/请求
  - 工具调用: 15 次/请求

Per Tenant:
  - 总请求: 1000 次/分钟

超限 → HTTP 429 + 告警日志
```

## 4. 输出安全

### 4.1 Faithfulness 检查

```
自检 Prompt：
  "请检查以下回答是否完全基于检索到的上下文。如果回答中有任何声称
   未在上下文中找到依据，标记为 UNFAITHFUL 并指出具体问题。

   检索上下文：{context}
   Agent 回答：{answer}
   
   输出 JSON：{\"faithful\": bool, \"issues\": [...]}"
```

### 4.2 内容安全

- 推荐菜品必须是当前在售的（查库存验证）
- 价格必须与菜单一致（查菜品数据库验证）
- 退款金额不能超过原始订单金额
- 不输出其他顾客的订单信息（数据隔离）

### 4.3 输出脱敏

```java
// 脱敏规则
phoneNumber  → 138****1234
idCard       → 3301**********1234
bankCard     → 6222****1234
address      → 浙江省杭州市余杭区****
```

## 5. 审计日志

### 5.1 日志格式

```json
{
  "timestamp": "2026-05-04T12:00:00Z",
  "traceId": "abc-123",
  "tenantId": "STORE-001",
  "userId": "U-12345",
  "action": "CREATE_REFUND",
  "params": {
    "orderId": "ORD-67890",
    "amount": 68.00,
    "reason": "菜凉了"
  },
  "policyDecision": "REQUIRE_APPROVAL",
  "result": "APPROVED",
  "decidedBy": "manager-001",
  "latencyMs": 2500
}
```

### 5.2 存储与保留

- 审计日志写入独立的日志文件/appender
- 保留 90 天（合规要求）
- 不可修改/删除（append-only）
- 支持按 traceId / tenantId / userId / action 查询

## 6. 工具治理

### 6.1 工具注册规范

```yaml
tool:
  name: "refund_create"
  display_name: "创建退款工单"
  version: "2.0.0"
  description: "根据订单ID和退款原因创建退款工单"
  owner: "work-order-team"
  
  input_schema:
    type: object
    properties:
      orderId:
        type: string
        description: "订单ID"
      reason:
        type: string
        description: "退款原因"
        maxLength: 500
      amount:
        type: number
        description: "退款金额"
        minimum: 0
    required: ["orderId", "reason"]
    
  risk:
    level: HIGH
    requires_approval: true
    approval_threshold:
      field: amount
      operator: ">"
      value: 200
      
  rate_limit:
    max_per_minute: 5
    max_per_user_per_hour: 10
    
  permissions: ["refund:create"]
  roles: ["customer", "waiter", "manager", "admin"]
  
  timeout_ms: 5000
  retry:
    max_attempts: 2
    backoff: exponential
```

### 6.2 工具调度流程

```
Agent 输出 tool_call → Gateway
  → Tool Registry 查找工具元信息
  → 校验权限 (RBAC)
  → 校验频率限制
  → Policy Engine 风险评估
  → ALLOW: 执行工具调用
  → APPROVAL: 创建审批票据 → 挂起
  → DENY: 返回拒绝原因
  → 记录审计日志
  → 返回结果给 Agent
```

### 6.3 工具监控

| 指标 | 阈值 | 告警 |
|------|------|------|
| 调用成功率 | < 95% | P1 |
| P95 延迟 | > 3s | P2 |
| 被限流次数 | > 100/hour | P2 |
| 被拒绝次数 | > 50/hour | P2 |
| 错误率 | > 5% | P1 |

## 7. 跨业务方工具共享

### 7.1 工具可见性

```
命名空间隔离：
  brand-a:tool_name   →  仅品牌 A 可见
  brand-b:tool_name   →  仅品牌 B 可见
  global:tool_name    →  所有品牌可见（需审批）

共享审批流程：
  Brand A 提交工具共享申请 → Brand B 管理员审批 → 建立共享关系
  → Brand B 的 Agent 可以调用 Brand A 的指定工具
```

### 7.2 数据隔离

- 工具调用时携带 caller 的 tenantId
- 被调用方按 tenantId 过滤数据
- A 品牌 Agent 调用 B 品牌查询工具 → 只返回 A 品牌自家的数据
- 跨品牌数据访问 → 需要显式授权 + 审计记录
