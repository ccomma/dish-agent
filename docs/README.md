# dish-agent Docs

`docs/` 是共享项目上下文命名空间，不属于某一个 agent、skill、自动化流程或工具。

## Source Of Truth

| Artifact | Owns | Must Not Own |
| --- | --- | --- |
| `../README.md` | 项目入口、快速开始、阅读路径 | 当前 phase 状态、长设计记忆、执行态恢复 |
| `../DESIGN.md` | 长期产品判断、原则、非目标、稳定架构边界 | 当前阶段状态、分支步骤、文档加载策略 |
| `context/` | 当前执行态、恢复入口、验证命令、下一步 | 长设计历史、完整 phase 设计 |
| `process/` | 正式 phase execution workflow、上下文加载协议、阶段更新规则 | 当前 phase 状态、产品定位 |
| `roadmap/` | phase 顺序、目标、输入、输出、验收标准、退出条件、前置依赖 | 实现任务拆分、代码细节 |
| `phases/` | phase handoff、implementation plan、acceptance evidence | 长期产品方向 |
| `prd/` | 为什么做、给谁做、成功标准、非目标、需求边界 | 模块实现细节、当前任务状态 |
| `technical/` | 模块边界、接口、模型、存储、失败模式、安全边界 | 产品定位、日常进度 |
| `testing/` | 测试策略、fixture、回归风险、验证命令 | acceptance 事后证据 |
| `adr/` | 难逆转、跨阶段、需要背景的持久决策 | 临时想法、 routine implementation choices |
| `discovery/` | 上游探索输入与已消费归档 | 当前执行态、正式 phase 需求或技术契约 |
| `interview/` | 面试展示和讲解补充材料 | 当前 source of truth |
| `openapi/` | 接口契约资产 | 产品定位、phase 规划、执行态 |
| `templates/` | 项目内可复用文档骨架 | 当前项目状态、真实证据 |

## Write Rule

写入前先判断责任归属。如果某个主题已经有 owning layer，就更新或引用那个 layer，而不是复制一份到别处重新定义。

## Helper Layer Rule

`discovery/`、`interview/`、`openapi/` 等目录允许存在，但默认不进入主加载链。只有当当前任务显式需要它们，或 owning layer 指向它们时，才按需读取。

## Historical Record Rule

历史 phase 文档默认是证据记录。除非历史记录会误导当前工作，否则不要只为了套用新的文档规范而重写它们。
