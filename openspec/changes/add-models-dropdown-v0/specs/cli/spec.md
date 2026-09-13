## ADDED Requirements

### Requirement: /effort slash 命令

CLI SHALL 支持 `/effort <low|medium|high>` 命令,切换当前会话的 `AgentLoop.reasoningEffort` 字段。命令结果通过 `message_delta` (`delta_type: "text"`) 推送切换成功提示。

#### Scenario: /effort high 切到 high

- **WHEN** 用户提交 `/effort high`
- **THEN** 当前会话的 `AgentLoop.reasoningEffort` 改为 `"high"`
- **AND** 推送 `message_delta: "已切换思考强度为 high"`

#### Scenario: /effort low 切到 low

- **WHEN** 用户提交 `/effort low`
- **THEN** 当前会话的 `AgentLoop.reasoningEffort` 改为 `"low"`
- **AND** 推送 `message_delta: "已切换思考强度为 low"`

#### Scenario: /effort 参数非法(非 low/medium/high)

- **WHEN** 用户提交 `/effort extreme`(不在白名单)
- **THEN** 不切换 `reasoningEffort`
- **AND** 推送 `message_delta: "思考强度必须是 low / medium / high, 当前未变"`

#### Scenario: /effort 在 deepseek-chat 模型下接受但不报错

- **WHEN** 用户在 `deepseek-chat` 模型下提交 `/effort high`
- **THEN** 仍切换 `reasoningEffort="high"`
- **AND** 上游 DeepSeek 忽略该参数(不抛错,见 web-ui spec ProviderRequest.reasoningEffort 透传)

### Requirement: /model 别名向后兼容

CLI SHALL 保留现有 `/model reasoning` / `/model chat` 别名命令,行为不变(切换 model,见 add-reasoning-thinking-streaming spec)。

#### Scenario: /model reasoning 切换 reasoner

- **WHEN** 用户提交 `/model reasoning`
- **THEN** `AgentLoop.model` 改为 `deepseek-reasoner`
- **AND** 推送切换成功提示

#### Scenario: /model chat 切换 chat

- **WHEN** 用户提交 `/model chat`
- **THEN** `AgentLoop.model` 改为 `deepseek-chat`
- **AND** 推送切换成功提示