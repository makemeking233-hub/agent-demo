## ADDED Requirements

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
