# cli Specification (delta)

> 本文件是 `fix-resume-link` 的 delta spec。在 archive 时合并到 `openspec/specs/cli/spec.md`。

## MODIFIED Requirements

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
