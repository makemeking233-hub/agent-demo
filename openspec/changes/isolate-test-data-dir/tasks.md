## 1. 定位污染来源

- [x] 1.1 按内容指纹确认测试产物：`hi`/`go`（`WebIntegrationTest`）、`你是谁`（`LocalKeySendTest`）、空白
- [x] 1.2 确认规模特征：测试产物 < 1 KB，用户真实会话可达 126 KB
- [x] 1.3 定位代码根因：`defaultAgentDataDir()` 只认环境变量，而 `@SpringBootTest` 设不了环境变量

## 2. 清理既有污染

- [x] 2.1 清理 live 目录 60 个测试会话（保留 4 个真实）
- [x] 2.2 清理归档区 113 个测试会话（保留 19 个真实）
- [x] 2.3 两份删除明细导出 CSV 供审计（`~/.agent-demo/test-sessions-removed*.csv`）
- [x] 2.4 存疑的一律保留（`你好` ×3、`你好呢` ×1，以及归档区 `你好` ×10 与全部含真实语义的会话）

## 3. 隔离机制（防复发）

- [x] 3.1 `WebAgentRuntime` 新增系统属性 `agent.demo.home`（优先级高于环境变量），暴露常量供测试引用
- [x] 3.2 `config.yaml` 改为从解析出的数据目录读取（此前恒取 `user.home`，隔离不彻底）
- [x] 3.3 `WebIntegrationTest` / `LocalKeySendTest` 在**类初始化**时设该属性指向 `target/test-data`
- [x] 3.4 新增 `WebAgentRuntimeDataDirTest`（3 例：属性生效 / 属性优先于环境变量与回退 / 空值回退）

## 4. 全局规则

- [x] 4.1 `~/.dsh/AGENTS.md` 新增 §10「测试不得污染用户真实数据」：隔离优先、无法隔离则跑完即清、删除前用可复核判据区分归属、留审计痕迹、存疑先弹框确认、常见污染源与对策

## 5. 收尾

- [x] 5.1 `mvn -o -pl agent-core clean test` 全绿（418 用例）
- [x] 5.2 `mvn -o -pl agent-web verify` 全绿（185 用例）+ 真实数据目录会话数不变（4 → 4）
- [x] 5.3 `openspec validate isolate-test-data-dir --strict` + archive + commit + push

## 6. 验收证据

| 项 | 证据 |
|----|------|
| 污染规模 | live 60/64 为测试产物、归档区 113/132 为测试产物，合计 173 |
| 识别判据 | 内容指纹（测试源码里写死的 `hi`/`go`/`你是谁`/空白）+ 规模（< 2 KB）+ 时间戳成组（`hi`→`go`→`go` 间隔 5 秒，共 11 组） |
| 隔离生效 | 单独跑两个集成测试：真实目录 4 → 4，产物落在 `agent-web/target/test-data/.agent-demo/sessions`（4 个） |
| 全量验证不污染 | `mvn -o -pl agent-web verify`：真实目录 4 → 4 |
| 清理后状态 | live 4 个（全部真实）、归档区 19 个（全部真实） |
| 规则落地 | `~/.dsh/AGENTS.md` §10 |

## 7. 过程中发现的并发构建问题（非本 change 引入）

验证时 `ToolCallClosureTest` 报 `NoClassDefFoundError: ToolCallClosureTest$1`——匿名内部类的 class 文件缺失，是**增量编译残留**，与我今天上午诊断的 `EditFileTool$Input` 事故同一机制。诱因是**并行 agent 与我在同一个 `target/` 上并发跑 Maven**。`mvn clean test` 后全绿（418 用例）。

> 结论：多 agent 并行开发时应各自使用独立 worktree，或至少约定不并发构建同一模块。
