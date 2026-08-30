# cli Specification

## Purpose
TBD - created by archiving change add-resume-command. Update Purpose after archive.
## Requirements
### Requirement: Session Resume
The system SHALL restore conversation history from the most recent session file on `/resume` command.

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

