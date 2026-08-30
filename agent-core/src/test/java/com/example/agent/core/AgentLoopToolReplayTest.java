package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 复现「assistant(tool_calls) 缺对应 tool 消息 → DeepSeek 400」缺陷：
 * 用真实 DeepSeek SSE 格式（ToolCallStart 携带完整参数 + Finished(TOOL_CALLS)，无单独 ToolCallEnd）
 * 驱动 AgentLoop，检查第 2 轮发给模型的 messages 是否满足
 * 「每个 assistant.tool_calls 都有对应 tool 消息」。
 */
class AgentLoopToolReplayTest {

    @Test
    void toolMessagesFollowToolCallsInNextTurn(@TempDir Path tmp) {
        // 捕获第 2 轮发给模型的 ChatRequest.messages
        AtomicReference<List<Message>> secondRequestMsgs = new AtomicReference<>();

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                // 第 1 轮：返回真实 DeepSeek tool_call 格式（ToolCallStart 带完整参数，无 ToolCallEnd）
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart(
                                                "call_1", "fake", "{\"path\":\"/tmp/a.txt\"}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                // 第 2 轮：返回普通文本（结束）
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(FinishReason.STOP, new StreamChunk.Usage(1, 1))));

        // fake tool：成功返回
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool fakeTool =
                new Tool() {
                    @Override
                    public String name() {
                        return "fake";
                    }
                    @Override
                    public String description() {
                        return "fake";
                    }
                    @Override
                    public java.util.Map<String, Object> inputSchema() {
                        return java.util.Map.of();
                    }
                    @Override
                    public String renderUse(Object input) {
                        return "fake()";
                    }
                    @Override
                    public String renderResult(Object output) {
                        return String.valueOf(output);
                    }
                    @Override
                    public Object parseArguments(String argumentsJson) {
                        return argumentsJson;
                    }
                    @Override
                    public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                        return Mono.just(ToolResult.ok("file-content", "call_1"));
                    }
                };

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(fakeTool).when(tools).getRaw("fake");
        @SuppressWarnings("rawtypes")
        List toolsList = List.of(fakeTool);
        when(tools.list()).thenReturn(toolsList);

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider, tools, hist, new StreamingPrinter(), 25, "deepseek-chat", tmp,
                        null, SessionLogSink.NOOP, null, PermissionConfirmer.allowAll());

        // 借助 spy 捕获 provider.streamChat 第 2 次的 ChatRequest.messages
        // 用一个包装 provider：记录第 2 次调用收到的 messages
        LlmProvider capturingProvider = new LlmProvider() {
            int call = 0;
            @Override public Flux<StreamChunk> streamChat(ChatRequest req) {
                call++;
                if (call == 2) secondRequestMsgs.set(req.messages());
                // 转发到 mock
                return provider.streamChat(req);
            }
            @Override public String name() { return "deepseek"; }
            @Override public int contextWindow() { return 100_000; }
            @Override public int maxOutputTokens() { return 8192; }
        };

        AgentLoop loop2 =
                new AgentLoop(
                        capturingProvider, tools, hist, new StreamingPrinter(), 25, "deepseek-chat", tmp,
                        null, SessionLogSink.NOOP, null, PermissionConfirmer.allowAll());

        loop2.processTurn(new Message.User("go")).block();

        // 校验第 2 轮 messages 里：每个 assistant 的 tool_calls 都应有对应 tool 消息
        List<Message> msgs = secondRequestMsgs.get();
        assertTrue(msgs != null && !msgs.isEmpty(), "第 2 轮应有请求");
        boolean hasAssistantToolCalls =
                msgs.stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.Assistant a
                                                && a.toolCalls() != null
                                                && !a.toolCalls().isEmpty());
        assertTrue(hasAssistantToolCalls, "第 2 轮 messages 应含带 tool_calls 的 assistant");
        // 检查 tool_calls 与 tool 消息数量配对：assistant.tool_calls 数量 <= tool 消息数量
        long asstToolCalls =
                msgs.stream()
                        .mapToLong(
                                m -> m instanceof Message.Assistant a && a.toolCalls() != null
                                        ? a.toolCalls().size() : 0)
                        .sum();
        long toolMsgs =
                msgs.stream().filter(m -> m instanceof Message.ToolResult).count();
        assertTrue(
                toolMsgs >= asstToolCalls,
                "assistant.tool_calls(" + asstToolCalls + ") 应有 >= 数量的 tool 消息，但只有 " + toolMsgs);
        // 且所有 tool 消息的 tool_call_id 都能在 assistant.tool_calls 里找到
        List<String> asstIds =
                msgs.stream()
                        .filter(Message.Assistant.class::isInstance)
                        .flatMap(m -> ((Message.Assistant) m).toolCalls().stream())
                        .map(com.example.agent.llm.ToolCall::id)
                        .toList();
        msgs.stream()
                .filter(Message.ToolResult.class::isInstance)
                .map(m -> ((Message.ToolResult) m).toolCallId())
                .forEach(
                        id ->
                                assertTrue(
                                        asstIds.contains(id),
                                        "tool 消息 tool_call_id=[" + id + "] 应在 assistant.tool_calls 中找到"));
    }
}
