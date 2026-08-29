# Tasks: Add /resume Command

> TDD 顺序：每项先写测试（红）→ 实现 → 转绿 → commit

- [ ] T1: 先写 `SessionStoreTest.loadLatest` 测试（mtime 最大文件被选中、无文件返回空）→ 实现 `SessionStore.loadLatest()` → 转绿
- [ ] T2: 先写 `SlashCommandTest` 的 `/resume` 分支测试 → 实现 SlashCommand `/resume` case + ChatCommand 集成 → 转绿
- [ ] T3: 先写 `MessageHistoryTest.replaceAll` 测试 → 实现 `MessageHistory.replaceAll()` + AgentLoop 切换 → 转绿
- [ ] T4: 跑 `mvn test` + `mvn verify`（jacoco LINE>=80% BRANCH>=70% 门禁）+ 修 README
- [ ] T5: git add + commit + push + 验证 github 远端
