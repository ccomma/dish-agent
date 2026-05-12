# Phase 6 交接文档

加载顺序：
1. `docs/process/DEVELOPMENT_FLOW.md`
2. `docs/prd/phase-06-rag-2.0.md`
3. `docs/technical/phase-06-rag-architecture.md`
4. `docs/testing/phase-06-rag-2.0-test-plan.md`
5. `docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md`
6. 恢复执行顺序：
   `创建 / 切换 phase-06-rag-2.0 分支 -> 按里程碑实现 -> 运行最小验证 -> 更新 ACCEPTANCE -> 合并`

当前阶段：
Phase 6 `RAG 2.0 — 多路召回与智能检索`

阶段目标：
把当前单路检索升级为可扩展的检索基础设施，覆盖双路召回、级联重排、动态 TopK、领域化 chunk 策略、缓存与索引优化，并保持对上游调用方的兼容。

分支与提交策略：
- 目标分支：`phase-06-rag-2.0`
- 当前仓库尚未创建该分支，且工作区仍有未提交改动
- 进入实现前先完成当前文档与重构改动的工作区收敛，再从稳定状态创建 phase 分支

当前范围：
- BM25 通道与向量通道并行召回
- RRF 融合
- 粗排 + 精排为主的级联重排
- 动态 TopK
- 餐饮领域 chunk 策略与元数据富化
- 检索缓存与向量索引优化

非范围：
- Agentic RAG 三层自主检索
- GraphRAG
- 多模态检索
- 跨 Agent 的策略共享

关键文档：
- PRD：`docs/prd/phase-06-rag-2.0.md`
- 技术设计：`docs/technical/phase-06-rag-architecture.md`
- 测试计划：`docs/testing/phase-06-rag-2.0-test-plan.md`
- 实现计划：`docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md`
- 验收记录：`docs/phases/phase-06-rag-2.0/ACCEPTANCE.md`
- 相关 ADR：`docs/adr/ADR-003-multi-recall-fusion.md`

文件归属与避免项：
- 本 handoff 只维护 Phase 6 的操作边界，不重复设计论证
- `docs/discovery/PRODUCT_BRIEF.md` 与 `docs/discovery/AGENTIC_RAG_DESIGN.md` 是未来方向输入，不能替代当前 phase 文档

验证命令：
```bash
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false
mvn test -pl dish-agent-dish,dish-memory -am -s settings-test.xml -DfailIfNoTests=false
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml
```

下一步：
1. 完成当前文档与工作区状态收敛
2. 建立 `RetrievalChannel` 抽象与双路召回编排边界
3. 明确 Elasticsearch 集成与索引管理方案
4. 为 `MemoryReadService` 的兼容改造补齐回归测试
5. 在实现前确认 Phase 6 acceptance 指标如何采样与记录

上下文预算规则：
实现过程中优先读取 PRD、技术设计、测试计划和实现计划；只有在后续阶段边界发生变化时，才回看 roadmap 和 discovery 文档。
