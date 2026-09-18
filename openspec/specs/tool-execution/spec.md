# tool-execution Specification

## Purpose
TBD - created by archiving change harden-tool-error-boundary. Update Purpose after archive.
## Requirements
### Requirement: 工具类加载失败降级为工具错误

工具交互入口（入参解析、权限检查、工具主体调用）抛出的 `LinkageError` SHALL 被转换为普通异常，使其能够被既有的错误处理路径转成错误 `ToolResult`，SHALL NOT 逃出响应式链。

#### Scenario: 入参解析时类缺失

- **WHEN** 某个工具的 `parseArguments` 抛出 `NoClassDefFoundError`
- **THEN** 该次调用产生一个错误 `ToolResult`，其 `toolCallId` 等于本次调用的 id
- **AND** 该错误被回流给模型
- **AND** 整轮对话继续，不因该错误中断

#### Scenario: 权限检查时类缺失

- **WHEN** 某工具的 `checkPermissions` 抛出 `LinkageError`
- **THEN** 该次调用产生一个错误 `ToolResult`，不中断整轮

#### Scenario: 工具主体调用时类缺失

- **WHEN** 某工具的 `execute` 在被调用时同步抛出 `LinkageError`
- **THEN** 该次调用产生一个错误 `ToolResult`，不中断整轮

#### Scenario: 错误信息可定位

- **WHEN** 类加载失败被降级
- **THEN** 错误信息包含工具名与失败原因
- **AND** 日志记录完整堆栈

### Requirement: 虚拟机级错误保持致命

`VirtualMachineError` SHALL NOT 被降级为工具错误，SHALL 继续向调用方传播。

#### Scenario: OOM 不被吞掉

- **WHEN** 工具交互过程中抛出 `OutOfMemoryError`
- **THEN** 该错误不被转换成错误 `ToolResult`
- **AND** 它继续向调用方传播

### Requirement: 工具类加载失败可被区分

降级产生的异常 SHALL 使用专门的类型，携带工具名与失败原因，以便与业务失败区分。

#### Scenario: 专门类型

- **WHEN** 发生类加载失败降级
- **THEN** 抛出的是专用异常类型而非通用的 `IllegalStateException`
- **AND** 该异常的 cause 是被捕获的 `LinkageError`

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

