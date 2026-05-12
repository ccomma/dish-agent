# DESIGN.md

## 产品定义

`dish-agent` 是面向餐饮 SaaS 场景的企业级 AI Agent 平台。它的目标不是做通用聊天机器人，而是把点餐、查菜、订单查询、退款处理、库存检索、政策问答等高频业务动作，组织成可规划、可审计、可回放、可持续演进的多 Agent 系统。

## 目标用户

- 一线门店角色：服务员、店长、顾客
- 中后台角色：运营、总部知识维护人员
- 平台工程角色：负责治理 Agent、RAG、审批、记忆和可观测性的研发与运维团队

## 核心痛点

1. 餐饮业务请求天然跨知识、跨工具、跨风险等级，单一 Agent 容易把规划、检索、执行和风控混在一起。
2. 业务链路里既有低风险高频问答，也有退款、库存、合规等高风险动作，不能只靠一层 prompt 决策。
3. 业务方需要的不只是回答正确，还要能追溯为什么这样答、为什么这样做、出了问题怎么回放。
4. 随着能力演进，检索、记忆、规划和可观测性都需要独立升级，不能把所有变化都压回单体对话链路。

## 北极星

在不牺牲业务安全和系统可控性的前提下，让餐饮业务中的自然语言请求可以稳定地进入标准业务链路，并留下完整的执行、审批、记忆和观测证据。

## 差异化

1. 平台把语言理解接到了真实业务执行链路，而不止是知识问答。
2. 平台把风险判定、审批闭环、执行回放和 trace 证据视为一等能力。
3. 平台允许检索、记忆、规划、执行和观测按 phase 独立演进，而不是一开始就追求全自主 Agent。

## 长期判断

1. `dish-agent` 的核心价值在于业务执行平台化，而不是单点 prompt 技巧。
2. 检索、规划、策略、执行、审批、记忆、观测必须分层建设，避免把复杂度堆回单一 Agent。
3. 智能能力应渐进增强：先把固定链路做稳，再引入更强的检索和决策层。
4. 高风险业务必须把 AI 能做 与 AI 能自主决定 区分开，默认保留人工审批与降级路径。

## 非目标

- 不做通用娱乐型 Chatbot
- 不替代人工进行高风险最终决策
- 不在当前产品阶段追求实时语音 / 视频理解
- 不在检索基础设施尚未稳定前提前全面 Agentic 化

## 产品边界

### 系统边界

`dish-agent` 以 Gateway 编排、Control Plane 治理、Agent Cluster 执行为稳定主架构：

```text
HTTP / UI
  -> dish-gateway
  -> control plane (planner / policy / memory)
  -> agent cluster (dish / workorder / chat)
```

### 业务边界

- 平台负责理解请求、规划执行、治理风险、检索知识、回写运行态
- 业务数据访问通过既定后端网关和工具层完成
- 高风险动作必须经 policy 判定，需要时进入审批闭环

### 长期演进边界

- 后续可以演进到更强的检索决策层、图谱、多模态与评测体系
- 但这些方向只有在底层检索、执行、记忆和治理链路稳定后才值得推进

## 设计原则

1. 安全优先：风险识别、审批门禁、可回放优先于功能激进度。
2. 分层清晰：业务执行、治理决策、存储适配、观测链路各自归位。
3. 渐进增强：先建立稳定基础设施，再叠加更复杂的智能层。
4. 兼容演进：在不明确要求破坏式变更前，优先保持公共接口稳定。
5. 可观测与可解释：每次规划、策略、召回、执行都应可追踪。
6. 多租户隔离：门店、品牌、知识和业务数据按租户隔离。

## 风险原则

1. 不把高风险动作默认交给自主决策链路，优先保留 policy 与 approval 兜底。
2. 不把探索性方向直接升级为当前执行态；必须先回写 owning layer。
3. 不把 phase 文档写成已完成的假象；acceptance 只记录真实证据。
4. 不为了 future vision 提前污染当前 phase 范围。

## 稳定架构约束

这些约束属于长期有效的系统边界，应在阶段规划、技术设计和代码实现时反复对照。

### 跨模块稳定约束

1. Gateway 只依赖 `dish-control-api`，不直接依赖任何 Provider 实现模块。
2. Agent 之间不直接通信，统一经 Gateway 编排。
3. 记忆读写统一走 `dish-memory` Dubbo 接口，Agent 不直接访问 Redis/Milvus。
4. 高风险动作默认经 Policy / Approval 链路治理。
5. 全链路必须保持 trace 透传：HTTP `X-Trace-Id`、Dubbo attachment `traceId`、日志 `traceId=%X{traceId}`。

### 模块分层约束

#### dish-memory

- `service.impl` 只保留 Dubbo Provider 门面职责。
- `storage` 负责 Redis / 内存模式下的数据读写与长期记忆存储适配。
- `retrieval` 负责召回编排、混合检索、排序、解释与结果组装。
- `runtime` 负责 execution runtime 的 graph 投影和状态演算。
- `support` 只保留 memory 模块强语义支撑。
- `approval` 负责审批票据装配、审批决策应用、审批时间线写入。

#### dish-planner / dish-policy

- `service.impl` 只保留 Dubbo Provider 门面职责和规则编排入口。
- 较复杂的规则图构建与规则判断应下沉到 `support`。

#### dish-agent-dish / dish-agent-workorder / dish-agent-chat

- Provider 门面类只负责 Dubbo 对外入口和最小参数整理。
- Agent / ReAct 类负责上下文整理、步骤编排、最终响应转换。
- `tools` 只提供业务动作入口，`backend` 只负责数据源适配，`rag` 只负责知识预热、检索、重排、生成。

#### dish-gateway

- `controller` 只保留 HTTP 入参与响应出参处理。
- `service.impl` 负责会话绑定、路由编排、控制面查询、execution 恢复和响应聚合。
- `observability` 负责指标和 tracing，不放业务编排分支。
- `support` 负责 execution graph 还原、Dashboard 聚合、控制面视图 DTO 装配。

## 生产化约束

1. `sessionId` 必须全链路传递，店铺优先从 `X-Store-Id` 绑定，缺失时回退 `STORE_001`。
2. RAG 业务知识通过 classpath 文件维护，不在代码中硬编码业务知识。
3. 多步推理必须基于 `AbstractReActEngine` 扩展实现。
4. 工具层通过 `WorkOrderBackendGateway` 访问工单后端，禁止在工具中直接写数据访问。
5. 会话存储通过 `session.store.type` 切换 `memory` 或 `redis`。
6. 微服务主线至少维护网关层单元测试；路由、会话、RAG 加载逻辑的改动必须补测试。
7. `POST /api/control/plan-preview` 只做编排预览，不允许真实执行 Agent。
8. `Execution Runtime` 统一由 Gateway 产出事件、`dish-memory` 持久化。
9. 所有微服务暴露健康检查与监控端点，观测启动资源统一维护在 `ops/observability/`。
