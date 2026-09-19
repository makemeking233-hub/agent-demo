## MODIFIED Requirements

### Requirement: CLI 默认模型

CLI 启动且未显式设置模型时，SHALL 使用 `agent.chat.providers` 目录中当前合法且与 web profile 默认对齐的模型 id。**该 id SHALL NOT 是已停用的 `deepseek-chat`**。

#### Scenario: 无配置时的默认模型

- WHEN CLI 启动且 env / yml / `--model` 均未指定模型
- THEN `AgentLoop` 在缺 model 时回退到 `deepseek-v4-flash`（而不是已停用的 `deepseek-chat`）
- AND `AgentConfig.defaults().provider().model() == "deepseek-v4-flash"`

> 本 Requirement 与 `web-ui/spec.md §/api/chat/send 发送聊天消息` 一致——两侧默认值同源。
