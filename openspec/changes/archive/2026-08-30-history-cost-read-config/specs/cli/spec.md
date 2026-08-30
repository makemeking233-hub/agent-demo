# cli Specification (delta)

> 本文件是 `history-cost-read-config` 的 delta spec。在 archive 时合并到 `openspec/specs/cli/spec.md`。

## ADDED Requirements

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
