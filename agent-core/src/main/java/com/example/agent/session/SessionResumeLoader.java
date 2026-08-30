package com.example.agent.session;

import com.example.agent.core.Message;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.llm.ToolCall;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话恢复加载器（fix-resume-link change）。
 *
 * <p>把 {@code sessions/*.jsonl} 存档（{@link SessionEntry} 列表）恢复为 {@link List<Message>}，
 * 并恢复累计 token。修复了此前 {@code SlashCommand.doResume} 丢失 toolCalls / toolCallId / isError /
 * meta token，以及 tool_result 孤儿缺失的问题。
 *
 * <p>snip 裁剪：{@link #snip(MessageHistory, TokenEstimator, int)} 在 restored 消息超上限时，把旧轮
 * user/assistant/tool 坍缩为一条 summary system 消息，保留最新轮。
 */
public final class SessionResumeLoader {

    /** 恢复结果：消息列表 + 累计 token。 */
    public record ResumeResult(List<Message> messages, int promptTokens, int completionTokens) {}

    private static final String ORPHAN_CALL_NAME = "resumed_tool";

    private SessionResumeLoader() {}

    /**
     * 从 sessions 目录加载最近一次会话并恢复为消息列表 + token。
     *
     * @param sessionsDir sessions 目录（{@code ~/.agent-demo/sessions/}）
     * @return {@link ResumeResult}；无会话或异常时返回空消息 + 0 token
     */
    public static ResumeResult load(Path sessionsDir) {
        List<SessionEntry> entries = SessionStore.loadLatest(sessionsDir);
        if (entries.isEmpty()) return new ResumeResult(List.of(), 0, 0);

        List<Message> messages = new ArrayList<>();
        int prompt = 0;
        int completion = 0;
        for (SessionEntry e : entries) {
            Map<String, Object> ex = e.extras() == null ? Map.of() : e.extras();
            switch (e.type()) {
                case "user" -> messages.add(new Message.User(e.content()));
                case "assistant" ->
                        messages.add(
                                new Message.Assistant(
                                        e.content(), parseToolCalls(ex)));
                case "tool_result" ->
                        messages.add(
                                new Message.ToolResult(
                                        str(ex.get("toolCallId")),
                                        e.content(),
                                        boolVal(ex.get("isError"))));
                case "system" -> messages.add(new Message.System(e.content()));
                case "meta" -> {
                    String key = str(ex.get("key"));
                    if ("prompt".equals(key)) prompt = intOf(ex.get("value"));
                    else if ("completion".equals(key)) completion = intOf(ex.get("value"));
                }
                default -> {
                    // 未知类型跳过
                }
            }
        }
        // 并行 tool_result 孤儿处理：为无前置 assistant.tool_calls 的 tool_result 注入合成骨架
        injectOrphanSkeletons(messages);
        return new ResumeResult(messages, prompt, completion);
    }

    /**
     * snip 裁剪：若消息列表 token 总量超过 {@code maxTokens}，把最早轮（从头部）坍缩为
     * summary system 消息，直到剩余不超过上限。坍缩会丢弃旧 detail（只保留一条提示 + 最新消息）。
     *
     * @param messages 已 restored 的消息列表
     * @param estimator token 估算器
     * @param maxTokens token 上限
     * @return 裁剪后的消息列表（若未超限则原样）
     */
    public static List<Message> snip(
            List<Message> messages, TokenEstimator estimator, int maxTokens) {
        List<Message> all = new ArrayList<>(messages);
        if (maxTokens <= 0 || all.isEmpty()) return all;
        if (estimate(all, estimator) <= maxTokens) return all;

        // 从头部逐个丢弃，直到剩余 ≤ 上限或只剩一条。
        int drop = 0;
        while (drop < all.size() && estimate(all.subList(drop, all.size()), estimator) > maxTokens) {
            drop++;
        }
        if (drop == 0) return all;
        List<Message> kept = new ArrayList<>(all.subList(drop, all.size()));
        // 在头部补一条 summary 系统消息，告知前方已压缩
        kept.add(0, new Message.System("[RESUMED] 前方对话已被压缩（保留最近轮次）。"));
        return kept;
    }

    // ---------- 内部 ----------

    /** 扫描消息，为每个无前置 assistant.tool_calls 的 tool_result 注入合成 assistant 骨架。 */
    private static void injectOrphanSkeletons(List<Message> messages) {
        int result = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof Message.ToolResult tr) {
                if (!hasMatchingCall(messages, i, tr.toolCallId())) {
                    // 在当前位置之前插入合成 assistant
                    messages.add(
                            i,
                            new Message.Assistant(
                                    "",
                                    List.of(new ToolCall(tr.toolCallId(), ORPHAN_CALL_NAME, "{}"))));
                    i++; // 跳过刚插入的
                    result++;
                }
            }
        }
    }

    private static boolean hasMatchingCall(List<Message> messages, int upTo, String callId) {
        for (int i = 0; i < upTo; i++) {
            if (messages.get(i) instanceof Message.Assistant a
                    && a.toolCalls() != null
                    && a.toolCalls().stream().anyMatch(tc -> tc.id().equals(callId))) {
                return true;
            }
        }
        return false;
    }

    private static int estimate(List<Message> msgs, TokenEstimator estimator) {
        int sum = 0;
        for (Message m : msgs) sum += estimator.estimate(m.content());
        return sum;
    }

    /** 解析 extras 中的 toolCalls（List&lt;Map&gt; → List&lt;ToolCall&gt;）。 */
    private static List<ToolCall> parseToolCalls(Map<String, Object> ex) {
        Object raw = ex.get("toolCalls");
        if (!(raw instanceof List<?> list)) return List.of();
        List<ToolCall> calls = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> mm = asStringMap(m);
            calls.add(
                    new ToolCall(
                            str(mm.get("id")),
                            str(mm.get("name")),
                            str(mm.get("argumentsJson"))));
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static int intOf(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static boolean boolVal(Object v) {
        if (v instanceof Boolean b) return b;
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }
}
