# CLAUDE.md

本文件是面向 Claude Code 的冷启动入口。进入仓库后不要一次性加载全部文档，先按下面顺序读取最小上下文，再按任务补充。

## 首次加载顺序

1. [docs/context/CURRENT_HANDOFF.md](docs/context/CURRENT_HANDOFF.md)
2. 当前阶段 handoff：
   [docs/phases/phase-06-rag-2.0/HANDOFF.md](docs/phases/phase-06-rag-2.0/HANDOFF.md)
3. 在进入正式实现、阶段切换或中途重规划前，读取
   [docs/process/DEVELOPMENT_FLOW.md](docs/process/DEVELOPMENT_FLOW.md)
4. 需要 `docs/` 目录 ownership 与辅助文档处理规则时，读取
   [docs/README.md](docs/README.md)
5. 需要长期产品边界与稳定架构约束时，读取
   [DESIGN.md](DESIGN.md)
6. 需要项目概览、启动方式与公开入口导航时，读取
   [README.md](README.md)

## 强制流程

开始任何正式代码工作前，必须遵循
[docs/process/DEVELOPMENT_FLOW.md](docs/process/DEVELOPMENT_FLOW.md)
定义的阶段流程：

`PRD → 技术设计 → 测试计划 → 代码实现 → 验收 → 合并`

如果当前任务是新增需求、阶段切换或中途重规划，先修正文档层，再更新
`CURRENT_HANDOFF.md`，不要直接进入实现。

## 必须知道的仓库约束

1. Gateway 只依赖 `dish-control-api`，不直接依赖 Provider 实现模块。
2. Agent 之间不直接通信，统一经 Gateway 编排。
3. 记忆读写统一走 `dish-memory` Dubbo 接口，Agent 不直接访问 Redis/Milvus。
4. 所有跨链路请求都要保持 `traceId` 透传：HTTP `X-Trace-Id`、Dubbo attachment `traceId`、日志 MDC `traceId=%X{traceId}`。
5. `docs/discovery/` 是上游输入，不是执行态文档。

## 何时继续加载长文档

- 需要产品定位、长期边界、稳定架构约束：读 [DESIGN.md](DESIGN.md)
- 需要项目概览、快速开始、公开导航：读 [README.md](README.md)
- 需要阶段顺序、退出条件、前置依赖：读 [docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md](docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md)
- 需要 `docs/` 目录 owns / must-not-own 规则：读 [docs/README.md](docs/README.md)
- 需要当前阶段 requirements / architecture / tests：读取对应 PRD、技术设计、测试计划
