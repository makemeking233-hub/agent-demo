# 测试设计文档 — fix-cli-residue

- 批次目录：`docs/test-agent-demo/2026-09-19-fix-cli-residue/`
- 测试日期：2026-09-19
- 对应 change：`fix-cli-residue`

## 1. 测试范围

### 1.1 被测问题

`AgentLoop.DEFAULT_MODEL = "deepseek-chat"` 与 `AgentConfig.defaults().provider().model = "deepseek-chat"` 仍把**已停用**的 `deepseek-chat` 作为 CLI 默认模型。Web profile 的 `application-web.yml` 早就改成 `deepseek-v4-flash`，两边不同源——CLI 启动未配 model/env 时会原样把已停用 id 发给上游。

`SlashCommand` 的 `/model chat` 别名虽然也包含 `deepseek-chat`，但被 `cli/spec.md §/model 别名向后兼容` 与 `web-ui/spec.md §/model reasoning slash 命令` 锁死为向后兼容语义，**不在本次范围**。

### 1.2 覆盖的行为

| # | 行为 | 期望 |
|:--:|------|------|
| B1 | `AgentConfig.defaults().provider().model()` | `"deepseek-v4-flash"`（与 web profile 对齐） |
| B2 | `AgentLoop.DEFAULT_MODEL`（反射读） | `"deepseek-v4-flash"` |
| B3 | `InitCommand.run` 生成 `config.yaml` 默认 model | `deepseek-v4-flash`（自动跟随 defaults） |
| B4 | `ConfigLoader.defaultsWhenNoFile` | model = `deepseek-v4-flash`（fixture 同步） |
| B5 | `ChatCommand` 404 错误消息示例 / `ChatRequest` javadoc | 用 `deepseek-v4-flash` 而非 `deepseek-chat` |

### 1.3 不变

- `SlashCommand` 的 `/model chat` / `/model reasoning` 别名行为（spec 锁死）
- agent-core `application-local.yml`（gitignored）

## 2. 测试策略

- `AgentConfigDefaultsModelTest`：直接 `AgentConfig.defaults()` 后断言 `cfg.provider().model()`。
- `AgentLoopDefaultModelTest`：反射读 `private static final DEFAULT_MODEL` 常量（避免为测试改 production 可见性）。
- `InitCommandTest.createsConfigFile`：把断言从 `deepseek-chat` 改成 `deepseek-v4-flash`（fixture 同步，不增新覆盖）。
- `ConfigLoaderTest.defaultsWhenNoFile`：同样把 `assertEquals("deepseek-chat", ...)` 改成 `deepseek-v4-flash`。

## 3. DoD

- [ ] 新增 2 条单测（`AgentConfigDefaultsModelTest` / `AgentLoopDefaultModelTest`）全绿
- [ ] 同步 `InitCommandTest` / `ConfigLoaderTest`
- [ ] agent-core 全量绿（实测 535/0）
- [ ] 合并后 main 上 `mvn verify` 全绿（实测 532 core + 379 web + BUILD SUCCESS）
