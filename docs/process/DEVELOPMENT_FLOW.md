# DEVELOPMENT_FLOW.md

## Purpose

本文定义 `dish-agent` 从 phase 规划走向代码实现、验收和合并的正式流程。它的职责是保证 phase 执行顺序稳定、文档层不串位、阶段证据可追溯。

## Document Responsibility Boundary Summary

| 文档 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| `DESIGN.md` | 长期产品判断、原则、稳定架构边界 | 当前 phase 状态、分支步骤 |
| `docs/roadmap/PROJECT_DEVELOPMENT_PLAN.md` | phase 顺序、目标、输入输出、退出条件 | 逐任务实现切片 |
| `docs/prd/` | 阶段需求与成功标准 | 模块实现细节 |
| `docs/technical/` | 技术契约、模块边界、失败模式 | 产品定位、任务清单 |
| `docs/testing/` | 测试策略、fixture、回归风险 | acceptance 事实证据 |
| `docs/context/CURRENT_HANDOFF.md` | 当前执行态最小上下文 | 长设计历史 |
| `docs/phases/` | 单 phase 的执行包和证据 | 长期产品方向 |

## Context Loading Protocol

新会话或新 agent 承接开发时，按下面顺序加载：

1. `AGENTS.md` 或 `CLAUDE.md`
2. `docs/context/CURRENT_HANDOFF.md`
3. 当前阶段 `docs/phases/<phase>/HANDOFF.md`
4. 当前任务相关代码和测试
5. 在进入正式实现、阶段切换或中途重规划前，回读本文件确认 phase flow
6. 当 handoff 不足以回答边界问题时，再读取 roadmap、PRD、技术设计、测试计划或 `DESIGN.md`

`docs/interview/`、`docs/openapi/`、`docs/discovery/` 等辅助层默认不进入主加载链。

## Standard Phase Progression

所有正式开发默认遵循：

`PRD → 技术设计 → 测试计划 → 代码实现 → 验收 → 合并`

### 1. PRD

- 定义阶段为什么做、给谁做、做什么、不做什么
- 明确成功标准和边界

### 2. 技术设计

- 定义模块边界、接口、数据模型、失败模式、兼容策略
- 不在这里写逐任务拆解

### 3. 测试计划

- 定义单元、集成、回归、验收验证范围
- 提前识别风险点和验证口径

### 4. 代码实现

- 从当前稳定状态创建 phase 分支
- 按 `IMPLEMENTATION_PLAN.md` 切片推进
- 每个里程碑都做最小相关验证

### 5. 验收

- 将真实命令、结果、制品、提交和残余风险写入 `ACCEPTANCE.md`
- 不以设计预期替代真实证据

### 6. 合并

- 回到 `main`
- 合并 phase 分支
- 保留 phase 分支作为阶段证据
- 更新 `CURRENT_HANDOFF.md` 指向下一阶段或下一任务

## Phase-End Update Rules

阶段结束时至少检查：

1. `CURRENT_HANDOFF.md` 是否已更新
2. 当前 phase `HANDOFF.md` 是否移除了过期待办
3. 当前 phase `ACCEPTANCE.md` 是否记录了真实验证证据
4. roadmap 是否需要因 phase 退出条件变化而更新
5. `README.md` 是否需要反映新的可见能力或启动方式
6. `DESIGN.md` 是否需要吸收新的长期判断
7. `docs/README.md` 是否需要因目录 ownership 变化而更新

## Branch And Phase-Package Rules

- 一个 phase 默认对应一个独立分支：`phase-0X-<slug>`
- phase 分支与 phase 文档目录应使用同一标识
- 如果当前工作区仍有未收敛改动，先收敛，再决定是否从当前状态创建新分支
- 历史 phase 若缺少 package，可补建精简 handoff / implementation / acceptance，但不要伪造不存在的证据
