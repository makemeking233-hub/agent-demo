package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.core.exception.MaxIterationsExceededException;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
                (StreamChunk) new StreamChunk.ToolCallStart("1", "fake"),
                new StreamChunk.ToolCallEnd("1", "fake", "{}"),
                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)));

    ToolRegistry tools = mock(ToolRegistry.class);
    Tool<Object, Object> fakeTool = mock(Tool.class);
    when(fakeTool.name()).thenReturn("fake");
    when(fakeTool.description()).thenReturn("fake tool");
    when(fakeTool.inputSchema()).thenReturn(java.util.Map.of());
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
                new StreamChunk.Finished(FinishReason.STOP, new StreamChunk.Usage(5, 8))));

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
                new StreamChunk.ToolCallStart("1", "ghost"),
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
}
