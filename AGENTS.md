# AGENTS.md

本文件为 Codex (Codex.ai/code) 在此代码库中工作时提供指导。

**重要**：每次对项目进行修改后，请检查是否需要同步更新本文件和 README.md，以确保文档与代码保持一致。

## 通用整理流程

跨项目通用的模块整改方法已经沉淀到 skill：

- [engineering-baseline](./.agents/skills/engineering-baseline/SKILL.md)

后续如果要做模块级重构、包分层整理、注释回填、README/AGENTS 边界梳理、通用能力上收，优先遵循这个 skill。

本文件只保留这个仓库的特化落点，不再重复整套跨项目通用方法论。

## 本仓库特化约束

- 注释默认使用中文。
- 修改代码后，仍需检查是否要同步更新 `README.md` 和 `AGENTS.md`。
- `README.md` 面向第一次阅读项目的开发者，`AGENTS.md` 面向本仓库内工作的 Agent。
- 对于本仓库中的核心门面类、编排类、存储适配类、运行态投影类、召回流程类，默认应补中文类注释和必要的 `1、2、3` 步骤注释。

## 项目概述

这是一个 **LangChain4j 1.x 微服务架构项目**，包含两类模块：

### 微服务模块（新架构 - v2.0）

基于 **Spring Cloud Alibaba + Dubbo** 的工业级微服务架构：

```
┌─────────────────────────────────────────────────────────────────┐
│                        Gateway Layer                            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │  意图识别    │ →  │ Control API │ →  │    结果整合         │ │
│  │(RoutingAgent)│    │ + Dubbo RPC │    │ (ResponseAggregator)│ │
│  └─────────────┘    └─────────────┘    └─────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                      Control Plane Cluster                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ dish-planner │  │ dish-policy  │  │     dish-memory      │ │
│  │  DAG 编排     │  │ 风险门禁/审批 │  │ 记忆时间线/审批票据    │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                     Agent Cluster (独立部署)                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │ dish-agent-dish  │  │dish-agent-work  │  │dish-agent-   │ │
│  │ @DubboService   │  │   order         │  │    chat      │ │
│  │ + ReAct Loop   │  │ @DubboService   │  │ @DubboService│ │
│  │ + RAG Pipeline │  │ + ReAct Loop   │  │              │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                          dish-common                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐│
│  │ ReAct Engine│  │ Tool Call   │  │ Context / Tracing /     ││
│  │ (通用封装)  │  │ Framework   │  │ Dubbo Interfaces      ││
│  └─────────────┘  └─────────────┘  └─────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

| 模块 | 端口 | 职责 |
|------|------|------|
| dish-gateway | 8080 | HTTP 入口、意图路由、结果聚合 |
| dish-control-api | - | Control Plane RPC 契约层 |
| dish-planner | 20884 (Dubbo) | DAG 规划、执行模式生成 |
| dish-policy | 20885 (Dubbo) | 风险判定、审批门禁 |
| dish-memory | 20886 (Dubbo) | 会话记忆、审批票据、时间线查询、长期知识召回 |
| dish-agent-dish | 20881 (Dubbo) | 菜品知识 RAG + ReAct 多步推理 |
| dish-agent-workorder | 20882 (Dubbo) | 库存/订单/退款 + ReAct 多步推理 |
| dish-agent-chat | 20883 (Dubbo) | 闲聊对话 |
| dish-common | - | 公共组件：ReActEngine、AgentContext、Dubbo 接口 |

### 遗留模块（保留用于教学）

| 模块 | 用途 |
|------|------|
| langchain4j-demo | LangChain4j 1.x 教学演示 |
| langchain4j-enterprise | 单体版企业级多Agent（已迁移到微服务） |

## 常用命令

### 构建和编译

```bash
# 编译全部模块
mvn compile -s settings-test.xml

# 仅编译微服务模块
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml

# 清理并编译
mvn clean compile -s settings-test.xml

# 打包微服务 JAR
mvn package -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml
```

### 微服务启动顺序

```bash
# 1. 启动 Nacos（服务注册与发现）
docker run -d --name nacos -p 8848:8848 nacos/nacos-server

# 2. 启动 Control Plane 服务（可并行）
java -jar dish-planner/target/dish-planner.jar
java -jar dish-policy/target/dish-policy.jar
java -jar dish-memory/target/dish-memory.jar

# 3. 启动 Agent 服务（可并行）
java -jar dish-agent-dish/target/dish-agent-dish.jar
java -jar dish-agent-workorder/target/dish-agent-workorder.jar
java -jar dish-agent-chat/target/dish-agent-chat.jar

# 4. 启动网关
java -jar dish-gateway/target/dish-gateway.jar
```

### 微服务测试

```bash
# 调用网关 API
curl -X POST http://localhost:8080/api/chat/process \
  -H "Content-Type: application/json" \
  -H "X-Store-Id: STORE_001" \
  -d '{"message": "宫保鸡丁是什么菜系？"}'

# 健康检查
curl http://localhost:8080/api/chat/health

# 编排预览
curl -X POST http://localhost:8080/api/control/plan-preview \
  -H "Content-Type: application/json" \
  -H "X-Store-Id: STORE_001" \
  -d '{"message": "查询订单123并给出后续建议", "sessionId": "SESSION_DEMO"}'

# 查询会话记忆时间线
curl "http://localhost:8080/api/control/sessions/SESSION_DEMO/memory?limit=10" \
  -H "X-Store-Id: STORE_001"

# 查询语义召回解释
curl "http://localhost:8080/api/control/sessions/SESSION_DEMO/memory/retrieval?query=经理审核退款要记录什么&layers=SHORT_TERM_SESSION,LONG_TERM_KNOWLEDGE" \
  -H "X-Store-Id: STORE_001"

# 查询某个 session 最近一次 execution
curl "http://localhost:8080/api/control/sessions/SESSION_DEMO/executions/latest" \
  -H "X-Store-Id: STORE_001"

# 查询 execution DAG 快照
curl "http://localhost:8080/api/control/executions/exec-12345678" \
  -H "X-Store-Id: STORE_001"

# 查询 execution 历史回放
curl "http://localhost:8080/api/control/executions/exec-12345678/replay" \
  -H "X-Store-Id: STORE_001"

# 查询审批票据
curl http://localhost:8080/api/control/sessions/SESSION_DEMO/approvals/APR-12345678 \
  -H "X-Store-Id: STORE_001"

# 审批通过
curl -X POST http://localhost:8080/api/control/sessions/SESSION_DEMO/approvals/APR-12345678/approve \
  -H "Content-Type: application/json" \
  -H "X-Store-Id: STORE_001" \
  -d '{"decidedBy":"ops-user","decisionReason":"人工审核通过"}'

# 控制面 Dashboard
curl "http://localhost:8080/api/control/dashboard/overview?limit=10" \
  -H "X-Store-Id: STORE_001"

# 打开控制面页面
open http://localhost:8080/control/dashboard

# Prometheus 指标
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8091/actuator/prometheus
curl http://localhost:8092/actuator/prometheus
curl http://localhost:8093/actuator/prometheus
```

### 运行教学演示示例

```bash
# 提示模板示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.basics.PromptTemplateExample" -s settings-test.xml

# 基础对话示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.basics.BasicChatExample" -s settings-test.xml

# RAG/Embedding 外部 API 集成测试（默认单元测试会跳过）
RUN_LANGCHAIN4J_DEMO_INTEGRATION=true mvn test -pl langchain4j-demo -s settings-test.xml
```

## 项目结构

```
dish-agent/
├── pom.xml                              # 父POM（Spring Cloud Alibaba 2023.0.1.0 + Dubbo 3.2.4）
├── settings-test.xml                     # Maven 配置
│
├── dish-common/                          # 公共模块
│   └── src/main/java/com/example/dish/
│       ├── agent/ReActState.java         # ReAct状态机
│       ├── classifier/IntentType.java   # 意图枚举
│       ├── context/AgentContext.java      # 上下文传递
│       ├── contract/
│       │   ├── AgentResponse.java        # 统一响应
│       │   └── RoutingDecision.java      # 路由决策
│       ├── react/
│       │   ├── ReActEngine.java          # ReAct引擎接口
│       │   └── AbstractReActEngine.java  # ReAct执行器抽象类
│       └── rpc/
│           ├── DishAgentService.java     # 菜品Agent Dubbo接口
│           ├── WorkOrderAgentService.java # 工单Agent Dubbo接口
│           └── ChatAgentService.java     # 闲聊Agent Dubbo接口
│
├── dish-control-api/                     # Control Plane 独立契约层
│   └── src/main/java/com/example/dish/control/
│       ├── planner/                      # planner 请求/响应与服务接口
│       ├── policy/                       # policy 请求/响应与服务接口
│       ├── memory/                       # memory 请求/响应与服务接口
│       └── approval/                     # 审批票据创建/查询/决策契约
│
├── dish-gateway/                         # 网关服务（8080端口）
│   └── src/main/java/com/example/dish/gateway/
│       ├── GatewayApplication.java       # Spring Boot 启动类
│       ├── controller/ChatController.java # HTTP 入口
│       ├── controller/ControlPlaneController.java # 控制面入口
│       ├── service/
│       │   ├── DubboClientService.java    # Dubbo 客户端调用
│       │   ├── ResponseAggregator.java    # 结果聚合
│       │   ├── OrchestrationControlService.java # 编排/策略/记忆控制面
│       │   └── ControlPlaneQueryService.java # 会话时间线/审批票据查询
│       └── agent/RoutingAgent.java        # 意图识别路由
│
├── dish-planner/                         # 执行规划服务（Dubbo 20884）
├── dish-policy/                          # 策略审批服务（Dubbo 20885）
├── dish-memory/                          # 记忆时间线 + 长期知识服务（Dubbo 20886）
│
├── dish-agent-dish/                      # 菜品知识Agent（Dubbo 20881）
│   └── src/main/java/com/example/dish/
│       ├── DishAgentApplication.java      # @EnableDubbo 启动类
│       ├── rag/
│       │   ├── RAGPipeline.java            # RAG两阶段检索管道
│       │   └── EmbeddingService.java      # 向量化服务
│       └── service/
│           ├── DishAgentServiceImpl.java # @DubboService 实现
│           └── DishReActAgent.java        # 内部ReAct多步推理
│
├── dish-agent-workorder/                 # 工单处理Agent（Dubbo 20882）
│   └── src/main/java/com/example/dish/
│       ├── WorkOrderAgentApplication.java
│       ├── tools/                         # 业务工具
│       │   ├── InventoryTools.java
│       │   ├── OrderTools.java
│       │   └── RefundTools.java
│       └── service/
│           ├── WorkOrderAgentServiceImpl.java
│           └── WorkOrderReActAgent.java
│
├── dish-agent-chat/                      # 闲聊Agent（Dubbo 20883）
│   └── src/main/java/com/example/dish/
│       ├── ChatAgentApplication.java
│       └── service/ChatAgentServiceImpl.java
│
├── langchain4j-demo/                     # 教学演示模块（保留）
│   └── src/main/java/com/example/langchain4jdemo/
│       ├── basics/
│       │   ├── BasicChatExample.java
│       │   └── PromptTemplateExample.java
│       ├── memory/MemoryExample.java
│       ├── tool/ToolCallingExample.java
│       ├── rag/
│       │   ├── DocumentRAGExample.java
│       │   └── EmbeddingsExample.java
│       └── ...
│
└── langchain4j-enterprise/               # 单体版（保留，迁移中）
```

## 微服务架构说明

### 协作流程

```
用户请求 → Gateway:8080 → RoutingAgent (意图识别)
                                    ↓
                        Planner → Policy → Memory
                                    ↓
                    ┌───────────────┼───────────────┐
                    ↓               ↓               ↓
            dish-agent-dish  dish-agent-work  dish-agent-chat
              (Dubbo)          (Dubbo)         (Dubbo)
                  ↓               ↓
              RAGPipeline   Inventory/Order/Refund
```

### 意图类型 (IntentType)
- `GREETING` / `GENERAL_CHAT` → dish-agent-chat（直接对话）
- `DISH_QUESTION` / `DISH_INGREDIENT` / `DISH_COOKING_METHOD` / `POLICY_QUESTION` → dish-agent-dish（RAG）
- `QUERY_INVENTORY` / `QUERY_ORDER` / `CREATE_REFUND` → dish-agent-workorder（业务工具）

### 核心组件

1. **ReActEngine** - ReAct 多步推理引擎接口
   - `AbstractReActEngine` 提供通用流程
   - 子类实现：think(), decideAction(), executeAction()

2. **AgentContext** - 跨 Agent 状态传递
   - 字段: sessionId, intent, storeId, orderId, dishName, refundReason, userInput

3. **Dubbo 接口** - 服务间通信契约
   - `DishAgentService`, `WorkOrderAgentService`, `ChatAgentService`
   - `ExecutionPlannerService`, `PolicyDecisionService`, `MemoryReadService`, `MemoryTimelineService`, `ApprovalTicketService`

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Cloud Alibaba | 2023.0.1.0 |
| Dubbo | 3.2.4 |
| Nacos | 服务注册与发现 |
| LangChain4j | 1.12.2 |
| Java | 17+ |

## 依赖管理

在根 `pom.xml` 中统一管理：

- **Spring Boot**: 3.2.0
- **Spring Cloud**: 2023.0.0
- **Spring Cloud Alibaba**: 2023.0.1.0
- **Dubbo**: 3.2.4
- **LangChain4j**: 1.12.2
- **langchain4j-milvus**: 1.0.0-beta5
- **langchain4j-cohere**: 1.0.0-beta5

## 环境要求

- **Java 17+** 必需
- **Docker** 用于 Nacos
- **Minimax API 密钥** 在 `application.yml` 中配置
- **Milvus**（可选，用于生产环境向量存储）

## 故障排除

1. **Nacos 连接失败** - 确保 Nacos 已启动：`docker ps | grep nacos`
2. **Dubbo 服务不可达** - 检查服务是否注册到 Nacos
3. **编译错误** - 确保使用 Java 17+：`java -version`
4. **RAG 无结果** - 检查 Milvus 是否运行（生产环境）

## 生产化改造约定（2026-04）

1. **会话与租户**
- `sessionId` 必须在 `ChatController -> ChatExecutionService -> RoutingAgent -> AgentContext` 全链路传递。
- 优先从请求头 `X-Store-Id` 绑定会话店铺，若未提供则使用会话内已有值；首次缺失时回退 `STORE_001`。

2. **可观测性**
- 网关通过 `TraceIdFilter` 注入 `X-Trace-Id`，日志格式必须包含 `traceId=%X{traceId}`。
- 新增接口必须考虑 traceId 透传，便于跨服务排障。

3. **RAG 知识维护**
- 菜品/政策知识应存放于 `dish-agent-dish/src/main/resources/rag/knowledge/*.md`。
- 禁止再把业务知识以长文本硬编码在 `RAGPipeline` 中。

4. **ReAct 实现约束**
- `dish-agent-dish` 与 `dish-agent-workorder` 的多步推理必须基于 `AbstractReActEngine` 扩展实现。
- 业务 Agent 门面类负责上下文组织和响应转换，不重复实现 ReAct 主循环。

5. **工单后端接入约束**
- 工单工具层必须通过 `WorkOrderBackendGateway` 访问数据源，禁止在 `InventoryTools/OrderTools/RefundTools` 内直接写数据。
- 默认使用 `backend.mode=mock`；联调或生产环境使用 `backend.mode=http` 并配置 `backend.base-url`。
- `backend.mode=http` 默认依赖 `/api/backend/*` 路径族（库存、订单、退款），联调方需遵循该契约或同步调整适配器实现。
- 建议显式配置 `backend.connect-timeout-ms` 与 `backend.read-timeout-ms`，避免联调环境下默认超时不一致。
- 后端联调契约草案维护在 `docs/openapi/workorder-backend.yaml`，接口变更需同步更新该文件与适配器校验逻辑。

6. **会话存储约束**
- 网关会话存储通过 `session.store.type` 切换：`memory`（默认）或 `redis`（生产推荐）。
- `redis` 模式下应配置 `REDIS_HOST/REDIS_PORT` 等连接参数，保证多实例共享会话店铺绑定。

7. **测试基线**
- 微服务主线至少维护网关层单元测试（会话绑定、路由上下文传递）。
- 对路由、会话、RAG 加载逻辑的改动，必须同步补充测试或回归验证记录。

8. **控制面约束**
- `POST /api/control/plan-preview` 只做编排预览，不允许真实执行 Agent。
- 审批票据应通过 `ApprovalTicketCodec` 以稳定文本格式写入记忆服务，避免跨模块 JSON 版本冲突。
- `dish-memory` 除基础召回外，还应支持基于 `memoryType`、关键字、元数据过滤的时间线查询。
- `dish-memory` 需要支持按租户维度跨会话聚合时间线，以服务 Dashboard 聚合接口。
- 审批必须走 `ApprovalTicketService` 完成创建、查询、批准/拒绝闭环，避免状态只散落在网关内存中。
- `dish-gateway` 仅依赖 `dish-control-api`，不要再直接依赖 control plane provider 实现模块中的接口类。
- 可视化页面入口固定为 `GET /control/dashboard`；新增控制台功能时，优先复用现有控制面 API，而不是另开平行接口。
- Mission Control 控制台新增展示时，优先围绕现有 execution graph / replay / traceId 构造 Grafana deep link，不要再造第二套运行态入口。
- execution runtime 统一由 Gateway 产出事件、`dish-memory` 持久化 graph snapshot / replay / latest execution 索引；不要在页面层拼装运行态。
- `GET /api/control/executions/{executionId}/stream` 必须输出稳定 JSON SSE 事件，历史回放接口与实时流接口复用同一事件 DTO。
- `/api/chat/process` 的真实执行链路必须持续写入 `ExecutionRuntimeWriteService`，不能只在末尾写一条摘要。
- 所有微服务都应暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`；新增服务时同步补上 `spring-boot-starter-actuator` 和 Prometheus registry。
- Gateway 的 execution runtime 观测指标由 `ExecutionMetricsService` 统一维护；新增运行态事件时优先补指标而不是散落在控制器里。
- Gateway 关键执行路径的手工 span 统一通过 `GatewayExecutionTracing` 维护；新增 step 执行或恢复链路时，优先补 span 而不是只打日志。
- Dubbo Provider 侧的 trace 恢复统一通过 `DubboOpenTelemetrySupport` 完成；不要在各服务里重复手写 attachment 解析。
- 观测启动资源固定维护在 `ops/observability/`，包括 `docker-compose.yml`、Prometheus 抓取配置、Grafana dashboard、OTLP Collector 和 Tempo 配置。
- `ops/observability/grafana/dashboards/dish-agent-mission-control.json` 必须持续兼容控制台传入的 `traceId`、`executionId`、`targetAgent`、`focusService` 变量；调整变量名时需同步更新控制台链接生成逻辑。
- `memory.mode=redis` 时，时间线与审批票据必须可在服务重启后恢复；本地测试允许退回 `bootstrap` 内存模式。
- 长期记忆默认采用双层结构：Redis 存储时间线与审批票据，Milvus 负责 `LONG_TERM_KNOWLEDGE` 语义召回；不要把 `MemoryReadService` 退化回纯字符串匹配。
- 记忆写入必须分层：`SHORT_TERM_SESSION` 用于执行摘要和短期会话状态，`APPROVAL` 用于审批票据与审批决策，`LONG_TERM_KNOWLEDGE` 用于跨会话可复用经验和启动预热知识。
- `dish-memory/src/main/resources/memory/knowledge/*.md` 是长期知识预热入口；新增长期知识样例时，优先放在这里而不是散落进 Java 常量。
- 控制台中的“Semantic Recall Explorer”依赖 `GET /api/control/sessions/{sessionId}/memory/retrieval`；如果调整召回 DTO，必须同步更新控制台展示与测试。
- 控制台中的 Mission Control DAG 依赖 `GET /api/control/sessions/{sessionId}/executions/latest`、`GET /api/control/executions/{executionId}`、`GET /api/control/executions/{executionId}/replay` 和 execution SSE stream；调整 DTO 时必须同步更新控制台展示与测试。
- 控制台中的 Trace Bridge 依赖 execution graph 内的 `traceId`、节点 `targetAgent` 和时间窗；若调整 execution DTO 字段，必须同步更新 Grafana deep link 和 service graph 展示。
- `memory.retrieval.vector-dim`、`memory.retrieval.keyword-weight`、`memory.retrieval.vector-weight` 属于检索质量调优参数，修改时应补充回归测试或效果说明。
- `memory.long-term.provider`、`memory.long-term.milvus.*`、`memory.long-term.embedding-provider` 属于长期知识层配置，修改后需同步校验本地 fallback 与 Milvus 路径都可用。
- 新增控制面接口时，优先考虑面试展示价值：可解释编排、审批闭环、记忆时间线、trace 关联。
- trace 传播规范固定为：HTTP 使用 `X-Trace-Id`，Dubbo RPC attachment 使用 `traceId`，日志模式使用 `traceId=%X{traceId}`。
- 默认 OTLP 追踪上报端点为 `http://localhost:4318/v1/traces`；调整该配置时需同步检查本地 `ops/observability/` 栈与各微服务 `management.otlp.tracing.endpoint`。

## 约束

在建议任何命令之前，请先验证该命令是否存在于当前的 Codex 版本中。切勿在未确认已实现的情况下建议 /sessions、/history 或其他命令。

## 模块分层执行约定

本节面向 Agent，说明当前代码整理过程中已经形成的实现边界。README.md 只保留面向阅读者的目录说明，这里保留可执行的约束。

### dish-memory

- `service.impl`
  - 只保留 Dubbo Provider 门面职责：参数校验、步骤编排、结果返回。
  - 不再承载 tracing 模板、复杂工具方法、运行态投影、召回排序、存储细节。
  - 审批票据创建、决策应用和时间线写入应委托 `approval` 领域支撑组件，不要重新塞回 Provider 门面。

- `storage`
  - 负责 Redis / 内存模式下的数据读写与存储适配。
  - `MemoryEntryStorage` 以写入职责为主。
  - 时间线查询与召回候选收集应放在独立查询存储组件中。
  - 向量缓存与向量分数计算应放在独立向量索引组件中。
  - 长期记忆向量存储应保持编排职责，文档装配、EmbeddingModel 构造、Milvus/InMemory store 构造应放在独立组件中。

- `retrieval`
  - 负责召回编排、混合检索、结果排序、解释与结果组装。
  - `MemoryReadServiceImpl` 只能委托该层，不要把召回细节重新塞回门面类。

- `runtime`
  - 负责 execution runtime 的 graph 投影和状态演算。
  - 运行态投影器不应再放回 `service.impl`。

- `support`
  - 只保留 memory 模块强语义支撑，例如 Redis key、memory codec、memory 向量化。
  - 如果某个支撑类已经不依赖 memory 语义，应优先上收到 `dish-common`。

- `approval`
  - 负责审批票据装配、审批决策应用、审批时间线写入。
  - 不直接承担 Dubbo Provider 职责，也不访问 controller / gateway 层类型。

### dish-planner / dish-policy

- `service.impl`
  - 只保留 Dubbo Provider 门面职责和规则编排入口。
  - 可以保留轻量规则判断，但要补清晰的步骤注释，避免把“为什么这样判定”藏进一长串 if/switch。
  - 复杂规则图构建优先放到 `support`，跨模块共享的 targetAgent、executionMode、policyId 文本优先使用 `dish-common` 常量。

### dish-agent-dish / dish-agent-workorder / dish-agent-chat

- Provider 门面类
  - 只负责 Dubbo 对外入口和最小参数整理。
  - 不要把复杂 ReAct 状态流、RAG 检索细节或后端适配逻辑塞回 Provider。

- Agent/ReAct 类
  - 负责上下文整理、步骤编排、最终响应转换。
  - 多步骤方法必须补中文步骤注释，尤其是“第一次检索 -> 反思重试 -> 最终回答”这类链路。
  - AgentResponse / AgentContext 的重复装配优先复用 `dish-common` 的静态工厂或 copyBuilder，不要在各 Agent 模块手写字段复制。

- Tools / RAG / backend
  - `tools` 只提供业务动作入口，不直接承担门面职责。
  - `backend` 只负责数据源适配。
  - `rag` 只负责知识预热、检索、重排、生成，不把 Dubbo 门面逻辑带进来。

### dish-gateway

- `controller`
  - 只保留 HTTP 入参与响应出参处理，不下沉复杂编排细节。

- `service.impl`
  - 负责会话绑定、路由编排、控制面查询、execution 恢复和响应聚合。
  - 聊天主链路执行循环应由 `ChatExecutionService` 承接，Controller 不直接维护 planner/policy/dispatch/runtime event 循环。
  - 首次执行与审批恢复的 step dispatch/span/latency 模板应复用 `ExecutionStepRunner`，避免新增节点状态时两条链路漏改。
  - 类内可以保留必要的转换方法，但如果出现大段重复 DTO 组装或 metadata 提取，应优先考虑抽到 `support` 或专门 assembler。
  - Dashboard 总览聚合应放在 `support` 的 assembler 中，查询门面只负责调用 control plane RPC 并补齐必要查询函数。

- `observability`
  - 负责指标和 tracing，不要把业务编排分支塞入观测类。

## API 探索

当使用不熟悉的库 API 时，可使用 Agent 工具来探索代码库，找到正确的方法签名后再进行实现。可使用 `api-explore` skill 来启动探索。
