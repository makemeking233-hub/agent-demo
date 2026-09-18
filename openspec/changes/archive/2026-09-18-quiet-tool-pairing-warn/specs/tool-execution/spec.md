# tool-execution Capability（delta for quiet-tool-pairing-warn）

## ADDED Requirements

### Requirement: Tool call history repair is logged at INFO with deduplication

The SessionResumeLoader SHALL log tool-pair repair at INFO level (not WARN) and SHALL log each affected sessionId at most once per process lifetime.

#### Scenario: First time loading a session with dangling tool_calls

- **WHEN** `SessionResumeLoader.toMessages()` is called for sessionId X
- **AND** session X has dangling tool_calls
- **THEN** the system SHALL log an INFO message containing sessionId X and the dangling tool_call_ids list

#### Scenario: Subsequent loads of the same session

- **WHEN** `SessionResumeLoader.toMessages()` is called again for sessionId X
- **AND** session X still has dangling tool_calls
- **THEN** the system SHALL NOT log the INFO message again for that sessionId
- **AND** the repair behavior SHALL still occur (returns repaired messages)

#### Scenario: Load a different session with dangling tool_calls

- **WHEN** `SessionResumeLoader.toMessages()` is called for sessionId Y (new)
- **AND** session Y has dangling tool_calls
- **THEN** the system SHALL log an INFO message for Y

#### Scenario: Load a clean session (no dangling)

- **WHEN** `SessionResumeLoader.toMessages()` is called for sessionId Z
- **AND** session Z has no dangling tool_calls
- **THEN** the system SHALL NOT log any repair-related message
