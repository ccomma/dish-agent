# 餐饮智能助手 (dish-agent)

基于 **LangChain4j 1.x** 的微服务架构实现，用于餐饮 SaaS 场景的智能客服系统。

本文档面向第一次阅读或准备运行本项目的开发者，重点说明项目是什么、有哪些模块、如何启动、各模块大致负责什么。

- **v2.0 新增**：Spring Cloud Alibaba + Dubbo 微服务架构，每个 Agent 可独立部署。
- **v2.1 新增**：Control Plane 微服务群，支持 Planner / Policy / Memory 三层控制平面、独立 `dish-control-api` 契约模块、编排预览、审批闭环、可视化 Dashboard，以及 `Redis + 向量检索` 的长期记忆双层架构。
- **v2.2 新增**：`Redis + Milvus` 真正双层长期记忆、短期/审批/长期知识分层写入策略，以及控制台内置“向量命中分数 + 召回来源解释”可视化。
- **v2.3 新增**：Execution Runtime Store、SSE 实时步骤流、Execution Replay，以及可视化 Mission Control DAG 控制台。
- **v2.4 新增**：`Actuator + Prometheus + Grafana` 观测底座、execution runtime 自定义指标，以及统一 trace 传播规范。
- **v2.5 新增**：`OpenTelemetry + OTLP Collector + Tempo` 分布式追踪链路，Gateway 手工 step spans，以及 Dubbo context 透传。

## 项目概述

本项目展示了如何使用 LangChain4j 框架构建企业级 AI 应用：

- **微服务架构**：Gateway + Agent Cluster 的分布式部署方案
- **多 Agent 协同**：意图识别、动态路由、服务编排
- **ReAct 多步推理**：每个 Agent 内部实现 Thought → Action → Observation 循环
- **垂直领域 RAG**：餐饮知识库的检索增强生成
- **Function Calling**：与业务系统的工具调用集成

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         Gateway Layer (8080)                    │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │  意图识别    │ →  │ Control API │ →  │    结果整合         │ │
│  │(RoutingAgent)│    │ + Dubbo RPC │    │ (ResponseAggregator)│ │
│  └─────────────┘    └─────────────┘    └─────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Control Plane (Dubbo RPC)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ dish-planner │  │ dish-policy  │  │     dish-memory      │ │
│  │   (20884)    │  │   (20885)    │  │       (20886)        │ │
│  │ DAG 规划      │  │ 风险判定/审批 │  │ 会话记忆/审批时间线   │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     Agent Cluster (Dubbo RPC)                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │ dish-agent-dish  │  │dish-agent-work  │  │dish-agent-   │ │
│  │   (20881)       │  │   order (20882)  │  │    chat      │ │
│  │ + ReAct Loop    │  │ + ReAct Loop    │  │  (20883)     │ │
│  │ + RAG Pipeline  │  │ + Tool Call     │  │              │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                          dish-common                            │
│  ReAct Engine │ Tool Call Framework │ Context / Dubbo Interfaces │
└─────────────────────────────────────────────────────────────────┘
```

## 项目结构

```
dish-agent/
├── pom.xml                              # 父POM（Spring Cloud Alibaba + Dubbo）
├── settings-test.xml                    # Maven 配置
│
├── dish-common/                         # 公共模块
│   └── com.example.dish/
│       ├── react/ReActEngine.java        # ReAct引擎接口
│       ├── context/AgentContext.java    # 上下文传递
│       └── rpc/                         # Dubbo服务接口
│
├── dish-control-api/                    # Control Plane 独立契约模块
│   └── com.example.dish.control/
│       ├── planner/                     # PlanningRequest / PlanningResult / ExecutionPlannerService
│       ├── policy/                      # PolicyEvaluationRequest / PolicyEvaluationResult / PolicyDecisionService
│       ├── memory/                      # MemoryRead/Write/Timeline 契约
│       └── approval/                    # 审批票据创建/查询/决策契约
│
├── dish-gateway/                        # 网关服务 (8080)
│   └── com.example.dish.gateway/
│       ├── controller/ChatController.java
│       ├── controller/ControlPlaneController.java
│       ├── agent/RoutingAgent.java
│       └── service/impl/AgentDispatchServiceImpl.java
│
├── dish-planner/                        # 编排规划服务 (Dubbo 20884)
├── dish-policy/                         # 策略/审批服务 (Dubbo 20885)
├── dish-memory/                         # 会话记忆与审批时间线 (Dubbo 20886)
│
├── dish-agent-dish/                     # 菜品知识Agent (Dubbo 20881)
│   └── com.example.dish/
│       ├── rag/RAGPipeline.java         # RAG两阶段检索
│       └── service/DishReActAgent.java  # ReAct多步推理
│
├── dish-agent-workorder/                # 工单处理Agent (Dubbo 20882)
│   └── com.example.dish/
│       ├── tools/                        # 业务工具
│       └── service/WorkOrderReActAgent.java
│
├── dish-agent-chat/                      # 闲聊Agent (Dubbo 20883)
│
├── langchain4j-demo/                   # 教学演示模块（保留）
└── langchain4j-enterprise/              # 单体版（保留）
```

## 模块目录说明

这一节面向项目阅读者，说明各模块当前的主要目录分工，帮助快速建立代码导航感。更细的实现约束、演进规则和 Agent 执行规范维护在 `AGENTS.md`。

### dish-memory

`dish-memory` 目前主要分为下面几层：

- `com.example.dish.memory.service.impl`
  - Dubbo Provider 实现入口。
  - 负责对外暴露记忆、审批、execution runtime 等服务方法。

- `com.example.dish.memory.storage`
  - 存储访问层。
  - 负责 Redis / 内存模式下的时间线、审批票据、execution runtime 等数据存取。

- `com.example.dish.memory.retrieval`
  - 记忆召回层。
  - 负责候选收集、混合检索、结果排序与召回结果组装。

- `com.example.dish.memory.runtime`
  - execution runtime 视图层。
  - 负责根据 event stream 推导 graph、节点状态和耗时等运行态视图。

- `com.example.dish.memory.support`
  - memory 模块专属支撑类。
  - 包括 Redis key、memory codec、向量化支持等基础能力。

- `com.example.dish.memory.model`
  - memory 模块内部模型。
  - 用于模块内部协作，不直接作为外部 RPC 契约。

### dish-planner

- `com.example.dish.planner.service.impl`
  - Planner Dubbo Provider 实现入口。
  - 负责根据意图生成执行节点、依赖边和执行模式。

- `com.example.dish.planner.model`
  - planner 模块内部模型。
  - 用于承载规则图构建过程中的内部数据。

### dish-policy

- `com.example.dish.policy.service.impl`
  - Policy Dubbo Provider 实现入口。
  - 负责根据节点属性、租户范围和业务意图做策略放行、阻断或审批判断。

- `com.example.dish.policy.model`
  - policy 模块内部模型。
  - 用于组织规则评估过程中使用的辅助数据。

### dish-gateway

`dish-gateway` 目前主要分为下面几层：

- `com.example.dish.gateway.controller`
  - HTTP 入口和控制台入口。
  - 负责接收聊天请求、控制面查询请求以及页面跳转。

- `com.example.dish.gateway.agent`
  - 路由决策层。
  - 负责意图识别和目标 Agent 选择。

- `com.example.dish.gateway.service.impl`
  - 网关核心编排层。
  - 负责会话绑定、Agent 派发、控制面查询、审批恢复、execution 事件发布和响应聚合。

- `com.example.dish.gateway.observability`
  - 网关观测层。
  - 负责 execution 指标和 tracing 辅助能力。

- `com.example.dish.gateway.support`
  - 网关内部支撑层。
  - 负责 execution graph 还原等可复用的轻量支撑逻辑。

- `com.example.dish.gateway.dto`
  - gateway 对外返回 DTO。
  - 包括聊天主链路响应和控制台展示 DTO。

### dish-agent-dish

`dish-agent-dish` 目前主要分为下面几层：

- `com.example.dish.service.provider`
  - Dubbo Provider 门面。
  - 对外暴露标准问答和带反思重试的问答入口。

- `com.example.dish.service`
  - Agent 编排层。
  - 负责 ReAct 执行、上下文补齐和统一响应组装。

- `com.example.dish.rag`
  - RAG 检索层。
  - 负责知识预热、向量检索、可选重排和最终回答生成。

### dish-agent-workorder

`dish-agent-workorder` 目前主要分为下面几层：

- `com.example.dish.service`
  - Dubbo 门面与 ReAct 编排层。
  - 负责库存、订单、退款等工单场景的上下文构造和动作执行。

- `com.example.dish.tools`
  - 业务工具层。
  - 负责库存查询、订单查询、退款建单等动作的统一入口。

- `com.example.dish.tools.backend`
  - 后端访问适配层。
  - 负责 mock/http 两种模式下的数据源访问。

### dish-agent-chat

- `com.example.dish.service`
  - Chat Agent Dubbo Provider 入口。
  - 负责将简单对话请求交给底层 chatModel，并转换成统一 AgentResponse。

- `com.example.dish.config`
  - chat 模型相关配置。

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- Docker（用于 Nacos）
- Minimax API 密钥

### 2. 编译微服务

```bash
# 编译全部模块
mvn clean compile -s settings-test.xml

# 仅编译微服务
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml
```

### 3. 启动微服务

```bash
# 1. 启动 Nacos（服务注册中心）
docker run -d --name nacos -p 8848:8848 nacos/nacos-server

# 2. 启动 Control Plane 服务
java -jar dish-planner/target/dish-planner.jar
java -jar dish-policy/target/dish-policy.jar
java -jar dish-memory/target/dish-memory.jar

# 3. 启动 Agent 服务
java -jar dish-agent-dish/target/dish-agent-dish.jar
java -jar dish-agent-workorder/target/dish-agent-workorder.jar
java -jar dish-agent-chat/target/dish-agent-chat.jar

# 4. 启动网关
java -jar dish-gateway/target/dish-gateway.jar
```

### 4. 调用测试

```bash
curl -X POST http://localhost:8080/api/chat/process \
  -H "Content-Type: application/json" \
  -H "X-Store-Id: STORE_001" \
  -d '{"message": "宫保鸡丁是什么菜系？"}'

# 健康检查
curl http://localhost:8080/api/chat/health

# 编排预览（不真正执行 agent）
curl -X POST http://localhost:8080/api/control/plan-preview \
  -H "Content-Type: application/json" \
  -H "X-Store-Id: STORE_001" \
  -d '{"message": "帮我查一下订单123并总结处理建议", "sessionId": "SESSION_DEMO"}'

# 查询会话记忆时间线
curl "http://localhost:8080/api/control/sessions/SESSION_DEMO/memory?limit=10" \
  -H "X-Store-Id: STORE_001"

# 查询语义召回解释
curl "http://localhost:8080/api/control/sessions/SESSION_DEMO/memory/retrieval?query=经理审核退款要记录什么&layers=SHORT_TERM_SESSION,LONG_TERM_KNOWLEDGE" \
  -H "X-Store-Id: STORE_001"

# 查询某个 session 最近一次 execution DAG
curl "http://localhost:8080/api/control/sessions/SESSION_DEMO/executions/latest" \
  -H "X-Store-Id: STORE_001"

# 查询 execution 图快照
curl "http://localhost:8080/api/control/executions/exec-12345678" \
  -H "X-Store-Id: STORE_001"

# 查询 execution 事件回放
curl "http://localhost:8080/api/control/executions/exec-12345678/replay" \
  -H "X-Store-Id: STORE_001"

# 审批通过
curl -X POST http://localhost:8080/api/control/sessions/SESSION_DEMO/approvals/APR-12345678/approve \
  -H "Content-Type: application/json" \
  -H "X-Store-Id: STORE_001" \
  -d '{"decidedBy":"ops-user","decisionReason":"订单异常已核实"}'

# 控制面 Dashboard
curl "http://localhost:8080/api/control/dashboard/overview?limit=10" \
  -H "X-Store-Id: STORE_001"

# 打开可视化控制台
open http://localhost:8080/control/dashboard

# Gateway Prometheus 指标
curl http://localhost:8080/actuator/prometheus

# Memory Prometheus 指标
curl http://localhost:8093/actuator/prometheus
```

## 生产化改造进展（2026-04）

- 网关修复 `sessionId` 贯通：`ChatController -> RoutingAgent -> AgentContext` 全链路传递同一会话ID。
- 新增多租户会话绑定：支持从请求头 `X-Store-Id` 绑定会话店铺，默认店铺 `STORE_001`。
- 新增请求追踪：网关注入并透传 `X-Trace-Id`，日志中输出 `traceId` 便于排障。
- 新增健康检查接口：`GET /api/chat/health`。
- RAG 知识外置：菜品/政策知识从硬编码迁移到 `dish-agent-dish/src/main/resources/rag/knowledge/*.md`。
- ReAct 引擎统一：`dish-agent-dish` 与 `dish-agent-workorder` 均基于 `AbstractReActEngine` 执行。
- 新增主线模块测试：网关新增会话与路由上下文单元测试。
- 工单后端适配层：`dish-agent-workorder` 新增 `WorkOrderBackendGateway`，支持 `backend.mode=mock|http` 切换。
- 会话存储可切换：网关支持 `session.store.type=memory|redis`，生产可直接启用 Redis 会话共享。
- 会话冲突策略可配置：`session.store.conflict-strategy=keep_existing|prefer_request`，支持显式切店策略。
- 意图抽取失败安全兜底：抽取异常不再静默降级 `GENERAL_CHAT`，统一走安全路径（`UNKNOWN -> chat`）。
- Agent 分发降级：Dubbo 调用失败时按 Agent 返回差异化降级文案与 follow-up hints。
- 网关统一异常响应：通过全局异常处理返回结构化错误，避免 raw 500。
- traceId 跨 Dubbo 透传：网关写入 RPC attachment，Provider 恢复到 MDC，便于跨服务排障。
- Control Plane 微服务化：新增 `dish-planner`、`dish-policy`、`dish-memory` 三个独立 Dubbo 服务。
- 编排预览接口：`POST /api/control/plan-preview` 可输出执行步骤、策略决策、审批要求与记忆命中。
- 审批票据查询接口：`GET /api/control/sessions/{sessionId}/approvals/{approvalId}`。
- 审批闭环接口：`POST /api/control/sessions/{sessionId}/approvals/{approvalId}/approve|reject`。
- 会话记忆时间线接口：`GET /api/control/sessions/{sessionId}/memory`。
- 会话记忆召回解释接口：`GET /api/control/sessions/{sessionId}/memory/retrieval`。
- 最近 execution 查询接口：`GET /api/control/sessions/{sessionId}/executions/latest`。
- execution 图快照接口：`GET /api/control/executions/{executionId}`。
- execution 回放接口：`GET /api/control/executions/{executionId}/replay`。
- execution 实时流接口：`GET /api/control/executions/{executionId}/stream`（SSE）。
- 控制面 Dashboard 接口：`GET /api/control/dashboard/overview`。
- 控制面页面入口：`GET /control/dashboard`，提供 Mission Control DAG、Trace Bridge、Live Event Rail、Replay Console、审批队列和活跃会话工作台。
- execution runtime store：`dish-memory` 额外保存 execution graph snapshot、event stream、session 最新 execution 索引，支持历史回放与实时 SSE 共用同一事件模型。
- 每个微服务都暴露 `/actuator/health`、`/actuator/info`、`/actuator/metrics`、`/actuator/prometheus`，可直接接入 Prometheus。
- Gateway 额外输出 execution runtime 指标：执行启动、结果状态、节点状态切换、节点延迟、审批决策、SSE 订阅数。
- Gateway 关键执行链路额外产出手工 span：`gateway.step.dispatch`、`gateway.step.resume`，方便在 Trace 里直接看到节点级状态流转。
- Dubbo Provider 统一通过 `DubboOpenTelemetrySupport` 从 RPC attachment 恢复父上下文，保证 `gateway -> planner/policy/memory/agent` trace 不断链。
- 本地观测栈资源位于 `ops/observability/`，包含 `docker compose`、Prometheus 抓取配置、Grafana starter dashboard、OTLP Collector 和 Tempo。
- Mission Control 页面会为当前 execution / node 生成 Grafana deep link，并把 `traceId`、`executionId`、`targetAgent`、`focusService` 和时间窗同步到 Grafana Dashboard variables。
- Grafana `dish-agent-mission-control` dashboard 额外提供 `Trace Bridge Context`、`Trace Drill-Down` 和 `Service Graph Map` 面板，方便把 DAG、指标和 trace 串成一条演示链路。
- trace 传播规范统一为：HTTP 头 `X-Trace-Id`，Dubbo attachment `traceId`，日志 MDC 键 `traceId`。
- 默认 OTLP 上报端点为 `http://localhost:4318/v1/traces`；本地启动 `ops/observability/docker-compose.yml` 后即可直接在 Grafana 中联查指标和 Trace。
- 独立契约层：新增 `dish-control-api`，网关不再直接依赖 planner/policy/memory 实现模块。
- 结构化审批票据：审批记录以稳定可解析格式写入记忆服务，避免运行期 JSON 依赖冲突。
- 结构化记忆时间线：记忆服务支持按 `memoryType`、关键字、元数据过滤，便于控制面排查。
- 跨会话控制台聚合：记忆时间线支持按租户维度聚合，多会话 Dashboard 可直接从控制面查询。
- Redis 持久化记忆层：`dish-memory` 支持 `memory.mode=redis`，会话时间线、审批票据和控制面聚合可持久化到 Redis。
- 记忆写入分层：`execution_summary -> SHORT_TERM_SESSION`，`approval_ticket -> APPROVAL`，`operational_knowledge/knowledge_bootstrap -> LONG_TERM_KNOWLEDGE`。
- 真正双层长期记忆：`dish-memory` 现在以 Redis 保存时间线和审批票据，以 Milvus 负责长期知识向量检索，并在控制面统一做融合排序。
- 长期记忆混合检索：`dish-memory` 的 `MemoryReadService` 使用 `keyword + vector similarity + recency` 混合召回，支持非完全匹配的语义记忆检索。
- 长期知识启动预热：`dish-memory/src/main/resources/memory/knowledge/*.md` 会在启动时自动写入长期知识层，方便本地演示和面试展示。

## 功能演示

### 菜品咨询
```
用户: 宫保鸡丁是什么菜？
路由: dish-agent-dish (RAG)
回答: 宫保鸡丁是一道经典川菜，分类为川菜，辣度2级（微辣），价格38元...
```

### 库存查询
```
用户: 门店还有宫保鸡丁吗？
路由: dish-agent-workorder (InventoryTools)
回答: 【库存查询结果】
      • 宫保鸡丁: 50份 (有货)
```

### 退款申请
```
用户: 我要退款，订单号67890，因为菜凉了
路由: dish-agent-workorder (RefundTools)
回答: 【退款工单创建成功】
      工单号: TK1234567890
      订单号: 67890
      原因: 菜凉了
      状态: 待处理
```

## 微服务模块说明

| 模块 | 端口 | 职责 | 主要技术 |
|------|------|------|----------|
| dish-gateway | 8080 (HTTP) | 意图路由、结果聚合 | Spring Boot, Dubbo Consumer |
| dish-control-api | - | Control Plane RPC 契约层 | Java Records, Dubbo Contracts |
| dish-planner | 20884 (Dubbo) | DAG 执行规划、执行模式选择 | Planner Graph, Dubbo |
| dish-policy | 20885 (Dubbo) | 风险判定、审批门禁 | Rule Engine, Dubbo |
| dish-memory | 20886 (Dubbo) | 会话记忆、审批票据、长期知识检索 | Redis Timeline, Milvus, Dubbo |
| dish-agent-dish | 20881 (Dubbo) | 菜品 RAG + ReAct | RAGPipeline, Milvus, Cohere |
| dish-agent-workorder | 20882 (Dubbo) | 库存/订单/退款 + ReAct | InventoryTools, ReActEngine |
| dish-agent-chat | 20883 (Dubbo) | 闲聊对话 | LLM Chat |

## ReAct 多步推理

每个 Agent 内部实现 ReAct 循环：

```
Thought: 分析用户问题
    ↓
Action: 决定下一步行动（如 RAG 检索、工具调用）
    ↓
Observation: 获取行动结果
    ↓
(循环直到得到最终答案)
    ↓
Final: 生成最终回答
```

### 意图 → Agent 映射

```
GREETING / GENERAL_CHAT     → dish-agent-chat
DISH_QUESTION / DISH_INGREDIENT / DISH_COOKING_METHOD / POLICY_QUESTION → dish-agent-dish
QUERY_INVENTORY / QUERY_ORDER / CREATE_REFUND → dish-agent-workorder
```

## 配置说明

### 网关配置 (dish-gateway/src/main/resources/application.yml)

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
dubbo:
  registry:
    address: nacos://localhost:8848
llm:
  api-key: ${MINIMAX_API_KEY}
  base-url: https://api.minimax.chat/v1
  model: minimax-m2.7
logging:
  pattern:
    console: "... traceId=%X{traceId} ..."
```

### Agent 配置 (dish-agent-dish/src/main/resources/application.yml)

```yaml
dubbo:
  protocol:
    name: dubbo
    port: 20881
rag:
  vector-store-type: inmemory  # inmemory | milvus
  milvus:
    host: localhost
    port: 19530
```

### 工单后端适配配置 (dish-agent-workorder/src/main/resources/application.yml)

```yaml
backend:
  mode: ${WORKORDER_BACKEND_MODE:mock} # mock | http
  base-url: ${WORKORDER_BACKEND_BASE_URL:http://localhost:8090}
  timeout-ms: ${WORKORDER_BACKEND_TIMEOUT_MS:2000}
  connect-timeout-ms: ${WORKORDER_BACKEND_CONNECT_TIMEOUT_MS:${WORKORDER_BACKEND_TIMEOUT_MS:2000}}
  read-timeout-ms: ${WORKORDER_BACKEND_READ_TIMEOUT_MS:${WORKORDER_BACKEND_TIMEOUT_MS:2000}}
```

当 `backend.mode=http` 时，默认按以下接口约定调用后端：

- `GET /api/backend/inventory/all?storeId=...`
- `GET /api/backend/inventory?storeId=...&dishName=...`
- `GET /api/backend/stores`
- `GET /api/backend/orders/{orderId}`
- `POST /api/backend/refunds`
- `GET /api/backend/refunds/{ticketId}`

OpenAPI 草案见：[workorder-backend.yaml](/Users/ccomma/Desktop/dish-agent/docs/openapi/workorder-backend.yaml)。

### 会话存储配置 (dish-gateway/src/main/resources/application.yml)

```yaml
session:
  store:
    type: ${SESSION_STORE_TYPE:memory} # memory | redis
    ttl-hours: ${SESSION_STORE_TTL_HOURS:12}
    default-store-id: ${SESSION_DEFAULT_STORE_ID:STORE_001}
    conflict-strategy: ${SESSION_STORE_CONFLICT_STRATEGY:keep_existing} # keep_existing | prefer_request
```

### 记忆存储配置 (dish-memory/src/main/resources/application.yml)

```yaml
memory:
  mode: ${MEMORY_MODE:bootstrap} # bootstrap | redis
  retrieval:
    vector-dim: ${MEMORY_VECTOR_DIM:128}
    candidate-fetch-size: ${MEMORY_RETRIEVAL_CANDIDATE_FETCH_SIZE:200}
    keyword-weight: ${MEMORY_RETRIEVAL_KEYWORD_WEIGHT:0.45}
    vector-weight: ${MEMORY_RETRIEVAL_VECTOR_WEIGHT:0.55}
  long-term:
    provider: ${MEMORY_LONG_TERM_PROVIDER:inmemory} # inmemory | milvus
    embedding-provider: ${MEMORY_LONG_TERM_EMBEDDING_PROVIDER:hash} # hash | openai
    min-score: ${MEMORY_LONG_TERM_MIN_SCORE:0.12}
    bootstrap:
      enabled: ${MEMORY_LONG_TERM_BOOTSTRAP_ENABLED:true}
      pattern: ${MEMORY_LONG_TERM_BOOTSTRAP_PATTERN:classpath*:memory/knowledge/*.md}
    milvus:
      host: ${MILVUS_HOST:localhost}
      port: ${MILVUS_PORT:19530}
      collection: ${MEMORY_MILVUS_COLLECTION:dish_memory_long_term}

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
```

- `bootstrap`：本地开发默认模式，使用进程内存时间线。
- `redis`：生产推荐模式，时间线与审批票据写入 Redis，支持服务重启后保留状态。
- Redis 模式下，时间线仍然按时间顺序保存在 Redis Sorted Set 中，同时为每条记忆生成向量并持久化，`MemoryReadService` 会做混合检索而不是只做字符串包含。
- `memory.long-term.provider=milvus`：把长期知识记忆写入 Milvus；同时 Redis 继续维护控制面时间线，形成真正双层架构。
- `memory.long-term.embedding-provider=hash`：默认使用确定性本地 embedding，方便无外部模型依赖地演示 Milvus；生产可切到 `openai`。

## 依赖说明

- **Spring Cloud Alibaba**: 2023.0.1.0
- **Dubbo**: 3.2.4
- **LangChain4j**: 1.12.2
- **langchain4j-milvus**: 1.0.0-beta5
- **langchain4j-cohere**: 1.0.0-beta5

> 注意：使用 `settings-test.xml` 编译以指向 Maven Central

## 向量检索与 RAG

### 架构

生产级 RAG 使用两阶段检索 + 重排序：

```
用户问题 → Embedding → Milvus/InMemory (Top-5) → Cohere Reranking (Top-3) → LLM → 回答
```

### 知识来源

- 当前知识文档外置于 `dish-agent-dish/src/main/resources/rag/knowledge/`。
- 新增知识建议通过新增 Markdown 文件完成，无需修改 `RAGPipeline` 代码。

### 启动 Milvus（Docker）

```bash
docker run -d --name milvus -p 19530:19530 milvusdb/milvus:latest
```

## 学习路径

1. **微服务架构（v2.0）**：学习分布式 Agent 系统
   - dish-gateway → 网关路由
   - dish-agent-dish → RAG + ReAct
   - dish-agent-workorder → 工具调用 + ReAct

2. **langchain4j-demo**：学习 LangChain4j 基础用法
   - BasicChatExample → 对话基础
   - PromptTemplateExample → 提示词工程
   - DocumentRAGExample → RAG 实现
   - ToolCallingExample → 函数调用

3. **langchain4j-enterprise**：学习单体版 Agent 架构（保留参考）
   - Bootstrap → 启动入口
   - 各 Agent 源码 → 架构设计

## 参考资源

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [Dubbo 官方文档](https://dubbo.apache.org/)
- [Spring Cloud Alibaba](https://spring-cloud-alibaba-group.github.io/)
- [Minimax API 文档](https://api.minimax.chat/)

## 许可证

MIT 许可证
