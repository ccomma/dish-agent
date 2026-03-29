# 餐饮智能助手 (dish-agent)

基于 LangChain4j 的多 Agent 协同架构实现，用于餐饮 SaaS 场景的智能客服系统。

## 项目概述

本项目展示了如何使用 LangChain4j 框架构建企业级 AI 应用，包括：

- **多 Agent 协同架构**：前置路由 Agent、菜品知识 Agent、工单处理 Agent 的协作
- **意图识别与动态路由**：基于 LLM 的前置意图分类与分发
- **垂直领域 RAG**：餐饮知识库的检索增强生成
- **Function Calling**：与业务系统的工具调用集成

## 项目结构

```
dish-agent/
├── langchain4j-demo/          # 教学演示模块（6个独立示例）
├── langchain4j-enterprise/    # 企业级多Agent架构
└── pom.xml                   # Maven 父 POM
```

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- Minimax API 密钥（用于云端示例）

### 2. 配置 API 密钥

编辑 `langchain4j-enterprise/src/main/resources/config.properties`：

```properties
MINIMAX_API_KEY=your_api_key_here
MINIMAX_BASE_URL=https://api.minimax.chat/v1
MINIMAX_MODEL=minimax-m2.7
```

### 3. 编译项目

```bash
mvn clean compile
```

### 4. 运行多 Agent 架构

```bash
# 启动交互式对话
mvn exec:java -Dexec.mainClass="com.enterprise.langchain4j.Bootstrap"

# 或运行模块测试
mvn exec:java -Dexec.mainClass="com.enterprise.langchain4j.MultiAgentTest"
```

## 功能演示

### 菜品咨询
```
用户: 宫保鸡丁是什么菜？
路由: dish-knowledge (RAG)
回答: 宫保鸡丁是一道经典川菜，分类为川菜，辣度2级（微辣），价格38元...
```

### 库存查询
```
用户: 门店还有宫保鸡丁吗？
路由: work-order (InventoryTools)
回答: 【库存查询结果】
      • 宫保鸡丁: 50份 (有货)
```

### 订单查询
```
用户: 查询订单12345的状态
路由: work-order (OrderTools)
回答: 【订单查询结果】
      订单号: 12345
      门店: STORE_001
      商品: 宫保鸡丁 x1, 麻婆豆腐 x1
      状态: 配送中
```

### 退款申请
```
用户: 我要退款，订单号67890，因为菜凉了
路由: work-order (RefundTools)
回答: 【退款工单创建成功】
      工单号: TK1234567890
      订单号: 67890
      原因: 菜凉了
      状态: 待处理
```

## 多 Agent 架构

### 组件说明

| Agent | 职责 | 处理意图 |
|-------|------|----------|
| OrchestrationAgent | 协调入口，分发请求 | 系统唯一入口 |
| RoutingAgent | 意图识别，参数抽取 | 所有输入先行路由 |
| DishKnowledgeAgent | 菜品知识问答 | DISH_QUESTION, DISH_INGREDIENT, DISH_COOKING_METHOD, POLICY_QUESTION |
| WorkOrderAgent | 业务操作处理 | QUERY_INVENTORY, QUERY_ORDER, CREATE_REFUND |

### 意图 → Agent 映射

```
GREETING / GENERAL_CHAT     → ChatAgent（直接对话）
DISH_QUESTION / DISH_INGREDIENT / DISH_COOKING_METHOD / POLICY_QUESTION → DishKnowledgeAgent
QUERY_INVENTORY / QUERY_ORDER / CREATE_REFUND → WorkOrderAgent
```

### 上下文传递

每个 Agent 处理后会返回 `AgentResponse`，包含：
- `success`: 是否成功
- `content`: 响应内容
- `agentName`: 来源 Agent
- `context`: 更新后的上下文（传递给下一步）
- `followUpHints`: 后续操作提示

## 企业级模块 (langchain4j-enterprise)

### 包结构

```
com.enterprise.langchain4j/
├── agent/                    # Agent 实现
│   ├── OrchestrationAgent.java
│   ├── RoutingAgent.java
│   ├── DishKnowledgeAgent.java
│   └── WorkOrderAgent.java
├── context/                  # 上下文传递
│   └── AgentContext.java
├── contract/                 # 契约定义
│   ├── AgentResponse.java
│   └── RoutingDecision.java
├── tool/                     # 业务工具
│   ├── InventoryTools.java
│   ├── OrderTools.java
│   └── RefundTools.java
├── classifier/               # 意图分类
│   ├── IntentClassifier.java
│   └── IntentType.java
├── rag/                      # RAG管道
│   └── RAGPipeline.java
├── Bootstrap.java            # 启动入口
└── Config.java               # 配置加载
```

## 依赖说明

- **langchain4j** (0.31.0): LangChain4j 核心库
- **langchain4j-open-ai**: OpenAI/Minimax 集成
- **langchain4j-ollama**: Ollama 本地模型集成
- **langchain4j-milvus**: Milvus 向量数据库集成
- **langchain4j-elasticsearch**: Elasticsearch 向量存储集成
- **slf4j-simple**: 日志实现

## 学习路径

1. **langchain4j-demo 模块**：学习 LangChain4j 基础用法
   - BasicChatExample → 对话基础
   - PromptTemplateExample → 提示词工程
   - DocumentRAGExample → RAG 实现
   - ToolCallingExample → 函数调用
   - LocalModelExample → 本地模型
   - StructuredOutputExample → 结构化输出

2. **langchain4j-enterprise 模块**：学习企业级多 Agent 架构
   - MultiAgentTest → 模块测试
   - Bootstrap → 启动入口
   - 各 Agent 源码 → 架构设计

## 参考资源

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [Minimax API 文档](https://api.minimax.chat/)

## 许可证

MIT 许可证
