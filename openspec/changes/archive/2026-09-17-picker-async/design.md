# 设计：picker-async

## Context

当前 pick-folder 端点同步阻塞直到 PowerShell dialog 关闭。改造为**异步 + 任务队列 + 轮询**模式。后续 change `picker-jna` 将进一步去掉 PowerShell 用 JNA 直调 Win32。

## Goals / Non-Goals

**Goals**:
- 后端立即返回 202 + task_id，前端轮询拿结果
- 前端不再"卡 UI"，可在 dialog 打开期间正常操作
- 加 reveal 快速备选（explorer /select，~50ms）
- 5 分钟超时保留
- 支持 AbortSignal（用户取消 modal → 后端 destroy process）

**Non-Goals**:
- 去掉 PowerShell（→ 后续 change `picker-jna`）
- 跨平台 picker 优化
- SSE 推送（轮询足够简单）

## Decisions

### D1. 后端异步任务表

```java
class PickerTaskStore {
  record Task(String id, Process process, Path outputFile, CompletableFuture<String> future) {}
  Task submit(BiFunction<Path, Consumer<String>, Process> starter);
  Optional<Task> get(String id);
  void cancel(String id);  // destroy process
}
```

- 启动 process 写到 `outputFile`（避免 stdout 阻塞）
- 注册 AbortSignal：future.complete(outputFile 读到的路径)
- 完成后 1 分钟清理（避免内存泄漏）

### D2. 端点

```
POST /api/workspaces/pick-folder
  → 202 Accepted
    { task_id: "uuid", timeout_seconds: 300 }

GET /api/workspaces/pick-folder/{task_id}
  → 200 OK
    { status: "running" | "done" | "cancelled" | "error" | "invalid_path" | "timeout",
      path: "...", reason: "..." }

DELETE /api/workspaces/pick-folder/{task_id}
  → 204 No Content  // abort
```

### D3. 前端轮询

```typescript
const task_id = await startPickFolder();
const interval = setInterval(async () => {
  const status = await pollPickFolder(task_id);
  if (status.status !== "running") {
    clearInterval(interval);
    if (status.status === "done") setSelectedPath(status.path);
  }
}, 500);
// 30s 后改 2s
setTimeout(() => {
  interval && clearInterval(interval);
  // 切换 polling 频率
}, 30000);
```

### D4. Reveal 备选

modal header 加 "在文件管理器中显示" 按钮 → 调 `/api/settings/reveal` 复用（已有 reveal 控制器）

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | 轮询增加后端 QPS | 1s/次，可调 |
| R2 | 任务未清理导致内存泄漏 | 完成后 1 分钟自动清理 |
| R3 | 5 分钟 timeout 用户不知情 | modal 显示 "操作超时" 错误条 |
| R4 | 多 tab 同时 picker | 各自 task_id 独立，无冲突 |

## Migration Plan

无（仅 UI 改造，行为兼容）

## Open Questions

1. 轮询频率是否可配置？→ **v0.1 固定 1s/2s**，后续看实际使用
2. 任务清理周期？→ **1 分钟**（够前端拿到结果）
3. 是否用 SSE？→ **不用**，轮询足够简单
