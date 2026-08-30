# cli Specification (delta)

> 本文件是 `add-model-switch-command` 的 delta spec。在 archive 时合并到 `openspec/specs/cli/spec.md`。

## ADDED Requirements

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
