## 1. 测试先行（红）

- [x] 1.1 `AgentLoopTest#linkageErrorInParseBecomesToolErrorNotFatal`：匿名 `Tool.parseArguments` 抛 `NoClassDefFoundError` → **先红**（`Tests run: 2, Failures: 0, Errors: 1`，错误正是 `NoClassDefFoundError: com/example/agent/tools/file/EditFileTool$Input`，且日志出现 4 次 `throwIfFatal detected a jvm fatal exception`，与生产事故签名一致）
- [x] 1.2 `AgentLoopTest#virtualMachineErrorStaysFatal`：`parseArguments` 抛 `OutOfMemoryError` → 修复前后均通过（VM 错误本来就该致命，作为反向护栏）
- [x] 1.3 `ToolClassLoadingExceptionTest`：类型 / 消息 / cause 保真（2 例）

## 2. 实现（转绿）

- [x] 2.1 新增 `com.example.agent.core.ToolClassLoadingException`（`RuntimeException`，带 `toolName` 与 `stage`）
- [x] 2.2 `AgentLoop` 新增私有 `invokeTool(toolName, stage, Supplier<T>)`：捕获 `LinkageError` → `log.warn` 全栈 + 抛 `ToolClassLoadingException`
- [x] 2.3 三处接入：`parseArguments`（STAGE_PARSE）、`resolvePermission`（STAGE_PERMISSION）、`execute`（STAGE_EXECUTE）
- [x] 2.4 `AgentLoopTest` 16 用例全绿

## 3. 文档与收尾

- [x] 3.1 `docs/design/design.md` 新增 §11.5「工具错误边界（LinkageError 降级）」，含 Reactor `throwIfFatal` 源码行为、三处接入表、只捕获 `LinkageError` 的理由、已知残留，以及本次事故的排查经验
- [x] 3.2 `mvn -o -pl agent-core verify`：`Tests run: 376, Failures: 0, Errors: 0` + `All coverage checks have been met` + BUILD SUCCESS
- [x] 3.3 `check-md.sh docs/design/design.md` 全部检查通过
- [x] 3.4 `openspec validate harden-tool-error-boundary --strict` + archive + commit + push

## 4. 验收证据

| 项 | 证据 |
|----|------|
| 缺口真实存在（红） | 修复前 `mvn test -Dtest=AgentLoopTest#linkageErrorInParseBecomesToolErrorNotFatal+virtualMachineErrorStaysFatal` → `Errors: 1`，栈为 `NoClassDefFoundError`，并触发 Reactor `throwIfFatal detected a jvm fatal exception` |
| 降级生效（绿） | 修复后同一命令全绿；`AgentLoopTest` 16/16 |
| 整轮不中断 | `linkageErrorInParseBecomesToolErrorNotFatal` 中 `processTurn(...).block()` 正常返回，且 history 里出现携带真实 `toolCallId="1"` 的 error 工具结果 |
| VM 错误不被吞 | `virtualMachineErrorStaysFatal` 断言 `OutOfMemoryError` 仍向调用方传播 |
| 无回归 | agent-core 全量 376 用例全绿，jacoco 门禁达标 |
