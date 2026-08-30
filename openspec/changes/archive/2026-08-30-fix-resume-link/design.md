## Context

当前 `/resume`（`SlashCommand.doResume`）用 `SessionStore.loadLatest(sessionsDir)` 读 `sessions/*.jsonl` 存档，并把 `SessionEntry` 转成 `List<Message>`，但转换逻辑有 3 个明确 bug + 2 个缺失能力：

- `assistant` → `new Message.Assistant(content, List.of())` 丢失 toolCalls。
- `tool_result` → `new Message.ToolResult("", content, false)` 丢失 toolCallId/isError。
- `assistant`/`tool_result` 的 `extras` 字段未读取。
- meta（token 累计）未恢复 → resume 后 `/history` 费用归零。
- 无 snip 裁剪 → 超长会话全量加载可能撑爆上下文。
- 无 parallel tool_result 孤儿处理 → 顺序约束可能被破坏。

## Goals / Non-Goals

**Goals:**
- 从 `SessionEntry.extras()` 正确还原 assistant 的 toolCalls 与 tool_result 的 toolCallId/isError。e
- 从 meta 条目恢复 token 累计。
- snip：restored 消息 token 超过上限时，把旧轮裁剪为一条 summary system 消息，保留最新轮。
- parallel tool_result 孤儿处理：为无前置 assistant.tool_calls 的孤儿 tool_result 注入合成工具调用骨架。
- 抽成 `SessionResumeLoader` 工具类，统一转换逻辑并便于单测，`SessionReplay`（事件流）复用。

**Non-Goals:**
- 不改变会话写入（`SessionStore.append`）逻辑。
- 不改 `/resume` 的入口 UI 提示（提示文案沿用）。
- 不做会话列表选择（仍 `loadLatest` 取最近）。

## Decisions

**D1: 新增 `SessionResumeLoader.load(Path sessionsDir)` 返回 `ResumeResult`。**
- `ResumeResult` 含 `List<Message>` 与累计 prompt/completion token。
- 逐 `SessionEntry` 转换：`user`→`Message.User`；`assistant`→ 从 `extras.toolCalls` 解析（`List<Map>`→`List<ToolCall>`，含 id/name/argumentsJson），补全为空列表；`tool_result`→ 从 `extras.toolCallId`/`extras.isError` 读取；`system`→`Message.System`；`meta`→ 更新 token 累计（key=prompt/completion）。
- 备选：继续在 `SlashCommand.doResume` 内联转换。否决——难单测，逻辑分散。

**D2: snip 用 token 估算 + 保留最近 N 轮。**
- `SessionResumeLoader.load(..., TokenEstimator, int maxTokens)`：restored 后若 `estimator.estimate(history.all()) > maxTokens`，从头部坍缩旧 user/assistant/tool 三连为一条 `Message.System("[RESUMED SUMMARY] ...")`，直到剩余 ≤ 上限；保留最新轮次。
- 备选：按"保留最近 N 轮"固定窗口。否决——固定窗口无法感知实际 token，仍可能超限。用 token 上限更稳，回退到固定窗口下限。

**D3: 并行孤儿处理在转换后做一次后处理。**
- 扫描 restored 消息，若某 `Message.ToolResult` 的 toolCallId 在前置 `Message.Assistant` 的 toolCalls 中不存在（孤儿），在其前插入一个合成 `Message.Assistant(content="", [ToolCall(toolCallId,"unknown","{}")])`，保证序列 well-formed。
- 备选：丢弃孤儿 tool_result。否决——丢失信息，违反"恢复完整信息"。插入骨架更贴近真实。

**D4: `SlashCommand.doResume` 改为调用 `SessionResumeLoader`。**
- `doResume` 用 `SessionResumeLoader.load(sessionsDir, estimator, maxTokens)`，把 `ResumeResult` 交给 `onResume` 回调（仍传 `List<Message>`，token 由回调方同步到累计器）。
- `ChatCommand` 在 `/resume` 回调里用 loader 返回的 token 覆写累计器。

## Risks / Trade-offs

- [snip 坍缩丢细节] → 用 summary 系统消息保留"前方已压缩"提示；本 change 的 summary 用固定提示文本（不调 LLM），避免引入新依赖。
- [extras.toolCalls 可能是 null/缺 key] → 解析前判空，缺失时回退空列表/默认值。
- [孤儿注入合成骨架可能让模型困惑] → 注入的 `arguments` 用 `{}`，description 注明，属协议兜底；仅在真正孤儿时触发。
- [MODIFIED delta 使主 spec 变长] → 严格复制完整 requirement 块，archive 后一致。

## Migration Plan

1. 新增 `SessionResumeLoader`（含 `ResumeResult`）。
2. `SlashCommand.doResume` 改用 loader。
3. `ChatCommand` 同步 token 累计。
4. 新增 `SessionResumeLoaderTest`；适配 `SlashCommandTest`。
5. `mvn -pl agent-core verify` 全绿（jacoco 门禁达标）。

## Open Questions

- snip 的 token 上限默认值：沿用上下文窗口缓存配置（如 config 里有 `context.compactBuffer` 或独立 `resumeMaxTokens`）。实施时读取现有配置，缺省用 80% 上下文窗口。
