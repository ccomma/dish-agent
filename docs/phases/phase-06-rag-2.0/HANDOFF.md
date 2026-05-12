# Phase 6 HANDOFF

Load order:
1. `docs/process/DEVELOPMENT_FLOW.md`
2. `docs/prd/phase-06-rag-2.0.md`
3. `docs/technical/phase-06-rag-architecture.md`
4. `docs/testing/phase-06-rag-2.0-test-plan.md`
5. `docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md`
6. 恢复执行顺序：
   `创建 / 切换 phase-06-rag-2.0 分支 -> 按里程碑实现 -> 运行最小验证 -> 更新 ACCEPTANCE -> 合并`

Current phase:
Phase 6 `RAG 2.0 — 多路召回与智能检索`

Phase goal:
把当前单路检索升级为可扩展的检索基础设施，覆盖双路召回、级联重排、动态 TopK、领域化 chunk 策略、缓存与索引优化，并保持对上游调用方的兼容。

Branch and commit policy:
- 目标分支：`phase-06-rag-2.0`
- 当前仓库尚未创建该分支，且工作区仍有未提交改动
- 进入实现前先完成工作区收敛，再从稳定状态创建 phase 分支

In scope:
- BM25 通道与向量通道并行召回
- RRF 融合
- 粗排 + 精排为主的级联重排
- 动态 TopK
- 餐饮领域 chunk 策略与元数据富化
- 检索缓存与向量索引优化

Out of scope:
- Agentic RAG 三层自主检索
- GraphRAG
- 多模态检索
- 跨 Agent 的策略共享

Key artifacts:
- PRD：`docs/prd/phase-06-rag-2.0.md`
- 技术设计：`docs/technical/phase-06-rag-architecture.md`
- 测试计划：`docs/testing/phase-06-rag-2.0-test-plan.md`
- 实现计划：`docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md`
- 验收记录：`docs/phases/phase-06-rag-2.0/ACCEPTANCE.md`
- 相关 ADR：`docs/adr/ADR-003-multi-recall-fusion.md`

Owned files / files to avoid:
- 本 handoff 只维护 Phase 6 的操作边界，不重复设计论证
- `docs/discovery/PRODUCT_BRIEF.md` 与 `docs/discovery/AGENTIC_RAG_DESIGN.md` 是未来方向输入，不能替代当前 phase 文档

Verification commands:
```bash
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false
mvn test -pl dish-agent-dish,dish-memory -am -s settings-test.xml -DfailIfNoTests=false
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml
```

Next work:
1. 建立 `RetrievalChannel` 抽象与双路召回编排边界
2. 明确 Elasticsearch 集成与索引管理方案
3. 为 `MemoryReadService` 的兼容改造补齐回归测试
4. 在实现前确认 Phase 6 acceptance 指标如何采样与记录

Context budget rule:
实现过程中优先读取 PRD、技术设计、测试计划和实现计划；只有在后续阶段边界发生变化时，才回看 roadmap 和 discovery 文档。
