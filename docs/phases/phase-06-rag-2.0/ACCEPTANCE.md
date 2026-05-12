# Phase 6 ACCEPTANCE

## Exit criteria checklist

- [ ] `MemoryReadService` 对外契约保持兼容
- [ ] 双路召回可工作，并具备单路故障降级
- [ ] 级联重排与动态 TopK 接入主链路
- [ ] 领域化 chunk 与元数据过滤链路可用
- [ ] Phase 6 定义的关键测试与性能采样完成
- [ ] 当前阶段产物与风险已记录完毕

## Commands run

当前尚未进入 Phase 6 实现，本节待收尾时补充真实命令。预期至少记录：

```bash
mvn test -pl dish-memory -am -s settings-test.xml -DfailIfNoTests=false
mvn test -pl dish-agent-dish,dish-memory -am -s settings-test.xml -DfailIfNoTests=false
mvn test -s settings-test.xml
```

## Actual results

尚未填写。Phase 6 完成时需要按真实结果记录：

- 通过 / 失败的测试范围
- 关键指标实测值
- 若有条件完成项或降级方案，明确写出原因

## Final artifacts

- `docs/prd/phase-06-rag-2.0.md`
- `docs/technical/phase-06-rag-architecture.md`
- `docs/testing/phase-06-rag-2.0-test-plan.md`
- `docs/phases/phase-06-rag-2.0/IMPLEMENTATION_PLAN.md`
- `docs/adr/ADR-003-multi-recall-fusion.md`
- 与 Phase 6 代码实现对应的最终文件清单

## Commit / branch evidence

待 Phase 6 实际完成后填写：

- Phase 分支名
- 关键提交
- 合并到 `main` 的提交
- 如有远端分支，记录 push 结果

## Remaining risks

当前已知但尚未验证的风险：

- Elasticsearch 运维与索引生命周期复杂度可能高于预期
- 级联重排与双路召回叠加后，延迟预算可能需要缓存与分层降级共同兜底
- 领域微调是否值得投入，取决于标注语料质量与离线评测结果
