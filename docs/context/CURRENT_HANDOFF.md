# CURRENT_HANDOFF.md

Load order:
1. `docs/phases/phase-06-rag-2.0/HANDOFF.md`
2. 当前阶段配套文档：
   `docs/prd/phase-06-rag-2.0.md`
   `docs/technical/phase-06-rag-architecture.md`
   `docs/testing/phase-06-rag-2.0-test-plan.md`
3. 在进入正式实现、阶段切换或中途重规划前，读取：
   `docs/process/DEVELOPMENT_FLOW.md`
4. 恢复执行时遵循 phase flow：
   `创建 / 切换 phase 分支 -> 实现 -> 最小验证 -> 验收 -> 合并`
5. 需要 `docs/` 目录 ownership 或辅助层规则时，读取：
   `docs/README.md`
6. 只有在需要长期判断或后续阶段信息时，才继续读取 `DESIGN.md` 与 roadmap

Current phase:
Phase 6 `RAG 2.0 — 多路召回与智能检索` 处于规划完成、待进入实现前的状态。

Phase goal:
在不改变现有 `MemoryReadService` 对外契约的前提下，把检索基础设施升级为多路召回、级联重排、动态 TopK 与领域化 chunk 策略，并为后续 Agentic RAG 预留可复用底座。

Branch and commit policy:
- 仓库当前位于 `main`
- 代码工作区存在未提交改动，主要集中在 `dish-gateway` / `dish-memory` 的执行边界重构与本轮文档整理
- 开始 Phase 6 实现前，应先确认并收敛当前未提交改动，再创建 `phase-06-rag-2.0` 分支
- 未明确要求前，不自动提交、不强推

In scope:
- 维护当前规范化后的项目文档层级和 Phase 6 文档包
- 在进入实现前保持 Phase 6 的 PRD、技术设计、测试计划、实现计划、handoff、acceptance 边界清晰
- 保持 Phase 6 与后续 Agentic RAG 的边界清晰

Out of scope:
- 直接开始 Phase 6 代码实现
- 将 Agentic RAG 提前并入 Phase 6 执行范围
- 重写已完成 phase 的历史事实或 acceptance 证据

Key artifacts:
- `README.md`
- `DESIGN.md`
- `docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md`
- `docs/process/DEVELOPMENT_FLOW.md`
- `docs/phases/phase-06-rag-2.0/HANDOFF.md`
- `docs/prd/phase-06-rag-2.0.md`
- `docs/technical/phase-06-rag-architecture.md`
- `docs/testing/phase-06-rag-2.0-test-plan.md`
- `docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md`
- `docs/phases/phase-06-rag-2.0/ACCEPTANCE.md`
- `docs/discovery/PRODUCT_BRIEF.md`
- `docs/discovery/AGENTIC_RAG_DESIGN.md`

Owned files / files to avoid:
- 本 handoff 只拥有“当前执行态摘要”，不复制 PRD、技术设计、roadmap 的长内容
- `docs/discovery/` 是上游输入目录，不要把它当作当前执行态的 source of truth
- 若需补充模块分层约束，写回 `AGENTS.md`，不要继续堆到 handoff

Verification commands:
```bash
git status --short
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false
```

Next work:
1. 审查当前文档体系是否仍有 layering / wording / ownership 问题
2. 确认当前 `main` 上未提交的 gateway/memory 重构是否需要先收尾
3. 收敛并提交文档体系重整
4. 创建 `phase-06-rag-2.0` 分支
5. 按 Phase 6 文档包进入实现

Context budget rule:
默认只加载 handoff、开发流程和当前 phase 文档。除非出现命名冲突、阶段边界不清或历史决策矛盾，否则不要一次性读取全部技术设计与 ADR。
