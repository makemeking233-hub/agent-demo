# observability Specification (delta)

> 本文件是 `webclient-explicit-timeouts` 的 delta spec。在 archive 时合并到 `openspec/specs/observability/spec.md`。

## ADDED Requirements

### Requirement: HTTP Client Timeouts

The system SHALL configure explicit HTTP timeouts on the LLM provider WebClient to prevent indefinite blocking on slow or hung upstream services.

#### Scenario: connection timeout fires

- GIVEN the LLM provider WebClient is configured with `connectTimeout=10s`
- WHEN the upstream host is unreachable (TCP SYN times out)
- THEN the HTTP call fails within 10s with a `WebClientRequestException`
- AND the failure is logged at WARN level
- AND `SlashCommand.dispatch` propagates the failure to the REPL user

#### Scenario: response timeout fires

- GIVEN the LLM provider WebClient is configured with `responseTimeout=60s`
- WHEN the upstream returns headers but no body within 60s
- THEN the HTTP call fails with a timeout exception
- AND the failure is logged at WARN level
