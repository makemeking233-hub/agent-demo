## Context

`AgentLoop.executeOne` 是工具调用的唯一入口，形状如下（简化）：

```java
return Mono.fromCallable(() -> (Object) tool.parseArguments(call.argumentsJson()))
        .flatMap(input -> authorize(tool, call, input))   // 内部 resolvePermission + tool.execute
        .map(r -> List.of(stampCallId(r, call.id())))
        .onErrorResume(e -> {                              // ← 承诺的兜底
            ToolResult<Object> err = ToolResult.error(call.id(), "工具执行失败: " + e.getMessage());
            sink.onToolResult(err, elapsed);
            recordToolExecution(elapsed);
            return Mono.just(List.of(err));
        });
```

问题在于 `.onErrorResume` 只能看到**被转换成 `onError` 信号**的失败。Reactor 在把异常转成信号之前会先调用 `Exceptions.throwIfFatal(t)`：

```java
public static void throwIfFatal(Throwable t) {
    if (t instanceof VirtualMachineError) { throw (VirtualMachineError) t; }
    else if (t instanceof ThreadDeath)     { throw (ThreadDeath) t; }
    else if (t instanceof LinkageError)    { throw (LinkageError) t; }
}
```

`NoClassDefFoundError` / `ExceptionInInitializerError` / `UnsupportedClassVersionError` 都是 `LinkageError`，于是被**当场 rethrow**，绕过所有 `onError*` 算子，最终逃出响应式链。

## Goals / Non-Goals

**Goals：**

- 工具的类加载失败降级为工具错误，不打断整轮、不撕连接。
- 保持 `VirtualMachineError` 的致命语义。
- 覆盖面：入参解析、权限检查、工具主体调用三个入口。

**Non-Goals：**

- 不把工具执行改成完全隔离（不引入子进程 / 独立 ClassLoader）。
- 不处理工具**返回的 Mono 内部**再抛 `LinkageError` 的情况（见 R2）。
- 不改 SSE 协议、不改前端。

## Decisions

### D1：在进入 Reactor 之前捕获，而不是加更多 `onErrorResume`

**理由**：`LinkageError` 被 rethrow 发生在 Reactor 算子内部，任何**下游**的 `onErrorMap` / `onErrorResume` 都不可能看到它。唯一有效的位置是**我们交给 Reactor 执行的那段代码内部**——在那里 `catch` 住并换成普通异常，Reactor 才会正常地把它变成 `onError` 信号。

**实现**：

```java
/** 工具交互边界：把类加载失败（LinkageError）降级为普通异常。 */
private static <T> T invokeTool(String toolName, String what, Supplier<T> body) {
    try {
        return body.get();
    } catch (LinkageError e) {
        throw new ToolClassLoadingException(toolName, what, e);
    }
}
```

三处调用点：

| 位置 | 包裹内容 | 为什么需要 |
|------|---------|-----------|
| `Mono.fromCallable` 内 | `tool.parseArguments(json)` | `AbstractFileTool.parseArguments` 会调 `inputClass()`，**类解析发生在这里**（本次事故的直接位置） |
| `authorize` 内 | `tool.checkPermissions(...)` | 权限实现类可能尚未加载 |
| `authorize` 内 | `tool.execute(input, ctx)` | 工具主体的类可能与入参类不同 |

**考虑过**：用 `Hooks.onOperatorError` 全局拦截。否决：该 hook 在 `throwIfFatal` 之前/之后都无法阻止 rethrow，语义不匹配，且是全局副作用。

### D2：只捕获 `LinkageError`，不捕获 `Throwable`

**理由**：`VirtualMachineError`（OOM、StackOverflow）与 `ThreadDeath` 是真致命错误，把它们降级成一个工具错误会让进程带着已损坏的状态继续服务，比直接失败更危险。`LinkageError` 的语义恰好是「某个类的加载/链接失败」——对一个可插拔工具而言，这是**该工具不可用**，而不是进程不可用。

**考虑过**：捕获 `Throwable` 图省事。否决：会吞掉 OOM。

### D3：用专门的异常类型而非复用 `IllegalStateException`

**理由**：`ToolClassLoadingException` 让「工具因类加载失败不可用」在日志、测试与未来的可观测性事件里可被区分与检索；消息带工具名与缺失类名，可直接定位是哪个插件的哪个类。

## Risks / Trade-offs

### R1：把「环境坏了」伪装成「某个工具坏了」

[Accepted] 工具级降级确实可能掩盖环境问题（例如本次的构建产物残缺）。缓解：降级时 `log.warn` 带完整堆栈，且消息里明确写「类加载失败」而非泛化的「执行失败」，避免与业务错误混淆。运维信号由日志承担，不靠让整个会话崩掉来暴露。

### R2：工具返回的 Mono 内部抛 `LinkageError` 不在覆盖范围

[Accepted] `tool.execute(...)` 返回的 `Mono` 在 Reactor 算子内部再抛 `LinkageError` 时，本边界无法拦截（那时已经在我们交出去的代码之外）。要彻底覆盖只能让工具实现自行 `catch (LinkageError)`，或把工具执行放进独立 ClassLoader/进程——成本远超本 change 的收益。本次事故的直接位置（`parseArguments` → `inputClass()`）已被覆盖。

### R3：`ToolClassLoadingException` 是 `RuntimeException`，会被上游当成普通工具失败

[Accepted] 这正是目的。回流给模型的文本是「工具执行失败: 工具类加载失败 [EditFile] ...」，模型可以看到并换策略，而不是整轮崩掉。

## Migration Plan

无破坏性变更：不新增/不修改公开 API 签名，仅新增内部异常类型与三处 `try/catch`。正常路径零开销（无异常时 `try` 块无成本）。

回滚：单 commit revert。

## Open Questions

无。
