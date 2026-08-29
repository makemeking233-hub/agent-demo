package com.example.agent.log;

import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.ToolCall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话回放工具（observability 设计 D6）：把 {@code session.jsonl} 事件流重建为
 * {@link MessageHistory}，供调试与测试复用（v0.2 的 /resume 可切到事件流）。
 *
 * <p>只重建对话消息事件：user/message → {@link Message.User}；assistant/message →
 * {@link Message.Assistant}（含 toolCalls）；tool/result → {@link Message.ToolResult}。
 * context/snapshot、system/*、permission/decision、turn/*、session 等事件跳过。
 * 未知/未来类型行跳过不报错（向后兼容）。
 */
public final class SessionReplay {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 从会话事件流重建消息历史。
     *
     * @param sessionJsonl session.jsonl 路径
     * @return 重建的 {@link MessageHistory}（按事件顺序）
     */
    public static MessageHistory replay(Path sessionJsonl) {
        MessageHistory hist = new MessageHistory(new com.example.agent.llm.TokenEstimator());
        List<Map<String, Object>> events = readEvents(sessionJsonl);
        for (Map<String, Object> e : events) {
            String type = String.valueOf(e.get("type"));
            switch (type) {
                case "user/message" -> hist.append(new Message.User(str(e, "content")));
                case "assistant/message" ->
                        hist.append(
                                new Message.Assistant(
                                        str(e, "content"), parseToolCalls(e.get("toolCalls"))));
                case "tool/result" ->
                        hist.append(
                                new Message.ToolResult(
                                        str(e, "callId"),
                                        str(e, "result"),
                                        Boolean.TRUE.equals(e.get("isError"))));
                default -> {
                    // context/snapshot、system/*、permission/decision、turn/*、session、未知类型：跳过
                }
            }
        }
        return hist;
    }

    /** 解析 assistant 事件里的 toolCalls 字段（List&lt;Map&gt; → List&lt;ToolCall&gt;） */
    private static List<ToolCall> parseToolCalls(Object raw) {
        if (!(raw instanceof List<?> list)) return null;
        List<ToolCall> calls = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> mm = (Map<String, Object>) m;
            calls.add(
                    new ToolCall(
                            String.valueOf(mm.get("id")),
                            String.valueOf(mm.get("name")),
                            String.valueOf(mm.get("argumentsJson"))));
        }
        return calls.isEmpty() ? null : calls;
    }

    private static String str(Map<String, Object> e, String key) {
        Object v = e.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static List<Map<String, Object>> readEvents(Path sessionJsonl) {
        try {
            List<Map<String, Object>> events = new ArrayList<>();
            for (String line : Files.readAllLines(sessionJsonl)) {
                if (line.isBlank()) continue;
                try {
                    events.add(JSON.readValue(line, new TypeReference<Map<String, Object>>() {}));
                } catch (IOException ignored) {
                    // 跳过坏行
                }
            }
            return events;
        } catch (IOException e) {
            throw new IllegalStateException("读取会话事件流失败: " + sessionJsonl, e);
        }
    }

    private SessionReplay() {}
}
