## Why

线上事故（2026-09-13 16:10）暴露了一个失效的错误边界：

```text
WARN  r.core.Exceptions - throwIfFatal detected a jvm fatal exception, which is thrown and logged below:
java.lang.NoClassDefFoundError: com/example/agent/tools/file/EditFileTool$Input
    at com.example.agent.tools.file.EditFileTool.inputClass(EditFileTool.java:49)
    at com.example.agent.tools.AbstractFileTool.parseArguments(AbstractFileTool.java:54)
    at com.example.agent.core.AgentLoop.lambda$executeOne$0(AgentLoop.java:523)
...
ERROR r.n.c.ChannelOperationsHandler - Error was received while reading the incoming data. The connection will be closed.
```

`AgentLoop.executeOne` 的 Javadoc 承诺「解析或执行失败时返回错误 `ToolResult`，不让单次失败打断整轮」，实现上靠 `.onErrorResume(...)` 兜底。但：

- `NoClassDefFoundError` 属于 `LinkageError`（`Error`，不是 `Exception`）。
- Reactor 的 `Exceptions.throwIfFatal` 会把 `LinkageError` **原样重新抛出**，不转成 `onError` 信号——日志里那行 `throwIfFatal detected a jvm fatal exception` 就是它。
- 因此 `.onErrorResume(...)` 永远不会执行，错误穿透整条响应式链，最终由 Netty 关闭连接：**一个工具的一个类文件缺失，撕掉了整条 SSE 连接 / 整个 turn**。

工具是可替换的插件式组件，它的类加载失败属于「这个工具不可用」，不属于「进程级致命错误」。本 change 把这类失败降级为普通的工具错误。

## What Changes

- `AgentLoop` 在**进入 Reactor 之前**捕获工具交互三个入口的 `LinkageError`，转成普通异常，让既有 `.onErrorResume(...)` 能把它变成 `ToolResult.error`：
  - `tool.parseArguments(json)`（入参反序列化，`inputClass()` 的类解析就在这里）
  - `tool.checkPermissions(...)`（`resolvePermission` 内部，惰性类加载）
  - `tool.execute(input, ctx)`（工具主体调用）
- 新增 `ToolClassLoadingException`（`RuntimeException`），消息里带工具名与缺失的类，便于定位。
- `VirtualMachineError`（OOM / StackOverflow）**不捕获**，保持致命语义——那是真正该让进程退出的情况。

## Capabilities

### New Capabilities

- `tool-execution`：工具执行的错误隔离契约。

### Modified Capabilities

无。

## Impact

- **agent-core（2 个文件）**：`AgentLoop.java`、新增 `ToolClassLoadingException.java`
- **测试**：`AgentLoopTest` 新增用例（`parseArguments` 抛 `NoClassDefFoundError` 时降级为工具错误且整轮继续；`VirtualMachineError` 仍致命）
- **前端 / 协议**：零改动
- **已知残留**：工具**自己返回的 Mono 内部**再抛 `LinkageError` 不会被本 change 覆盖（Reactor 在算子内部就 rethrow 了，工具实现需自行兜底）。已在 design.md 记录
