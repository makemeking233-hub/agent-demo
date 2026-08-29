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

