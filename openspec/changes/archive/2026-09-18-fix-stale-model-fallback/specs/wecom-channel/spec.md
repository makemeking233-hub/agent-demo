## ADDED Requirements

### Requirement: 微信通道默认模型来自目录

微信通道 SHALL 使用配置 `agent.chat.default-model` 作为发起回合的模型，SHALL NOT 在代码中硬编码任何模型 id。该模型 SHALL 存在于 `agent.chat.providers` 目录中（由 `ProviderCatalogService` 启动校验保证）。

#### Scenario: 微信回合使用配置默认模型

- WHEN 微信 userId 发文本消息触发一次回合
- THEN 系统调用 `ChatStreamService.create` 时传入的 `model` 等于 `agent.chat.default-model`
- AND 该值不等于任何代码内硬编码的模型 id

#### Scenario: 配置默认模型变更后生效

- WHEN 运维把 `agent.chat.default-model` 从 `deepseek-v4-flash` 改为另一个目录中的合法 id 并重启
- THEN 微信通道后续回合使用新值
- AND 无需改动任何代码
