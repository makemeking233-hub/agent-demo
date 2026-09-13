## Why

用户发现侧栏里堆积了大量**测试产生的会话**。核实后确认：这些是集成测试写进**用户真实数据目录**的产物。

实测规模（清理前）：

| 位置 | 测试产物 | 真实会话 |
|------|:--------:|:--------:|
| `~/.agent-demo/sessions/`（live） | 60 | 4 |
| `~/.agent-demo/sessions/.archive/` | 113 | 19 |

合计 **173 个测试会话**混进了用户的历史里，其中 113 个还被自动归档功能进一步搬进了归档区。

根因（代码层面，可复核）：

- `WebIntegrationTest` 与 `LocalKeySendTest` 用 `@SpringBootTest` 启动**完整 WebApplication**，并真实调用 `/api/chat/send`，于是 `WebAgentRuntime` 按默认路径把会话写进 `<user.home>/.agent-demo/sessions`。
- `WebAgentRuntime.defaultAgentDataDir()` 只认**环境变量** `AGENT_DEMO_HOME`，而 `@SpringBootTest(properties=…)` **无法设置环境变量**（它只写 Spring Environment）。因此测试**没有办法**隔离数据目录。
- 污染指纹清晰可辨：`WebIntegrationTest` 固定发送 `hi` → `go` → `go`（间隔 5 秒成组出现），`LocalKeySendTest` 发送 `你是谁`；这些会话体积都 < 1 KB，而用户的真实会话可达 126 KB。

## What Changes

- `WebAgentRuntime` 新增系统属性 `agent.demo.home`（`AGENT_DEMO_HOME_PROPERTY`），优先级**高于**环境变量 `AGENT_DEMO_HOME`；数据目录与 `config.yaml` 均从该解析结果出发（此前 config 恒取 `user.home`，覆盖数据目录时会读到用户真实配置）。
- 两个集成测试在**类初始化**时设该属性指向 `target/test-data`（必须早于 Spring 上下文创建）。
- 单测 `WebAgentRuntimeDataDirTest` 锁定解析优先级与回退语义。
- 清理既有污染：删除 173 个测试会话（明细导出为 CSV 供审计），保留全部真实会话。
- 全局规则 `~/.dsh/AGENTS.md` 新增 §10「测试不得污染用户真实数据」：隔离优先、无法隔离则跑完即清、删除前必须先区分归属并用可复核判据、留审计痕迹、有疑问先弹框确认。

## Capabilities

### New Capabilities

- `data-isolation`：测试与运行时数据目录的隔离契约。

## Impact

- **agent-web（3 个文件）**：`WebAgentRuntime.java`（解析逻辑）、`WebIntegrationTest.java`、`LocalKeySendTest.java`
- **测试**：新增 `WebAgentRuntimeDataDirTest`（3 例）
- **数据**：清理 173 个测试会话（live 60 + archive 113），保留 23 个真实会话
- **行为兼容**：既有的 `AGENT_DEMO_HOME` 环境变量语义不变（仍是「用户主目录」语义，其下拼 `.agent-demo`）；新增的系统属性插在它之前
