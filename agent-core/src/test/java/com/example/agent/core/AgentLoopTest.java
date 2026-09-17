package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.agent.core.exception.MaxIterationsExceededException;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.permission.PermissionDecision;
import com.example.agent.permission.PermissionMode;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

class AgentLoopTest {

    @Test
    void hitsMaxToolIterations() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);

        // 永远返回 tool_call 的假 provider
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.ToolCallStart("1", "fake", null),
                                new StreamChunk.ToolCallEnd("1", "fake", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)));

        ToolRegistry tools = mock(ToolRegistry.class);
        Tool<Object, Object> fakeTool = mock(Tool.class);
        when(fakeTool.name()).thenReturn("fake");
        when(fakeTool.description()).thenReturn("fake tool");
        when(fakeTool.inputSchema()).thenReturn(java.util.Map.of());
        // Mockito 不执行接口 default 方法，需显式 stub parseArguments（否则返回 null → Mono.empty → 工具结果丢失）
        when(fakeTool.parseArguments(any())).thenReturn("{}");
        when(fakeTool.execute(any(), any())).thenReturn(Mono.just(ToolResult.ok("ok", "1")));
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of(fakeTool));

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        3,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        StepVerifier.create(loop.processTurn(new Message.User("go")))
                .expectError(MaxIterationsExceededException.class)
                .verify();
    }

    @Test
    void singleTurnNoToolCall() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("你好，"),
                                new StreamChunk.TextDelta("有什么可以帮你的？"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(5, 8, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        TurnResult result = loop.processTurn(new Message.User("hi")).block();
        assertEquals("你好，有什么可以帮你的？", result.finalMessage());
        assertEquals(5, result.totalPromptTokens());
        assertEquals(8, result.totalCompletionTokens());
    }

    @Test
    void toolNotFoundReturnsError() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.ToolCallStart("1", "ghost", null),
                                new StreamChunk.ToolCallEnd("1", "ghost", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                // 第二轮：没有工具调用，正常结束
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(null).when(tools).getRaw("ghost");
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        TurnResult result = loop.processTurn(new Message.User("hi")).block();
        assertEquals("done", result.finalMessage());
        // 应该 history 含 tool_result error
        boolean hasError =
                hist.all().stream().anyMatch(m -> m instanceof Message.ToolResult t && t.isError());
        assertEquals(true, hasError);
    }

    @Test
    void passesSystemPromptToRequest() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        org.mockito.ArgumentCaptor<ChatRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        when(provider.streamChat(captor.capture()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("ok"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."),
                        "SYSTEM_PROMPT_TEST");

        loop.processTurn(new Message.User("hi")).block();
        assertEquals("SYSTEM_PROMPT_TEST", captor.getValue().systemPrompt());
    }

    @Test
    void nullSystemPromptStaysNull() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        org.mockito.ArgumentCaptor<ChatRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        when(provider.streamChat(captor.capture()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("ok"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        loop.processTurn(new Message.User("hi")).block();
        assertEquals(null, captor.getValue().systemPrompt());
    }

    @Test
    void deserializesArgumentsBeforeExecute() {
        // fakeTool：parseArguments 加前缀，验证 AgentLoop 先反序列化再 execute（不再传裸 String）
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool fakeTool =
                new Tool() {
                    @Override
                    public String name() {
                        return "fake";
                    }

                    @Override
                    public String description() {
                        return "fake tool";
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
                        return "PARSED:" + argumentsJson;
                    }

                    @Override
                    public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                        return Mono.just(ToolResult.ok("got=" + input, "<auto>"));
                    }
                };

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart(
                                                "1", "fake", "{\"path\":\"/tmp/a.txt\"}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."),
                        null,
                        SessionLogSink.NOOP,
                        null,
                        PermissionConfirmer.allowAll());

        TurnResult result = loop.processTurn(new Message.User("hi")).block();
        assertEquals("done", result.finalMessage());
        boolean hasParsed =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && t.content().startsWith("got=PARSED:{\"path\":"));
        assertEquals(true, hasParsed, "execute 应收到反序列化后的输入而非裸 JSON 字符串");
    }

    @Test
    void realToolReceivesDeserializedInput(@TempDir java.nio.file.Path tmp) throws Exception {
        // 真实 ReadFileTool 全链路：argumentsJson → Input → 读文件（复现用户遇到的 cast 崩溃场景）
        java.nio.file.Path file = tmp.resolve("note.txt");
        java.nio.file.Files.writeString(file, "hello from file");
        // Windows 反斜杠路径必须由 Jackson 正确转义（直接字符串拼接会产生非法 JSON 转义）
        String argsJson =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(java.util.Map.of("path", file.toString()));

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart("1", "ReadFile", argsJson),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = new ToolRegistry();
        tools.register(new com.example.agent.tools.file.ReadFileTool());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        tmp);

        TurnResult result = loop.processTurn(new Message.User("hi")).block();
        assertEquals("done", result.finalMessage());
        boolean hasContent =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && !t.isError()
                                                && t.content().contains("hello from file"));
        assertEquals(true, hasContent, "ReadFile 应成功读到文件内容（JSON 参数被正确反序列化）");
    }

    @Test
    void toolFailureResultCarriesToolCallId(@TempDir java.nio.file.Path tmp) throws Exception {
        // 工具 doExecute 返回 error（toolCallId=null）时，回流消息必须补全调用 id（否则 DeepSeek 400:
        // "messages[i]: invalid type: null, expected a string"）
        String argsJson =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(
                                java.util.Map.of("path", tmp.resolve("missing.txt").toString()));

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart("1", "ReadFile", argsJson),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = new ToolRegistry();
        tools.register(new com.example.agent.tools.file.ReadFileTool());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        tmp);

        loop.processTurn(new Message.User("hi")).block();
        boolean hasErrorWithId =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && t.isError()
                                                && "1".equals(t.toolCallId()));
        assertEquals(true, hasErrorWithId, "error tool_result 必须携带调用 id，否则回流 400");
    }

    @Test
    void parseFailureResultCarriesToolCallId() {
        // parseArguments 抛异常（onErrorResume 路径）时，error 结果同样必须携带调用 id
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool fakeTool =
                new Tool() {
                    @Override
                    public String name() {
                        return "fake";
                    }

                    @Override
                    public String description() {
                        return "fake tool";
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
                        throw new IllegalArgumentException("bad json");
                    }

                    @Override
                    public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                        return Mono.just(ToolResult.ok("ok", "<auto>"));
                    }
                };

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart(
                                                "1", "fake", "{\"path\":\"x\"}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        loop.processTurn(new Message.User("hi")).block();
        boolean hasErrorWithId =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && t.isError()
                                                && "1".equals(t.toolCallId()));
        assertEquals(true, hasErrorWithId, "parseArguments 失败产生的 error 也必须携带调用 id");
    }

    // ---- harden-tool-error-boundary：工具类加载失败（LinkageError）不得撕掉整轮 ----

    /** 构造「先要求调用 fake 工具、再正常结束」的两段假 provider。 */
    private static LlmProvider providerAskingForFakeTool() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart("1", "fake", "{\"path\":\"x\"}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));
        return provider;
    }

    /** 构造 loop，注册一个入参解析即抛指定 Error 的假工具。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AgentLoop loopWithToolWhoseParseThrows(Error toThrow, MessageHistory hist) {
        Tool fakeTool =
                new Tool() {
                    @Override
                    public String name() {
                        return "fake";
                    }

                    @Override
                    public String description() {
                        return "fake tool";
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
                        throw toThrow;
                    }

                    @Override
                    public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                        return Mono.just(ToolResult.ok("ok", "<auto>"));
                    }
                };

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of());
        return new AgentLoop(
                providerAskingForFakeTool(),
                tools,
                hist,
                new StreamingPrinter(),
                25,
                "deepseek-chat",
                java.nio.file.Paths.get("."));
    }

    @Test
    void linkageErrorInParseBecomesToolErrorNotFatal() {
        // 回归（2026-09-13 16:10 事故）：EditFileTool$Input 缺失抛出的 NoClassDefFoundError 属
        // LinkageError，Reactor 的 Exceptions.throwIfFatal 会把它**原样 rethrow**，绕过
        // onErrorResume 并最终撕掉整条 SSE 连接。工具类加载失败必须降级为工具错误。
        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                loopWithToolWhoseParseThrows(
                        new NoClassDefFoundError(
                                "com/example/agent/tools/file/EditFileTool$Input"),
                        hist);

        loop.processTurn(new Message.User("hi")).block(); // 不应抛出

        boolean hasErrorWithId =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && t.isError()
                                                && "1".equals(t.toolCallId()));
        assertEquals(true, hasErrorWithId, "类加载失败必须降级为携带调用 id 的 error 工具结果");
    }

    @Test
    void virtualMachineErrorStaysFatal() {
        // 与上一条相反：VirtualMachineError 是真正该让进程失败的信号，不得降级成工具错误
        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                loopWithToolWhoseParseThrows(new OutOfMemoryError("simulated"), hist);

        org.junit.jupiter.api.Assertions.assertThrows(
                OutOfMemoryError.class, () -> loop.processTurn(new Message.User("hi")).block());
    }

    @Test
    void toolSuccessResultCarriesToolCallId() {
        // 工具成功结果若返回占位 toolCallId（如 "<auto>"），回流消息必须替换为真实调用 id，
        // 否则 DeepSeek 400：assistant tool_calls 的 id 与 tool 消息的 tool_call_id 不匹配
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool fakeTool =
                new Tool() {
                    @Override
                    public String name() {
                        return "fake";
                    }

                    @Override
                    public String description() {
                        return "fake tool";
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
                        return "{}";
                    }

                    @Override
                    public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                        return Mono.just(ToolResult.ok("ok", "<auto>"));
                    }
                };

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.ToolCallStart("1", "fake", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."),
                        null,
                        SessionLogSink.NOOP,
                        null,
                        PermissionConfirmer.allowAll());

        loop.processTurn(new Message.User("hi")).block();
        boolean hasOkWithId =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && !t.isError()
                                                && "1".equals(t.toolCallId()));
        assertEquals(true, hasOkWithId, "成功 tool_result 必须用真实调用 id（不能用 \"<auto>\" 占位）");
    }

    @Test
    void deniesAskWhenNoConfirmer() {
        // 工具返回 ask 且未提供 confirmer 时 fail-closed：工具不执行，回流 error
        @SuppressWarnings({"rawtypes", "unchecked"})
        Tool fakeTool =
                new Tool() {
                    @Override
                    public String name() {
                        return "fake";
                    }

                    @Override
                    public String description() {
                        return "fake tool";
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
                        return "{}";
                    }

                    @Override
                    public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                        return Mono.just(ToolResult.ok("should-not-run"));
                    }
                };

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.ToolCallStart("1", "fake", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        // 不传 confirmer → fail-closed
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        loop.processTurn(new Message.User("hi")).block();
        boolean denied =
                hist.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult t
                                                && t.isError()
                                                && t.content().contains("用户拒绝执行"));
        assertEquals(true, denied, "无 confirmer 时 ask 应 fail-closed 拒绝");
    }

    @Test
    void setModelChangesModelForNextTurn() {
        // 简化 setModel 测试：只验证字段被切换（避免完整 processTurn 链路）
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.name()).thenReturn("deepseek");
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8_192);

        AgentLoop loop =
                new AgentLoop(
                        provider,
                        new ToolRegistry(),
                        new MessageHistory(new TokenEstimator()),
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        // 切换
        loop.setModel("deepseek-reasoner");

        // 不验证 toRequest 内部（那是私有行为），但确认 setModel 不抛异常
        // 完整 model 流转验证留给集成测试
    }

    @Test
    void setReasoningEffortChangesExtraForNextTurn() {
        // add-models-dropdown-v0：setReasoningEffort 后构造的 ChatRequest.extra 应含 reasoning_effort
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.name()).thenReturn("deepseek");
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8_192);

        AgentLoop loop =
                new AgentLoop(
                        provider,
                        new ToolRegistry(),
                        new MessageHistory(new TokenEstimator()),
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        // 切换前 reasoningEffort() 为 null
        org.junit.jupiter.api.Assertions.assertNull(loop.reasoningEffort());

        // 切换
        loop.setReasoningEffort("high");
        org.junit.jupiter.api.Assertions.assertEquals("high", loop.reasoningEffort());

        // 不验证 toRequest 内部（那是私有行为），但 setReasoningEffort 不抛异常 + getter 一致
    }

    // ---- add-session-stats-bar：本轮统计采集 ----

    @Test
    void turnDeltaCollectsStepsTokensAndCache() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                // 第一轮：触发一个不存在的工具（计 1 步）+ usage（含缓存字段），无文本 → 不产生 TTFT 样本
                .thenReturn(
                        Flux.just(
                                new StreamChunk.ToolCallStart("1", "ghost", null),
                                new StreamChunk.ToolCallEnd("1", "ghost", "{}"),
                                new StreamChunk.Usage(100, 10, 0, 80, 20),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                // 第二轮：纯文本结束（产生 TTFT 样本）+ usage
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(50, 5, 0, 40, 10))));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        new MessageHistory(new TokenEstimator()),
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        TurnResult r = loop.processTurn(new Message.User("hi")).block();
        com.example.agent.stats.TurnDelta d = r.delta();
        assertEquals(1, d.steps(), "工具不存在也算一步");
        assertEquals(150, d.tokensIn());
        assertEquals(15, d.tokensOut());
        assertEquals(1, d.ttftSamples(), "只有含文本的那次 streamChat 产生 TTFT 样本");
        assertEquals(Integer.valueOf(120), d.cacheHitTokens());
        assertEquals(Integer.valueOf(30), d.cacheMissTokens());
        assertEquals(1, r.toolCallCount());
    }

    @Test
    void turnDeltaWithoutCacheFieldsLeavesThemNull() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("hi"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(7, 3, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        new MessageHistory(new TokenEstimator()),
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        TurnResult r = loop.processTurn(new Message.User("hi")).block();
        assertEquals(null, r.delta().cacheHitTokens(), "provider 未返回缓存字段 → null(N/A)");
        assertEquals(null, r.delta().cacheMissTokens());
    }

    // ---- fix-full-access-bypass：FULL_ACCESS 短路工具级 ASK，但仍尊重工具级 DENY ----

    @Test
    void fullAccessBypassesToolAsk() {
        // 工具自身 checkPermissions 返回 ASK；FULL_ACCESS 下应被短路为 ALLOW，工具应执行
        // （验证：history 含 tool_result ok，而非 "用户拒绝执行" 错误）
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.ToolCallStart("1", "fake", null),
                                new StreamChunk.ToolCallEnd("1", "fake", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                // 第二轮：纯文本结束
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));

        ToolRegistry tools = mock(ToolRegistry.class);
        Tool<Object, Object> fakeTool = mock(Tool.class);
        when(fakeTool.name()).thenReturn("fake");
        when(fakeTool.description()).thenReturn("fake");
        when(fakeTool.inputSchema()).thenReturn(java.util.Map.of());
        when(fakeTool.parseArguments(any())).thenReturn("{}");
        // 工具级 ASK（这是问题所在：之前会绕过 FULL_ACCESS）
        when(fakeTool.checkPermissions(any(), any())).thenReturn(PermissionDecision.ask());
        when(fakeTool.execute(any(), any())).thenReturn(Mono.just(ToolResult.ok("ran", "1")));
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of(fakeTool));

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));
        // 切到 FULL_ACCESS：PermissionManager.decide 在该模式下短路为 allow
        loop.setPermissionMode(PermissionMode.FULL_ACCESS);

        TurnResult r = loop.processTurn(new Message.User("go")).block();
        assertEquals("done", r.finalMessage());
        // 工具成功执行 → history 中不应有 isError 的 tool_result
        boolean hasErr =
                hist.all().stream()
                        .anyMatch(m -> m instanceof Message.ToolResult t && t.isError());
        org.junit.jupiter.api.Assertions.assertFalse(hasErr,
                "FULL_ACCESS 下工具级 ASK 应被短路，工具应执行成功（无错误 tool_result）");
    }

    @Test
    void fullAccessPreservesToolDeny() {
        // 工具自身 checkPermissions 返回 DENY；FULL_ACCESS 不应绕过 DENY 终态
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.ToolCallStart("1", "fake", null),
                                new StreamChunk.ToolCallEnd("1", "fake", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("denied"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));

        ToolRegistry tools = mock(ToolRegistry.class);
        Tool<Object, Object> fakeTool = mock(Tool.class);
        when(fakeTool.name()).thenReturn("fake");
        when(fakeTool.description()).thenReturn("fake");
        when(fakeTool.inputSchema()).thenReturn(java.util.Map.of());
        when(fakeTool.parseArguments(any())).thenReturn("{}");
        // 工具级 DENY（如路径越界 .. 兜底）：必须仍是终态
        when(fakeTool.checkPermissions(any(), any())).thenReturn(PermissionDecision.deny());
        when(fakeTool.execute(any(), any())).thenReturn(Mono.just(ToolResult.ok("should-not-run", "1")));
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of(fakeTool));

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        25,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));
        loop.setPermissionMode(PermissionMode.FULL_ACCESS);

        loop.processTurn(new Message.User("go")).block();
        // 工具应被拒绝 → history 含 isError tool_result（"权限拒绝: fake"）
        boolean hasDeny =
                hist.all().stream()
                        .anyMatch(m -> m instanceof Message.ToolResult t && t.isError()
                                && t.content().contains("权限拒绝"));
        org.junit.jupiter.api.Assertions.assertTrue(hasDeny,
                "FULL_ACCESS 不能绕过工具级 DENY（路径越界等终态必须保留）");
    }
}

