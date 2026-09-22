# fix-jacoco-rule — 任务清单（归档后重建）

> 说明：`openspec archive` 未识别本文件的 checkbox（其输出为 `Task status: No tasks`），
> 归档时把源目录 `openspec/changes/fix-jacoco-rule/` 删除却没搬走本文件。
> 内容按实际执行结果重建于归档目录，等价于归档时的最终状态。

## T1 pom 规则改写 + 跑门禁暴露缺口

- [x] 把 `agent-core/pom.xml` 的 jacoco 规则从 `<element>BUNDLE</element>` + 6 个类名通配改为 `<element>PACKAGE</element>`
- [x] 加 `<excludes>`（路径用斜杠）排除 JVM 入口类 `com/example/agent/AgentCli.*`
- [x] 用**阈值抬升实验**验证规则真的生效：修复前规则 0.99 → 仍 SUCCESS（对照组）；修复后 0.99 → `Rule violated` + BUILD FAILURE
- [x] 跑 `mvn -o -pl agent-core verify` 暴露真实缺口并记录每包数值
- [x] **发现子包漏检**：`PACKAGE` 元素下 `X` 只匹配根包、`X.*` 只匹配子包 → includes 改为根包与子包**成对**写出（5 组共 10 条）
- [x] 用 0.99 违规清单 vs 独立复算 `jacoco.csv` 比对，确认覆盖 11 个包（5 根包 + 6 子包）
- [x] commit `de1b60c` + push

## T2 按缺口逐包补测（共 74 例，未改任何 `src/main` 生产代码）

- [x] `session` 0.77/0.64 → **0.80/0.71**：`SessionEntryTest`(8) + `SessionResumeLoaderParsingTest`(18)
- [x] `tools` 0.78/0.50 → **0.89/0.88**：`ToolRegistryTest`(+5) + `PathGuardTest`(4) + `WebSearchToolTest`(+12)
- [x] `tools.websearch` 0.98/0.68 → **0.98/0.71**：`WebSearchProviderFactoryTest`(+4)
- [x] `provider.minimax` 0.53/1.00 → **1.00/1.00**：`MiniMaxProviderTest`(5)
- [x] `provider.anthropic` 0.75/0.68 → **0.97/0.77**：`AnthropicProviderStreamChatTest`(9)
- [x] `tools.file` 0.62/0.50 → **0.82/0.95**：`FileToolsProtocolTest`(4)
- [x] `tools.shell` 0.68/0.65 → **0.84/0.75**：`ShellToolTest`(+5)
- [x] 全量门禁：`All coverage checks have been met` + BUILD SUCCESS，agent-core 658/0 + agent-web 399/0
- [x] commit `3652bed` / `fcdbf1f` + push

## T3 spec 与 AGENTS.md 同步

- [x] `openspec/changes/fix-jacoco-rule/specs/testability/spec.md` 写好《覆盖率门禁》delta（含「根包 + 子包成对」与「阈值抬升实验」两个 Scenario）
- [x] `AGENTS.md` §2.5.3 的 jacoco 一行改为指向 spec，并写明 includes 成对规则；追加修订记录 v0.1.8

## T4 归档 + 合并 + 复验 + push

- [x] `openspec archive fix-jacoco-rule --yes` → 归档为 `2026-09-22-fix-jacoco-rule`；delta 已并入 `openspec/specs/testability/spec.md`（`+ 1 added`），已核验主 spec 含《覆盖率门禁》
- [x] 同步 `origin/main`（`cc89d4d`，27 个提交，无冲突）后重跑门禁：658/0 + 399/0，两模块 met，BUILD SUCCESS
- [x] 前端 vitest 43 files / 361 tests 全过；`tsc --noEmit` 2 ≤ 基线 7；本 change 零前端改动
- [x] 合并 `main` + `main` 上复验 + push `origin main`
- [x] 删除分支与 worktree

## T5 测试文档

- [x] `docs/test-agent-demo/2026-09-23-fix-jacoco-rule/` 四件套（test-design / test-cases / test-report / test-review）
- [x] `test-guide.md` §1 登记行 + §2 详情小节
- [x] §10 自查：新增用例全在内存或 `@TempDir` 中构造；pom 实验改动用原文本写回并核验 diff；未触碰真实数据目录

## 未完成（超范围，已记录）

- [ ] 修 D-03：`AnthropicProvider.streamChat` 的 SSE 解析缺陷（Spring SSE reader 剥 `data:` 前缀 ↔ `parseSseLine` 要求前缀）。超出本 change scope，已用 characterization 用例固化并建议单开 change
- [ ] 修 D-04：`agent-web` 的 `com.example.agent.web.*` 漏根包 `com.example.agent.web`。建议随 D-03 或独立 change 处理
