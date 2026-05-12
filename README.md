# dish-agent

面向餐饮 SaaS 场景的企业级 AI Agent 平台。项目以 Java / Spring / Dubbo 微服务为基础，把自然语言请求接入规划、策略、记忆、审批和多 Agent 执行链路。

## 项目状态

- 已完成：微服务底座、Control Plane、双层记忆、Execution Runtime、全链路可观测
- 当前阶段：Phase 6 `RAG 2.0`
- 后续方向：在 Phase 6 完成后演进到更高层的检索决策能力

## 阅读路径

### 我想快速理解项目

1. 本文档
2. [DESIGN.md](DESIGN.md)
3. [docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md](docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md)

### 我想开始继续开发

1. [docs/context/CURRENT_HANDOFF.md](docs/context/CURRENT_HANDOFF.md)
2. [docs/process/DEVELOPMENT_FLOW.md](docs/process/DEVELOPMENT_FLOW.md)
3. [docs/phases/phase-06-rag-2.0/HANDOFF.md](docs/phases/phase-06-rag-2.0/HANDOFF.md)
4. [AGENTS.md](AGENTS.md) 或 [CLAUDE.md](CLAUDE.md)

### 我想看当前阶段设计细节

- [docs/prd/phase-06-rag-2.0.md](docs/prd/phase-06-rag-2.0.md)
- [docs/technical/phase-06-rag-architecture.md](docs/technical/phase-06-rag-architecture.md)
- [docs/testing/phase-06-rag-2.0-test-plan.md](docs/testing/phase-06-rag-2.0-test-plan.md)
- [docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md](docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md)
- [docs/phases/phase-06-rag-2.0/ACCEPTANCE.md](docs/phases/phase-06-rag-2.0/ACCEPTANCE.md)

`docs/` 目录的 ownership 与辅助目录规则见 [docs/README.md](docs/README.md)。

## 架构概览

```text
HTTP / Dashboard
  -> dish-gateway
  -> control plane
     -> dish-planner
     -> dish-policy
     -> dish-memory
  -> agent cluster
     -> dish-agent-dish
     -> dish-agent-workorder
     -> dish-agent-chat
```

## 模块职责

- `dish-gateway`：HTTP 入口、会话绑定、执行编排、控制面查询、结果聚合
- `dish-control-api`：Gateway 与 Control Plane 的稳定 RPC 契约
- `dish-planner`：执行规划与节点图构建
- `dish-policy`：风险判定、放行 / 审批决策
- `dish-memory`：会话记忆、审批时间线、长期知识检索、execution runtime 持久化
- `dish-agent-dish`：菜品知识问答与 RAG 主路径
- `dish-agent-workorder`：库存、订单、退款等业务动作
- `dish-agent-chat`：非垂直知识依赖的闲聊路径
- `dish-common`：跨模块共享契约、上下文与基础执行支撑

## 关键约束

1. Gateway 只依赖 `dish-control-api`，不直接依赖 Provider 实现模块。
2. Agent 之间不直接通信，统一经 Gateway 编排。
3. 记忆读写统一走 `dish-memory`，Agent 不直接操作底层存储。
4. 高风险业务动作需经 policy 判定，必要时进入审批闭环。
5. 追踪规范统一为 HTTP `X-Trace-Id`、Dubbo attachment `traceId`、日志 `traceId=%X{traceId}`。

## 本地开发

### 环境要求

- Java 17+
- Maven 3.6+
- Docker

### 常用命令

```bash
# 微服务主线编译
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml

# 全量测试
mvn test -s settings-test.xml

# dish-memory 定向测试
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false
```

如果需要验证 `langchain4j-demo` 的真实外部模型链路，需显式开启：

```bash
RUN_LANGCHAIN4J_DEMO_INTEGRATION=true mvn test -pl langchain4j-demo -s settings-test.xml
```

## 仓库结构

```text
dish-agent/
├── dish-common/
├── dish-control-api/
├── dish-gateway/
├── dish-planner/
├── dish-policy/
├── dish-memory/
├── dish-agent-dish/
├── dish-agent-workorder/
├── dish-agent-chat/
├── docs/
├── DESIGN.md
├── README.md
├── AGENTS.md
└── CLAUDE.md
```

## 相关文档

- [docs/adr/ADR-003-multi-recall-fusion.md](docs/adr/ADR-003-multi-recall-fusion.md)
- [docs/adr/ADR-006-agentic-rag.md](docs/adr/ADR-006-agentic-rag.md)
- [ops/observability/README.md](ops/observability/README.md)
