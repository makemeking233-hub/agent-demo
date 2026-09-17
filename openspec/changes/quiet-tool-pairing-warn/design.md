# 设计：quiet-tool-pairing-warn

## Context

`ToolCallPairing.repair()` 是正常数据修复，不是异常。但当前每次都打 WARN + 列出 sessionId 和 toolCallId，重复刷新 Sidebar 会产生大量重复告警。

## Goals / Non-Goals

**Goals**:
- 同一 sessionId 仅首次发现不配对时打 INFO
- 后续加载静默
- 仍保留可见性（操作员能从日志看到"哪些 session 曾被修复过"）

**Non-Goals**:
- 不改变修复行为（仍 inject synthetic error result）
- 不写盘修复后的内容（已通过 `ToolCallPairing.repair()` 返回值传递）
- 不引入持久化记录

## Decisions

### D1. 内存级 dedupe（Static Set）

```java
private static final Set<String> seenWarnedSessions = ConcurrentHashMap.newKeySet();

// inside toMessages():
List<String> dangling = ToolCallPairing.danglingCallIds(messages);
if (!dangling.isEmpty() && seenWarnedSessions.add(sessionId)) {
    log.info("首次发现历史不配对，已自动补合成错误结果：sessionId={} 缺失 toolCallId={}",
        sessionId, dangling);
}
```

- 进程生命周期内有效
- 重启后重新累计（可接受：重启是少见事件）
- 内存占用：sessionId 字符串 ≈ 100 bytes × 历史 session 数（通常 < 1000）

### D2. WARN → INFO

修复是正常数据恢复路径，不属于异常。降级为 INFO。

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | 进程重启后重复打 INFO | 可接受；INFO 不是噪音 |
| R2 | 多个 agent-web 实例各一份 set | 可接受；每个实例 dedupe 自己 |
| R3 | sessionId 拼接产生的 set 体积大 | 实测 < 1000 session，无问题 |

## Migration Plan

无。

## Open Questions

1. 改用 INFO 后运维如何知道哪些 session 有不配对？→ **仍能从日志查询**；后续可加 metrics
2. 是否需要持久化 dedupe？→ **不需要**，进程重启是少见事件
