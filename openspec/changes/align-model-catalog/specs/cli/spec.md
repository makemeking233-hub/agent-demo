# cli — delta spec

## MODIFIED Requirements

### Requirement: /effort slash 命令

CLI SHALL 保留 `/effort <low|high|max>` 命令,切换当前会话的 `AgentLoop.reasoningEffort` 字段。
白名单 SHALL 恰为 DeepSeek 官方档位 `low` / `high` / `max`,`medium` SHALL NOT 被接受。
命令检查当前 `AgentLoop.model` 在 catalog 中 `supportsReasoning=true`,若否则仍切换但不生效
(对齐 web spec)。命令结果通过 `message_delta` 推送切换成功提示。

#### Scenario: /effort high 切到 high

- **WHEN** 用户提交 `/effort high`
- **THEN** 当前会话的 `AgentLoop.reasoningEffort` 改为 `"high"`
- **AND** 推送切换成功提示

#### Scenario: /effort max 切到 max

- **WHEN** 用户提交 `/effort max`
- **THEN** 当前会话的 `AgentLoop.reasoningEffort` 改为 `"max"`
- **AND** 推送切换成功提示

#### Scenario: /effort 非法等级

- **WHEN** 用户提交 `/effort extreme` 或 `/effort medium`(均不在白名单)
- **THEN** 不切换 `reasoningEffort`
- **AND** 推送 `思考强度必须是 low / high / max, 当前未变`

### Requirement: /model 别名向后兼容

CLI SHALL NOT 再保留指向已失效 id 的 `/model` 别名。`chat` / `reasoning` 两个别名此前映射到
`deepseek-chat` / `deepseek-reasoner` —— 两者均不在 DeepSeek 官方模型列表中,保留等价于保留
死链,故 SHALL 删除整个别名表。

`/model <name>` 收到未知模型名时 SHALL 拒绝切换,并推送包含当前合法 model id 的错误提示。
大小写不敏感行为 SHALL 保持不变。

#### Scenario: /model reasoning 不再切换

- **WHEN** 用户提交 `/model reasoning`
- **THEN** `AgentLoop.model` 保持不变
- **AND** 推送未知模型提示,提示中含 `deepseek-flash` 与 `deepseek-v4-pro`
- **AND** `AgentLoop.providerId` 保持不变

#### Scenario: /model chat 不再切换

- **WHEN** 用户提交 `/model chat`
- **THEN** `AgentLoop.model` 保持不变
- **AND** 推送未知模型提示,提示中含 `deepseek-flash` 与 `deepseek-v4-pro`

#### Scenario: 合法 id 大小写不敏感

- **WHEN** 用户提交 `/model DeepSeek/DeepSeek-V4-Pro`
- **THEN** `AgentLoop.model` 改为 `deepseek-v4-pro`
- **AND** `AgentLoop.providerId` 改为 `deepseek`
- **AND** 推送切换成功提示

### Requirement: CLI 默认模型

CLI 启动且未显式设置模型时，SHALL 使用 `agent.chat.providers` 目录中当前合法且与 web profile
默认对齐的模型 id。**该 id SHALL NOT 是已停用的 `deepseek-chat`，SHALL NOT 是被官方改名的
`deepseek-v4-flash`，SHALL NOT 是项目虚构的 `deepseek-reasoner`**。

#### Scenario: 无配置时的默认模型

- WHEN CLI 启动且 env / yml / `--model` 均未指定模型
- THEN `AgentLoop` 在缺 model 时回退到 `deepseek-flash`
- AND `AgentConfig.defaults().provider().model() == "deepseek-flash"`

#### Scenario: 默认模型存在于目录中

- WHEN `AgentConfig.defaults().provider().model()` 与 web profile 的
  `agent.chat.default-model` 比对
- THEN 两者相等,且该 id 在 DeepSeek 官方 `/models` 返回的 id 集合内

> 本 Requirement 与 `web-ui/spec.md` 及 `provider-catalog/spec.md` 一致——两侧默认值同源。
