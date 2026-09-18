# cli Specification

## Purpose
TBD - created by archiving change add-resume-command. Update Purpose after archive.
## Requirements
### Requirement: Session Resume

The system SHALL restore conversation history from the most recent session file on `/resume` command, preserving complete message fidelity (including tool calls, tool results, and token statistics) and capping the restored context so it does not exceed the model's usable window.

#### Scenario: Most recent session exists

- GIVEN the user has completed at least one previous session (JSONL file in `~/.agent-demo/sessions/`)
- WHEN the user issues `/resume`
- THEN the system loads the most recent session file by mtime
- AND deserializes all entries into the current MessageHistory
- AND the next user turn continues from where the previous session ended

#### Scenario: No previous session

- GIVEN no session files exist in `~/.agent-demo/sessions/`
- WHEN the user issues `/resume`
- THEN the system prints "无历史会话" (no history message)
- AND no error is raised
- AND the REPL continues with an empty history

#### Scenario: tool_calls restored on assistant messages

- GIVEN a session file whose assistant entry carries tool_calls
- WHEN `/resume` restores history
- THEN the restored `Message.Assistant` includes the tool call skeleton (id/name/arguments)

#### Scenario: tool_result restores callId and isError

- GIVEN a session file whose tool_result entry carries toolCallId and isError
- WHEN `/resume` restores history
- THEN the restored `Message.ToolResult` carries the original callId and error flag

#### Scenario: token stats restored from meta

- GIVEN a session file with meta entries recording prompt/completion tokens
- WHEN `/resume` restores history
- THEN the prompt/completion accumulators are restored so `/history` shows the prior cost

#### Scenario: oversized history is snipped

- GIVEN a session whose restored message tokens exceed the configured cap
- WHEN `/resume` restores history
- THEN older turns are collapsed into a single summary system message and the latest turns are preserved, so the restored context fits within the cap

#### Scenario: orphan tool_result gets synthetic call skeleton

- GIVEN a session whose tool_result has no preceding assistant.tool_calls in the restored history
- WHEN `/resume` restores history
- THEN the system injects a synthetic assistant tool-call skeleton before the orphan tool_result so the message sequence is well-formed for the upstream protocol

### Requirement: Runtime Model Switch

The system SHALL allow the user to switch the active model — and optionally the provider — at runtime via `/model` slash command without restarting the REPL. Supported forms SHALL be: `/model <provider>/<model>` (explicit provider path), `/model chat` / `/model reasoning` (legacy aliases), and `/model <model>` (shorthand, provider inferred from model name prefix).

#### Scenario: switch to known model

- GIVEN the REPL is active
- WHEN the user issues `/model deepseek-reasoner`
- THEN the active model becomes `deepseek-reasoner`
- AND the active provider becomes `deepseek` (inferred from the `deepseek-` prefix)
- AND the next user turn uses `deepseek-reasoner` for LLM calls
- AND the REPL prints `[/model] 切换到 deepseek/deepseek-reasoner`

#### Scenario: switch with explicit provider path

- GIVEN the REPL is active
- WHEN the user issues `/model openai/gpt-4o`
- THEN `AgentLoop.providerId` becomes `openai` and `AgentLoop.model` becomes `gpt-4o`
- AND the REPL prints `[/model] 切换到 openai/gpt-4o`

#### Scenario: provider path is case-insensitive

- GIVEN the REPL is active
- WHEN the user issues `/model DeepSeek/deepseek-reasoner`
- THEN the provider id is normalized to lowercase `deepseek`
- AND the switch succeeds

#### Scenario: unknown provider is rejected

- GIVEN the REPL is active
- WHEN the user issues `/model bogus/gpt-4o`
- THEN the REPL prints a message listing supported providers
- AND the active model is unchanged
- AND no error is raised

#### Scenario: empty model after slash is rejected

- GIVEN the REPL is active
- WHEN the user issues `/model openai/`
- THEN the REPL prints a usage hint
- AND the active model is unchanged

#### Scenario: list supported models (no argument)

- GIVEN the REPL is active
- WHEN the user issues `/model` (without argument)
- THEN the REPL prints the current model, the list of supported model shorthands, the list of supported providers, and the legacy alias names
- AND the active model is unchanged

#### Scenario: unknown model name

- GIVEN the REPL is active
- WHEN the user issues `/model gemini-99`
- THEN the REPL prints a message listing supported models and the `<provider>/<model>` hint
- AND the active model is unchanged
- AND no error is raised

### Requirement: History Cost Display

The system SHALL display the cost estimate in `/history` output by reading `agent.cost.inputPerMTokens` and `agent.cost.outputPerMTokens` from configuration, NOT hardcoded values.

#### Scenario: cost computed from config

- GIVEN `application-local.yml` configures `agent.cost.inputPerMTokens: 2.0` and `outputPerMTokens: 8.0`
- AND the user has consumed 1M input tokens and 1M output tokens
- WHEN the user issues `/history`
- THEN the output shows `估算费用: ¥10` (matching the configured rates)

#### Scenario: zero cost when config zero

- GIVEN `application-local.yml` configures `agent.cost.inputPerMTokens: 0` and `outputPerMTokens: 0`
- AND the user has consumed any tokens
- WHEN the user issues `/history`
- THEN the output shows `估算费用: ¥0` (not the hardcoded 2/8)

### Requirement: /effort slash 命令

CLI SHALL 保留 `/effort <low|medium|high>` 命令,切换当前会话的 `AgentLoop.reasoningEffort` 字段。命令检查当前 `AgentLoop.model` 在 catalog 中 `supportsReasoning=true`,若否则仍切换但不生效(对齐 web spec)。命令结果通过 `message_delta` 推送切换成功提示。

#### Scenario: /effort high 切到 high

- **WHEN** 用户提交 `/effort high`
- **THEN** 当前会话的 `AgentLoop.reasoningEffort` 改为 `"high"`
- **AND** 推送切换成功提示

#### Scenario: /effort 非法等级

- **WHEN** 用户提交 `/effort extreme`(不在白名单)
- **THEN** 不切换 `reasoningEffort`
- **AND** 推送 `思考强度必须是 low / medium / high, 当前未变`

### Requirement: /model 别名向后兼容

CLI SHALL 保留现有 `/model reasoning` / `/model chat` 别名命令,行为不变(切换 model),并在 v0.2 起同时切换到对应 provider。别名匹配 SHALL 大小写不敏感。

#### Scenario: /model reasoning 切换 reasoner

- **WHEN** 用户提交 `/model reasoning`
- **THEN** `AgentLoop.model` 改为 `deepseek-reasoner`
- **AND** `AgentLoop.providerId` 改为 `deepseek`
- **AND** 推送切换成功提示

#### Scenario: /model chat 切换 chat

- **WHEN** 用户提交 `/model chat`
- **THEN** `AgentLoop.model` 改为 `deepseek-chat`
- **AND** `AgentLoop.providerId` 改为 `deepseek`
- **AND** 推送切换成功提示

#### Scenario: 别名大小写不敏感

- **WHEN** 用户提交 `/model REASONING`
- **THEN** `AgentLoop.model` 改为 `deepseek-reasoner`(与小写别名行为一致)

