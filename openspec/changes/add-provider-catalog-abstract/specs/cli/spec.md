## MODIFIED Requirements

### Requirement: /model slash 命令

CLI SHALL 支持 `/model <provider>/<model>` 完整路径(对齐 dsh),以及向后兼容的 `/model <modelId>` 简写(从 `default-provider` 兜底)和 `/model reasoning` / `/model chat` 别名(见 change A spec)。三参数都通过 `message_delta` 推送切换成功提示。

#### Scenario: /model openai/o1 切到 OpenAI o1

- **WHEN** 用户提交 `/model openai/o1`
- **THEN** 当前会话的 `AgentLoop.provider = "openai"` + `AgentLoop.model = "o1"`
- **AND** 推送 `message_delta: "已切换模型到 OpenAI o1"`

#### Scenario: /model o1 简写(省略 provider)

- **WHEN** 用户提交 `/model o1`(无 provider 前缀)
- **THEN** 从 `model` 名称推断 provider:`o1` → `openai`
- **AND** `AgentLoop.provider = "openai"` + `AgentLoop.model = "o1"`

#### Scenario: /model reasoning 别名保留

- **WHEN** 用户提交 `/model reasoning`
- **THEN** 映射为 `/model deepseek/deepseek-reasoner`
- **AND** 行为同 change A spec

#### Scenario: /model openai/nonexistent 非法模型

- **WHEN** 用户提交 `/model openai/nonexistent-model`
- **THEN** 不切换
- **AND** 推送 `message_delta: "未知模型 nonexistent-model"`

### Requirement: /effort slash 命令

CLI SHALL 保留 change A spec 中的 `/effort <low|medium|high>` 命令。命令检查当前 `AgentLoop.model` 在 catalog 中 `supportsReasoning=true`,若否则仍切换但不生效(对齐 web spec)。

#### Scenario: /effort high 切到 high

- **WHEN** 用户提交 `/effort high`
- **THEN** 当前会话的 `AgentLoop.reasoningEffort` 改为 `"high"`
- **AND** 推送 `message_delta: "已切换思考强度为 high"`

#### Scenario: /effort 非法等级

- **WHEN** 用户提交 `/effort extreme`(不在白名单)
- **THEN** 不切换 `reasoningEffort`
- **AND** 推送 `message_delta: "思考强度必须是 low / medium / high, 当前未变"`