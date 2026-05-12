# AGENTS.md

本文件是面向 Codex 等读取 `AGENTS.md` 的 agent 冷启动入口。进入仓库后不要一次性加载全部文档，先按下面顺序读取最小上下文，再按任务补充。

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

1. 注释使用中文；核心门面类、编排类、存储适配类、运行态投影类默认补中文类注释和步骤注释。
2. Gateway 只依赖 `dish-control-api`，不直接依赖 Provider 实现模块。
3. Agent 之间不直接通信，统一经 Gateway 编排。
4. 记忆读写统一走 `dish-memory` Dubbo 接口，Agent 不直接访问 Redis/Milvus。
5. `docs/discovery/` 是上游输入，不是执行态文档。
6. 修改代码后需要判断是否同步更新 `README.md`、`DESIGN.md`、`docs/README.md` 或 phase 文档。

## Skills 与触发规则

- serious product 文档修复或中途重规划：优先使用 `product-plan`
- 大架构重构或模块合并拆分：使用 `improve-codebase-architecture`
- 模块职责整理、代码可读性或结构收敛：使用 `engineering-baseline`
- 代码实现、调试、验证、review：不要把 `product-plan` 当成替代流程

## 常用验证命令

```bash
# 微服务主线编译
mvn compile -pl dish-common,dish-control-api,dish-memory,dish-planner,dish-policy,dish-gateway,dish-agent-dish,dish-agent-workorder,dish-agent-chat -am -s settings-test.xml

# 全量测试
mvn test -s settings-test.xml

# dish-memory 定向测试
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false
```
