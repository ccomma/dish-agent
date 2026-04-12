# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

**重要**：每次对项目进行修改后，请检查是否需要同步更新本文件和 README.md，以确保文档与代码保持一致。

## 项目概述

这是一个 **LangChain4j 1.x 教学项目**，包含两个模块：

### langchain4j-demo 模块（教学演示）
展示 LangChain4j 1.x 核心 API 的完整示例，包括：
- 基础对话 (BasicChatExample)
- 提示模板 (PromptTemplateExample) - 使用 `PromptTemplate` API
- 文档 RAG (DocumentRAGExample)
- 工具调用 (ToolCallingExample)
- 本地模型 (LocalModelExample)
- 结构化输出 (StructuredOutputExample)
- 记忆 (MemoryExample)
- 链式调用 (ChainExample)
- 流式输出 (StreamingExample)
- 输出解析 (OutputParserExample)
- 向量嵌入 (EmbeddingsExample) - 包括 InMemory/Milvus 向量存储和 Reranking 演示

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
mvn compile -s settings-test.xml

# 清理并编译
mvn clean compile -s settings-test.xml

# 打包为 JAR
mvn package -s settings-test.xml
```

### 运行企业级多Agent示例
```bash
# 启动多Agent协同架构
mvn exec:java -Dexec.mainClass="com.enterprise.langchain4j.Bootstrap" -s settings-test.xml

# 运行模块测试
mvn exec:java -Dexec.mainClass="com.enterprise.langchain4j.MultiAgentTest" -s settings-test.xml
```

### 运行教学演示示例
```bash
# 提示模板示例（展示 PromptTemplate API）
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.basics.PromptTemplateExample" -s settings-test.xml

# 基础对话示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.basics.BasicChatExample" -s settings-test.xml

# 记忆示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.memory.MemoryExample" -s settings-test.xml

# RAG 示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.rag.DocumentRAGExample" -s settings-test.xml

# 工具调用示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.tool.ToolCallingExample" -s settings-test.xml

# 本地模型示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.model.LocalModelExample" -s settings-test.xml

# 结构化输出示例
mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.output.StructuredOutputExample" -s settings-test.xml
```

## 项目结构

```
dish-agent/
├── pom.xml                           # 父 POM
├── settings-test.xml                  # Maven 配置（不使用镜像）
├── langchain4j-demo/                 # 教学演示模块
│   └── src/main/java/com/example/langchain4jdemo/
│       ├── Config.java
│       ├── TestMinimax.java
│       ├── basics/
│       │   ├── BasicChatExample.java       # 基础对话
│       │   └── PromptTemplateExample.java  # 提示模板（PromptTemplate API）
│       ├── memory/
│       │   └── MemoryExample.java          # 记忆管理
│       ├── tool/
│       │   └── ToolCallingExample.java     # 工具调用
│       ├── chain/
│       │   └── ChainExample.java          # 链式调用
│       ├── rag/
│       │   ├── DocumentRAGExample.java    # 文档 RAG
│       │   └── EmbeddingsExample.java      # 向量嵌入
│       ├── streaming/
│       │   └── StreamingExample.java       # 流式输出
│       ├── output/
│       │   ├── StructuredOutputExample.java # 结构化输出
│       │   └── OutputParserExample.java    # 输出解析
│       └── model/
│           └── LocalModelExample.java      # 本地模型（Ollama）
├── langchain4j-enterprise/           # 企业级多Agent模块
│   └── src/main/java/com/enterprise/langchain4j/
│       ├── Bootstrap.java            # [启动入口]
│       ├── MultiAgentTest.java       # [模块测试]
│       ├── Config.java
│       ├── DishConsultingAgentTest.java
│       ├── agent/                    # Agent 实现
│       │   ├── OrchestrationAgent.java  # 编排协调层
│       │   ├── RoutingAgent.java       # 前置路由Agent
│       │   ├── DishKnowledgeAgent.java # 菜品知识Agent
│       │   └── WorkOrderAgent.java     # 工单处理Agent
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
│       └── rag/                     # RAG管道
│           └── RAGPipeline.java
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

## LangChain4j 1.x 关键 API

### 1. 模型配置
```java
// 使用 ChatModel 接口（替代旧的 ChatLanguageModel）
ChatModel model = OpenAiChatModel.builder()
    .apiKey(config.getApiKey())
    .baseUrl(config.getBaseUrl())
    .modelName(config.getModel())
    .temperature(0.7)
    .build();
```

### 2. AiServices 构建器模式
```java
// 使用 .chatModel() 方法（替代旧的 .chatLanguageModel()）
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(model)           // 新 API
    .chatMemory(memory)
    .tools(tool1, tool2)
    .build();
```

### 3. 消息构建
```java
// 使用静态工厂方法
List<ChatMessage> messages = List.of(
    SystemMessage.from("你是一个有帮助的助手"),
    UserMessage.from("你好")
);

// 或使用 .systemMessage() / .userMessage()
ChatResponse response = model.chat(messages);
String text = response.aiMessage().text();
```

### 4. PromptTemplate API（1.x 新特性）
```java
// 使用 PromptTemplate 进行模板渲染
PromptTemplate template = PromptTemplate.from(
    "请将{{text}}翻译成{{targetLanguage}}"
);

Prompt filled = template.apply(Map.of(
    "text", input,
    "targetLanguage", "中文"
));

String promptText = filled.text();
```

### 5. @Tool 注解模式
```java
public class MyTools {
    @Tool("查询库存")
    public String queryInventory(@P("门店ID") String storeId) {
        // implementation
    }
}
```

### 6. ChatMemory
```java
ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
```

### 7. EmbeddingStore 和相似度搜索
```java
// 使用 OpenAI EmbeddingModel
EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .apiKey(apiKey)
    .modelName("text-embedding-3-small")
    .build();

// 存入向量
EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
store.add(embeddingModel.embed("文本").content(), TextSegment.from("文本"));

// 相似度搜索
EmbeddingSearchResult<TextSegment> result = store.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddingModel.embed("查询").content())
        .maxResults(3)
        .build()
);

// 遍历结果
for (EmbeddingMatch<TextSegment> match : result.matches()) {
    System.out.println("[" + match.score() + "] " + match.embedded().text());
}
```

### 8. RAG with Reranking
```java
// 初步检索
EmbeddingSearchResult<TextSegment> initial = store.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(5)
        .build()
);

// Reranking 使用 Cohere ScoringModel
ScoringModel scoringModel = CohereScoringModel.builder()
    .apiKey(cohereApiKey)
    .build();

List<TextSegment> segments = initial.matches().stream()
    .map(EmbeddingMatch::embedded)
    .toList();

Response<List<Double>> scores = scoringModel.scoreAll(segments, queryText);
// 按分数排序后取 top-k
```

## 依赖管理

- **LangChain4j 版本**: `1.12.2`（在 pom.xml 中定义）
- **Java 版本**: 17+
- **核心依赖**:
  - `langchain4j` (核心)
  - `langchain4j-open-ai` (OpenAI/Minimax 兼容)
  - `langchain4j-ollama` (本地模型)
  - `langchain4j-milvus` (Milvus 向量数据库)
  - `langchain4j-cohere` (Cohere Reranking)

## 环境要求

- **Java 17+** 必需
- **Minimax API 密钥** 用于云端示例（在 `config.properties` 中配置）
- **Ollama** 用于本地模型示例（可选）

## 故障排除

1. **缺少 Minimax API 密钥** - 在 config.properties 中设置 MINIMAX_API_KEY
2. **Ollama 服务未运行** - 使用 `ollama serve` 启动并拉取所需模型
3. **编译错误** - 确保使用 Java 17+，运行 `mvn clean compile -s settings-test.xml`
4. **Maven 仓库连接问题** - 使用 `settings-test.xml` 配置指向 Maven Central

## 环境健康检查
在开始任何工作之前，运行完整的环境检查。可使用 `env-health-check` skill 启动自动化检查。

检查内容包括：Maven 仓库连接、凭证配置、依赖完整性、编译验证、API 版本兼容性。如发现问题会自动尝试修复或创建 Agent 处理。

## 约束
在建议任何命令之前，请先验证该命令是否存在于当前的 Claude Code 版本中。切勿在未确认已实现的情况下建议 /sessions、/history 或其他命令。

## API 探索
当使用不熟悉的库 API 时，可使用 Agent 工具来探索代码库，找到正确的方法签名后再进行实现。可使用 `api-explore` skill 来启动探索。

## 并行 Agent 开发模式
对于复杂功能，使用并行 Agent 模式同时开发。可使用 `parallel-dev` skill 来启动此模式。

1. **impl**：负责编写核心业务逻辑
2. **tests**：负责编写全面的测试套件
3. **docs**：负责创建 README 和 API 文档

此模式适用于所有模块（langchain4j-demo、langchain4j-enterprise）及未来新建模块。每个 Agent 独立工作，完成后合并并运行最终集成测试。
