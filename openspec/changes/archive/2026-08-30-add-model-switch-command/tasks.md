# Tasks: Add /model Switch Command

> TDD 顺序：每项先测试（红）→ 实现 → 转绿 → commit

- [ ] T1: SlashCommandTest 加 `/model` 测试（无参数列模型 / 切换 / 未知 name）→ 实现 `/model` 分支 + onModel 回调 → 转绿
- [ ] T2: AgentLoop 加 setModel(String) 方法（model 字段改非 final）→ 简单单元测试
- [ ] T3: ChatCommand 集成 onModel 回调（注入 ReplContext，handleLine 调 setModel）→ E2E 验证
- [ ] T4: README 增补 /model 说明 + mvn test 全绿 + commit + push
- [ ] T5: openspec archive add-model-switch-command
