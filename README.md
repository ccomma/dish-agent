# 餐饮智能助手 (dish-agent)

基于 **LangChain4j 1.x** 的微服务架构实现，用于餐饮 SaaS 场景的智能客服系统。

**v2.0 新增**：Spring Cloud Alibaba + Dubbo 微服务架构，每个 Agent 可独立部署。
**v2.1 新增**：Control Plane 微服务群，支持 Planner / Policy / Memory 三层控制平面、独立 `dish-control-api` 契约模块、编排预览、审批闭环、可视化 Dashboard，以及 `Redis + 向量检索` 的长期记忆双层架构。

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
- 控制面 Dashboard 接口：`GET /api/control/dashboard/overview`。
- 控制面页面入口：`GET /control/dashboard`，提供审批队列、活跃会话、时间线检查器和编排预览工作台。
- 独立契约层：新增 `dish-control-api`，网关不再直接依赖 planner/policy/memory 实现模块。
- 结构化审批票据：审批记录以稳定可解析格式写入记忆服务，避免运行期 JSON 依赖冲突。
- 结构化记忆时间线：记忆服务支持按 `memoryType`、关键字、元数据过滤，便于控制面排查。
- 跨会话控制台聚合：记忆时间线支持按租户维度聚合，多会话 Dashboard 可直接从控制面查询。
- Redis 持久化记忆层：`dish-memory` 支持 `memory.mode=redis`，会话时间线、审批票据和控制面聚合可持久化到 Redis。
- 长期记忆混合检索：`dish-memory` 的 `MemoryReadService` 现在使用 `keyword + vector similarity + recency` 混合召回，支持非完全匹配的语义记忆检索。

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
| dish-memory | 20886 (Dubbo) | 会话记忆、审批票据、时间线检索 | In-memory Timeline, Dubbo |
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
