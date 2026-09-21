package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.llm.ToolCall;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定位「assistant(tool_calls) 缺对应 tool 消息 → 400」缺陷：多工具调用 + 一个工具失败场景，
 * 检查第 2 轮发给模型的 messages 里每个 assistant.tool_calls 是否都有对应 tool 消息
 * （包括失败的那个）。若失配，即为 400 根因。
 */
class AgentLoopToolPairingTest {

    @Test
    void everyToolCallHasMatchingToolResultWhenOneFails(@TempDir Path tmp) {
        AtomicReference<List<Message>> secondMsgs = new AtomicReference<>();

        LlmProvider mockProvider = mock(LlmProvider.class);
        when(mockProvider.contextWindow()).thenReturn(100_000);
        when(mockProvider.maxOutputTokens()).thenReturn(8192);
        when(mockProvider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.ToolCallStart("c1", "oktool", "{}"),
                                new StreamChunk.ToolCallStart("c2", "failtool", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        // ok tool 成功
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool okTool = new Tool() {
            @Override public String name() { return "oktool"; }
            @Override public String description() { return "ok"; }
            @Override public java.util.Map<String, Object> inputSchema() { return java.util.Map.of(); }
            @Override public String renderUse(Object i) { return "ok()"; }
            @Override public String renderResult(Object o) { return String.valueOf(o); }
            @Override public Object parseArguments(String a) { return a; }
            @Override public Mono<ToolResult<Object>> execute(Object i, ToolContext c) {
                return Mono.just(ToolResult.ok("result-c1", "c1"));
            }
        };
        // fail tool 抛异常（模拟工具失败，回流 error 结果）
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool failTool = new Tool() {
            @Override public String name() { return "failtool"; }
            @Override public String description() { return "fail"; }
            @Override public java.util.Map<String, Object> inputSchema() { return java.util.Map.of(); }
            @Override public String renderUse(Object i) { return "fail()"; }
            @Override public String renderResult(Object o) { return String.valueOf(o); }
            @Override public Object parseArguments(String a) { return a; }
            @Override public Mono<ToolResult<Object>> execute(Object i, ToolContext c) {
                return Mono.error(new RuntimeException("boom"));
            }
        };

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(okTool).when(tools).getRaw("oktool");
        doReturn(failTool).when(tools).getRaw("failtool");
        @SuppressWarnings("rawtypes")
        List toolsList = List.of(okTool, failTool);
        when(tools.list()).thenReturn(toolsList);

        LlmProvider capturing = new LlmProvider() {
            int call = 0;
            @Override public Flux<StreamChunk> streamChat(ChatRequest req) {
                call++;
                if (call == 2) secondMsgs.set(req.messages());
                return mockProvider.streamChat(req);
            }
            @Override public String name() { return "deepseek"; }
            @Override public int contextWindow() { return 100_000; }
            @Override public int maxOutputTokens() { return 8192; }
        };

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        new AgentLoop(
                capturing, tools, hist, new StreamingPrinter(), 25, "deepseek-chat", tmp,
                null, SessionLogSink.NOOP, null, PermissionConfirmer.allowAll())
                .processTurn(new Message.User("go"))
                .block();

        List<Message> msgs = secondMsgs.get();
        assertTrue(msgs != null && !msgs.isEmpty(), "第 2 轮应有请求");

        // 收集所有 assistant.tool_calls 的 id
        List<String> asstIds = new ArrayList<>();
        for (Message m : msgs) {
            if (m instanceof Message.Assistant a && a.toolCalls() != null) {
                a.toolCalls().forEach(tc -> asstIds.add(tc.id()));
            }
        }
        // 收集所有 tool 消息的 tool_call_id
        List<String> toolIds = new ArrayList<>();
        for (Message m : msgs) {
            if (m instanceof Message.ToolResult t) toolIds.add(t.toolCallId());
        }
        // 关键断言：每个 assistant.tool_calls.id 都出现在 tool 消息里（包括失败的那个 c2）
        // 若失败，说明"多工具 + 一工具失败"时失败工具的结果未回流 → 发给模型的 tool_calls 与 tool 消息失配 → DeepSeek 400
        for (String id : asstIds) {
            assertTrue(
                    toolIds.contains(id),
                    "assistant.tool_calls id=[" + id + "] 应有对应 tool 消息，但 tool 消息 ids=" + toolIds);
        }
    }

    /** 是否存在无前置 {@code assistant.tool_calls} 的 tool_result（即反向孤儿）。 */
    private static boolean hasOrphanToolResult(List<Message> msgs) {
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (Message m : msgs) {
            if (m instanceof Message.Assistant a && a.toolCalls() != null) {
                for (ToolCall tc : a.toolCalls()) declared.add(tc.id());
            } else if (m instanceof Message.ToolResult tr && !declared.contains(tr.toolCallId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 请求路径的双向自愈（snip-pairing-repair）。
     *
     * <p>内存历史同时含两种污染：{@code c0} 的 tool 结果没有前置 tool_calls（反向孤儿，历史前缀被
     * {@code snip} 裁掉时的产物）、{@code c9} 的 tool_calls 没有结果（正向悬挂，回合被异常打断时的
     * 产物）。此前 `toRequest` 只修正向，反向孤儿会原样发给上游 → 400。
     */
    @Test
    void requestRepairsBothPairingDirectionsOnDirtyHistory(@TempDir Path tmp) {
        AtomicReference<List<Message>> sent = new AtomicReference<>();

        LlmProvider capturing =
                new LlmProvider() {
                    @Override
                    public Flux<StreamChunk> streamChat(ChatRequest req) {
                        sent.set(req.messages());
                        return Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0)));
                    }

                    @Override
                    public String name() {
                        return "deepseek";
                    }

                    @Override
                    public int contextWindow() {
                        return 100_000;
                    }

                    @Override
                    public int maxOutputTokens() {
                        return 8192;
                    }
                };

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        hist.append(new Message.User("上一轮"));
        hist.append(new Message.ToolResult("c0", "孤儿结果", false));
        hist.append(new Message.Assistant("", List.of(new ToolCall("c9", "ReadFile", "{}"))));
        int dirtySize = hist.size();

        ToolRegistry tools = mock(ToolRegistry.class);
        @SuppressWarnings("rawtypes")
        List emptyTools = List.of();
        when(tools.list()).thenReturn(emptyTools);

        new AgentLoop(
                        capturing,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-v4-flash",
                        tmp,
                        null,
                        SessionLogSink.NOOP,
                        null,
                        PermissionConfirmer.allowAll())
                .processTurn(new Message.User("go"))
                .block();

        List<Message> msgs = sent.get();
        assertNotNull(msgs, "应发出请求");

        // 正向：c9 已被补上合成错误结果
        assertTrue(
                ToolCallPairing.danglingCallIds(msgs).isEmpty(),
                "正向悬挂应已补齐，实际 dangling=" + ToolCallPairing.danglingCallIds(msgs));
        // 反向：c0 已被补上合成 assistant 骨架
        assertFalse(hasOrphanToolResult(msgs), "反向孤儿应已补齐骨架，否则发出去必被上游 400");
        // 发出的列表已是修复不动点（连续两次修复不再新增）
        assertEquals(
                msgs,
                ToolCallPairing.repairOrphanResults(ToolCallPairing.repair(msgs)),
                "请求列表应是配对修复的不动点");
        // 修复只作用于待发送列表：内存历史保持原样（本轮只多了 user + assistant 两条）
        assertEquals(dirtySize + 2, hist.size(), "内存历史条数不应被修复改变");
        assertTrue(
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult tr
                                                && tr.toolCallId().equals("c0")),
                "内存历史仍应保留原始孤儿结果（修复不写回）");
    }
}

