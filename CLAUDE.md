# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

这是一个 **LangChain4j 教学项目**，包含两个模块：

### langchain4j-demo 模块（教学演示）
展示 LangChain4j 核心功能，共 6 个独立示例。

### langchain4j-enterprise 模块（企业级多Agent架构）
基于**多Agent协同架构**的餐饮智能助手，包含：
- **前置路由Agent** - 意图识别与动态路由
- **菜品知识Agent** - RAG 检索增强生成
- **工单处理Agent** - 业务操作（库存、订单、退款）
- **编排Agent** - 协调层，作为系统唯一入口

## 常用命令

### 构建和编译
```bash
# 编译项目
mvn compile

# 清理并编译
mvn clean compile

# 打包为 JAR
mvn package
```

### 运行企业级多Agent示例（推荐）
```bash
# 启动多Agent协同架构
mvn exec:java -Dexec.mainClass="com.enterprise.langchain4j.Bootstrap"

# 运行模块测试
mvn exec:java -Dexec.mainClass="com.enterprise.langchain4j.MultiAgentTest"
```

### 运行教学演示示例
```bash
# 基础对话示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.BasicChatExample"

# 提示模板示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.PromptTemplateExample"

# RAG 示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.DocumentRAGExample"

# 工具调用示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.ToolCallingExample"

# 本地模型示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.LocalModelExample"

# 结构化输出示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.StructuredOutputExample"
```

## 项目结构

```
dish-agent/
├── pom.xml                           # 父 POM
├── langchain4j-demo/                 # 教学演示模块
│   └── src/main/java/com/example/langchain4jdemo/
│       ├── BasicChatExample.java
│       ├── PromptTemplateExample.java
│       ├── DocumentRAGExample.java
│       ├── ToolCallingExample.java
│       ├── LocalModelExample.java
│       └── StructuredOutputExample.java
├── langchain4j-enterprise/           # 企业级多Agent模块
│   └── src/main/java/com/enterprise/langchain4j/
│       ├── Bootstrap.java            # [启动入口]
│       ├── MultiAgentTest.java       # [模块测试]
│       ├── agent/                    # Agent 实现
│       │   ├── OrchestrationAgent.java  # 编排协调层
│       │   ├── RoutingAgent.java         # 前置路由Agent
│       │   ├── DishKnowledgeAgent.java   # 菜品知识Agent
│       │   └── WorkOrderAgent.java       # 工单处理Agent
│       ├── context/                  # 上下文传递
│       │   └── AgentContext.java
│       ├── contract/                 # 契约定义
│       │   ├── AgentResponse.java
│       │   └── RoutingDecision.java
│       ├── tool/                     # 业务工具
│       │   ├── InventoryTools.java
│       │   ├── OrderTools.java
│       │   └── RefundTools.java
│       ├── classifier/               # 意图分类
│       │   ├── IntentClassifier.java
│       │   └── IntentType.java
│       ├── rag/                      # RAG管道
│       │   └── RAGPipeline.java
│       └── Config.java
├── run_example.sh
├── README.md
└── CLAUDE.md
```

## 多Agent架构说明

### 协作流程
```
用户输入 → OrchestrationAgent → RoutingAgent (路由决策)
                                    ↓
                    ┌───────────────┼───────────────┐
                    ↓               ↓               ↓
            DishKnowledge    WorkOrder        Chat
              Agent           Agent          Agent
                ↓               ↓
            RAGPipeline   Inventory/Order/Refund Tools
```

### 意图类型 (IntentType)
- `GREETING` / `GENERAL_CHAT` → ChatAgent（直接对话）
- `DISH_QUESTION` / `DISH_INGREDIENT` / `DISH_COOKING_METHOD` / `POLICY_QUESTION` → DishKnowledgeAgent（RAG）
- `QUERY_INVENTORY` / `QUERY_ORDER` / `CREATE_REFUND` → WorkOrderAgent（业务工具）

### 核心组件

1. **AgentContext** - 跨Agent状态传递载体
   - 字段: sessionId, intent, storeId, orderId, dishName, refundReason, userInput

2. **RoutingDecision** - 路由决策
   - intent, targetAgent, reason, context

3. **AgentResponse** - Agent统一响应
   - success, content, agentName, context, followUpHints

## 架构和关键模式

### 1. 模型配置模式
- 通过 `Config.java` 单例从 `config.properties` 加载配置
- 使用 `OpenAiChatModel.builder()...build()` 构建模型

### 2. AiServices 构建器模式
- 使用 `@SystemMessage` 注解定义接口行为
- 通过 `.tools()` 注入工具
- 通过 `.chatMemory()` 管理对话历史

### 3. @Tool 注解模式
- 定义 AI 可调用的工具方法
- 使用 `@P` 注解标注参数

### 4. RAG 实现模式
- 文档检索 → 上下文组装 → LLM 生成
- 使用关键词匹配（可扩展为向量检索）

## 依赖管理

- LangChain4j 版本: `0.31.0`（在 pom.xml 中定义）
- Java 版本: 17+
- 核心依赖:
  - `langchain4j` (核心)
  - `langchain4j-open-ai` (OpenAI/Minimax 兼容)
  - `langchain4j-ollama` (本地模型)
  - `langchain4j-milvus` / `langchain4j-elasticsearch` (向量存储，可选)

## 环境要求

- **Java 17+** 必需
- **Minimax API 密钥** 用于云端示例（在 `config.properties` 中配置）
- **Ollama** 用于本地模型示例（可选）

## 故障排除

1. **缺少 Minimax API 密钥** - 在 config.properties 中设置 MINIMAX_API_KEY
2. **Ollama 服务未运行** - 使用 `ollama serve` 启动并拉取所需模型
3. **编译错误** - 确保使用 Java 17+，运行 `mvn clean compile`
4. **版本不兼容** - 确保 LangChain4j 版本与 Java 版本匹配
