# AGENTS.md

本文件为 Codex (Codex.ai/code) 在此代码库中工作时提供指导。

**重要**：每次对项目进行修改后，请检查是否需要同步更新本文件和 README.md，以确保文档与代码保持一致。

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
| dish-memory | 20886 (Dubbo) | 会话记忆、审批票据、时间线查询 |
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
```

### 运行教学演示示例

```bash
# 提示模板示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.basics.PromptTemplateExample" -s settings-test.xml

# 基础对话示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.basics.BasicChatExample" -s settings-test.xml
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
├── dish-memory/                          # 记忆时间线服务（Dubbo 20886）
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
- `sessionId` 必须在 `ChatController -> RoutingAgent -> AgentContext` 全链路传递。
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
- `memory.mode=redis` 时，时间线与审批票据必须可在服务重启后恢复；本地测试允许退回 `bootstrap` 内存模式。
- 长期记忆默认采用双层结构：Redis 存储时间线与审批票据，向量检索层负责语义召回；不要把 `MemoryReadService` 退化回纯字符串匹配。
- `memory.retrieval.vector-dim`、`memory.retrieval.keyword-weight`、`memory.retrieval.vector-weight` 属于检索质量调优参数，修改时应补充回归测试或效果说明。
- 新增控制面接口时，优先考虑面试展示价值：可解释编排、审批闭环、记忆时间线、trace 关联。

## 约束

在建议任何命令之前，请先验证该命令是否存在于当前的 Codex 版本中。切勿在未确认已实现的情况下建议 /sessions、/history 或其他命令。

## API 探索

当使用不熟悉的库 API 时，可使用 Agent 工具来探索代码库，找到正确的方法签名后再进行实现。可使用 `api-explore` skill 来启动探索。
