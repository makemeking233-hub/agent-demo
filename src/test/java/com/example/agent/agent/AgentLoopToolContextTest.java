package com.example.agent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.provider.FinishReason;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.StreamChunk;
import com.example.agent.provider.TokenEstimator;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 覆盖 C1 / C3 修复：
 *
 * <ul>
 *   <li>C1：工具调用时 ToolContext 正确传递（不再传 null 导致 NPE）
 *   <li>C3：setHistory() 切换后，新 turn 写入新 history
 * </ul>
 *
 * <p>不依赖 ReadFileTool 的 JSON→Input 解析（v0.1 已知缺陷，留 v0.2）。
 */
class AgentLoopToolContextTest {

  @Test
  void toolReceivesWorkingDirectoryFromContext(@TempDir Path tmp) {
    // fake tool：把 ctx.workingDirectory() 拼到输出，验证 ToolContext 正确传递
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
          public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
            return Mono.just(ToolResult.ok("cwd=" + ctx.workingDirectory().toString(), "<auto>"));
          }
        };

    LlmProvider provider = mock(LlmProvider.class);
    when(provider.contextWindow()).thenReturn(100_000);
    when(provider.maxOutputTokens()).thenReturn(8192);
    when(provider.streamChat(any()))
        .thenReturn(
            Flux.just(
                (StreamChunk) new StreamChunk.ToolCallStart("1", "fake"),
                new StreamChunk.ToolCallEnd("1", "fake", "{}"),
                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
        .thenReturn(
            Flux.just(
                new StreamChunk.TextDelta("ok"),
                new StreamChunk.Finished(FinishReason.STOP, new StreamChunk.Usage(5, 8))));

    ToolRegistry tools = mock(ToolRegistry.class);
    doReturn(fakeTool).when(tools).getRaw("fake");
    @SuppressWarnings("rawtypes")
    List toolsList = List.of(fakeTool);
    when(tools.list()).thenReturn(toolsList);

    MessageHistory hist = new MessageHistory(new TokenEstimator());
    AgentLoop loop =
        new AgentLoop(provider, tools, hist, new StreamingPrinter(), 25, "deepseek-chat", tmp);

    // 不应抛 NPE：ToolContext 正确传递 workingDirectory
    var result = loop.processTurn(new Message.User("go")).block();
    assertNotNull(result);
    assertEquals("ok", result.finalMessage());

    // tool_result 应包含 workingDirectory 信息（验证 ToolContext 正确传递）
    boolean hasAnyResult =
        hist.all().stream()
            .anyMatch(
                m ->
                    m instanceof Message.ToolResult t
                        && !t.isError()
                        && t.content() != null
                        && t.content().startsWith("cwd="));
    assertTrue(hasAnyResult, "fake tool 应成功执行并把 workingDirectory 写进 tool_result");
  }

  @Test
  void setHistorySwitchesContainerForFutureTurns(@TempDir Path tmp) {
    LlmProvider provider = mock(LlmProvider.class);
    when(provider.contextWindow()).thenReturn(100_000);
    when(provider.maxOutputTokens()).thenReturn(8192);
    when(provider.streamChat(any()))
        .thenReturn(
            Flux.just(
                new StreamChunk.TextDelta("reply"),
                new StreamChunk.Finished(FinishReason.STOP, new StreamChunk.Usage(5, 5))));

    ToolRegistry tools = mock(ToolRegistry.class);
    when(tools.list()).thenReturn(List.of());

    MessageHistory hist1 = new MessageHistory(new TokenEstimator());
    AgentLoop loop =
        new AgentLoop(provider, tools, hist1, new StreamingPrinter(), 25, "deepseek-chat", tmp);

    // 第一轮 turn：写入 hist1
    loop.processTurn(new Message.User("msg-1")).block();
    assertEquals(2, hist1.size(), "user + assistant");

    // /clear：切换到 hist2（保留旧 hist1 作为历史）
    MessageHistory hist2 = new MessageHistory(new TokenEstimator());
    loop.setHistory(hist2);

    // 第二轮 turn：应写入 hist2 而不是 hist1
    loop.processTurn(new Message.User("msg-2")).block();
    // setHistory 是切换（保留旧 hist1），不是清空；hist2 接收第二轮 turn 的写入
    assertEquals(2, hist1.size(), "hist1 保留第一轮 turn 内容");
    assertEquals(2, hist2.size(), "hist2 接收第二轮 turn 的写入");
  }
}
