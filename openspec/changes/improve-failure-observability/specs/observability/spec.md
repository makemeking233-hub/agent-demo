## ADDED Requirements

### Requirement: 故障上报不可被绕过

回合执行 SHALL 在 Reactor 之外设置错误边界：订阅侧（`ChatStreamService.start` 的 executor 任务、CLI REPL 的 turn 循环）SHALL 捕获 `Throwable` 并上报，SHALL NOT 仅捕获 `Exception`。

`VirtualMachineError` 与 `ThreadDeath` SHALL 原样抛出，不降级。

#### Scenario: Error 类失败仍被上报

- **WHEN** 回合执行过程中逃出 `LinkageError`（如 `NoClassDefFoundError`）
- **THEN** 日志出现一条 ERROR，含 `streamId` / `sessionId` / `workspace` / 模型名与异常摘要
- **AND** 该回合落一条 `system/error` 事件
- **AND** SSE 收到 `error` 事件并正常关流

#### Scenario: OOM 不被吞掉

- **WHEN** 回合执行抛出 `OutOfMemoryError`
- **THEN** 该错误继续向调用方传播，不被降级为回合失败

### Requirement: 工具调用必须有始有终

每个成功进入执行的工具调用 SHALL 产生唯一一条对应的结束记录（`tools.log` 的 `TOOL<`），无论其成功、失败或异常中断。

#### Scenario: 异常中断也收口

- **WHEN** 某工具调用在参数解析或执行中被异常中断，未产生结果
- **THEN** 该调用被补一条错误结果
- **AND** `tools.log` 出现对应的 `TOOL<` 行
- **AND** 日志出现 ERROR 指明该 `toolCallId` 未完成

#### Scenario: 幂等

- **WHEN** 同一回合内收口被触发多次
- **THEN** 每个未完成调用只被补一次

### Requirement: 日志根目录稳定且读写一致

运行时日志与 per-session 日志 SHALL 写入 `~/.agent-demo/logs`，SHALL NOT 依赖进程工作目录。日志查看接口 SHALL 读取同一目录。

#### Scenario: 不同工作目录写同一处

- **WHEN** 分别从仓库根目录与模块目录启动应用
- **THEN** 两次的日志都落在 `~/.agent-demo/logs/`

#### Scenario: 查看接口与写入一致

- **WHEN** 查询日志查看接口列出的会话
- **THEN** 列出的是实际被写入的会话日志

### Requirement: 测试日志与运行时日志隔离

测试运行 SHALL NOT 向运行时日志文件（`~/.agent-demo/logs/app.log`）写入内容。

#### Scenario: 跑测试不污染运行时日志

- **WHEN** 执行一次完整测试
- **THEN** 运行时 `app.log` 不新增来自测试的行

### Requirement: 会话记录完整性可诊断

系统 SHALL 提供诊断入口，报告存在 `tool_calls` 与 `tool_result` 不配对的会话及其缺失的 `toolCallId`。

#### Scenario: 报告不完整会话

- **WHEN** 某会话存档存在悬挂 `tool_calls`
- **THEN** 诊断结果包含该会话 id 与缺失的 `toolCallId` 列表

#### Scenario: 全部完整时报告为空

- **WHEN** 所有会话存档均满足配对不变式
- **THEN** 诊断结果中不完整会话列表为空

#### Scenario: 会话恢复时的修复被记录

- **WHEN** 恢复一个含悬挂 `tool_calls` 的存档并触发自动修复
- **THEN** 日志出现可检索的 WARN，含 `sessionId` 与被补的 `toolCallId` 列表
