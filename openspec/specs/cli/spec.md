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

The system SHALL allow the user to switch the active model at runtime via `/model <name>` slash command without restarting the REPL.

#### Scenario: switch to known model

- GIVEN the REPL is active
- WHEN the user issues `/model deepseek-reasoner`
- THEN the active model becomes `deepseek-reasoner`
- AND the next user turn uses `deepseek-reasoner` for LLM calls
- AND the REPL prints `[/model] 切换到 deepseek-reasoner`

#### Scenario: list supported models (no argument)

- GIVEN the REPL is active
- WHEN the user issues `/model` (without argument)
- THEN the REPL prints the current model and the list of supported models
- AND the active model is unchanged

#### Scenario: unknown model name

- GIVEN the REPL is active
- WHEN the user issues `/model gpt-99`
- THEN the REPL prints `[未知 model: gpt-99] 支持: deepseek-chat, deepseek-reasoner`
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

