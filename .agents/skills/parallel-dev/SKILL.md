# Parallel Agent Development Skill

当需要进行复杂功能开发时，使用并行 Agent 模式同时推进多个方面的工作。

## 触发条件

当用户请求开发新功能，且该功能涉及：
- 核心业务逻辑 + 测试 + 文档
- 或任何需要同时处理多个独立方面的任务

## 并行 Agent 分配

### Agent 1: impl（实现）
- 负责编写核心业务逻辑
- 确保代码符合项目规范
- 遵循 LangChain4j 1.x API

### Agent 2: tests（测试）
- 负责编写全面的测试套件
- 使用 JUnit 5
- 使用 InMemoryEmbeddingStore 进行测试（无需外部服务）
- 每次修改后运行 `mvn test -pl {module} -s settings-test.xml` 验证

### Agent 3: docs（文档）
- 负责创建/更新 README
- 负责编写 API 文档
- 确保文档与代码一致

## 执行流程

1. 分析任务，确定三个 Agent 的具体分工
2. 同时启动三个 Agent（使用 `run_in_background: true`）
3. 等待所有 Agent 完成
4. 合并结果
5. 运行最终集成测试：`mvn test -pl {module} -s settings-test.xml`

## 适用模块

- langchain4j-demo
- langchain4j-enterprise
- 未来新建的任何模块

## 注意事项

- 三个 Agent 独立工作，避免重复劳动
- 如遇 API 版本问题，使用 Agent 工具探索正确的 API 签名
- 每次代码修改后必须验证编译
