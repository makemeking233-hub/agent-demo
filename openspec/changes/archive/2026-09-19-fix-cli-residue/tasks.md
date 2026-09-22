# fix-cli-residue — 任务清单

- [x] T1 新增测试 `AgentConfigDefaultsModelTest` 断言 `defaults().provider().model() == "deepseek-v4-flash"`，跑红
- [x] T2 `AgentConfig.defaults()` Provider.model 改 `deepseek-v4-flash`，跑绿
- [x] T3 新增测试 `AgentLoopDefaultModelTest` 断言 `DEFAULT_MODEL == "deepseek-v4-flash"`，先红
- [x] T4 `AgentLoop.DEFAULT_MODEL` 改 `deepseek-v4-flash`，跑绿
- [x] T5 `ChatCommand:432` 错误消息示例、`ChatRequest:11` javadoc、`ConfigLoaderTest:18` 断言同步
- [x] T6 同步 tasks.md（勾选）+ 四件套 + test-guide + commit + push
- [x] T7 归档 OpenSpec + 合并 main + 复验 + push + 清理
