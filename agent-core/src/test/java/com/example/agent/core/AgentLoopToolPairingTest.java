package com.example.agent.core;

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
                                new StreamChunk.Finished(FinishReason.STOP, new StreamChunk.Usage(1, 1))));

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
}
