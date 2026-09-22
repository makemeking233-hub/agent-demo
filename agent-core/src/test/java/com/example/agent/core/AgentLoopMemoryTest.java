package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.example.agent.memory.MemorySectionSource;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AgentLoop 每轮动态记忆段测试（fix-memory-recall-wiring T4）。
 *
 * <p>覆盖：记忆段按当轮 query 追加到 system prompt；同一轮内多次请求组装只解析一次（轮内缓存）；
 * 不同轮次分别解析；无来源时 system prompt 保持构造值不变（向后兼容）。
 */
class AgentLoopMemoryTest {

    /** 构造时传入的基础 system prompt（不含记忆段）。 */
    private static final String BASE_PROMPT = "# Identity\n你是 agent-demo。";

    /** 记忆段文本（含 (relevant) 指纹）。 */
    private static final String SECTION = "### USER Scope (relevant)\n\n- java17.md — JDK 安装\n";

    /** 记录 query 的假记忆段来源，便于断言调用次数与参数。 */
    private static final class RecordingSource implements MemorySectionSource {
        final List<String> queries = new ArrayList<>();
        private final String section;

        RecordingSource(String section) {
            this.section = section;
        }

        @Override
        public String sectionFor(String query) {
            queries.add(query);
            return section;
        }
    }

    /** 只返回文本、无工具调用的 provider。 */
    private static LlmProvider textProvider() {
        LlmProvider p = mock(LlmProvider.class);
        when(p.contextWindow()).thenReturn(100_000);
        when(p.maxOutputTokens()).thenReturn(8192);
        when(p.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("好"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));
        return p;
    }

    /** 空工具表。 */
    private static ToolRegistry emptyTools() {
        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());
        return tools;
    }

    /** 带记忆段来源的 AgentLoop。 */
    private static AgentLoop loopWith(
            LlmProvider provider, ToolRegistry tools, MemorySectionSource source) {
        return new AgentLoop(
                provider,
                tools,
                new MessageHistory(new TokenEstimator()),
                new StreamingPrinter(),
                5,
                "deepseek-chat",
                Paths.get("."),
                BASE_PROMPT,
                source);
    }

    /** 捕获最后一次发给 provider 的 ChatRequest。 */
    private static ChatRequest captured(LlmProvider provider) {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.atLeastOnce())
                .streamChat(captor.capture());
        return captor.getValue();
    }

    @Test
    void memorySectionAppendedToSystemPrompt() {
        LlmProvider provider = textProvider();
        var source = new RecordingSource(SECTION);
        AgentLoop loop = loopWith(provider, emptyTools(), source);

        loop.processTurn(new Message.User("安装 Java")).block();

        ChatRequest req = captured(provider);
        assertTrue(req.systemPrompt().startsWith(BASE_PROMPT), "基础段应原样保留");
        assertTrue(req.systemPrompt().contains("(relevant)"), "记忆段应被追加");
        assertTrue(req.systemPrompt().contains("java17.md"));
        assertEquals(List.of("安装 Java"), source.queries, "应以当轮 user 消息为 query");
    }

    @Test
    void memorySectionResolvedOncePerTurn() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        // 第一次返回 tool_call（触发工具执行 + 递归 toRequest），第二次返回纯文本
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.ToolCallStart("1", "fake", null),
                                new StreamChunk.ToolCallEnd("1", "fake", "{}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("done"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));

        ToolRegistry tools = mock(ToolRegistry.class);
        @SuppressWarnings("unchecked")
        Tool<Object, Object> fakeTool = mock(Tool.class);
        when(fakeTool.name()).thenReturn("fake");
        when(fakeTool.description()).thenReturn("fake tool");
        when(fakeTool.inputSchema()).thenReturn(Map.of());
        when(fakeTool.parseArguments(any())).thenReturn("{}");
        when(fakeTool.execute(any(), any())).thenReturn(Mono.just(ToolResult.ok("ok", "1")));
        doReturn(fakeTool).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of(fakeTool));

        var source = new RecordingSource(SECTION);
        AgentLoop loop = loopWith(provider, tools, source);

        loop.processTurn(new Message.User("安装 Java")).block();

        assertEquals(1, source.queries.size(), "同一轮内记忆段只应解析一次（轮内缓存生效）");
    }

    @Test
    void memorySectionResolvedPerTurnForDifferentQueries() {
        LlmProvider provider = textProvider();
        var source = new RecordingSource(SECTION);
        AgentLoop loop = loopWith(provider, emptyTools(), source);

        loop.processTurn(new Message.User("第一个问题")).block();
        loop.processTurn(new Message.User("第二个问题")).block();

        assertEquals(
                List.of("第一个问题", "第二个问题"), source.queries, "不同轮次应分别按各自 query 解析");
    }

    @Test
    void nullSourceKeepsSystemPromptUnchanged() {
        LlmProvider provider = textProvider();
        AgentLoop loop = loopWith(provider, emptyTools(), null);

        loop.processTurn(new Message.User("安装 Java")).block();

        assertEquals(BASE_PROMPT, captured(provider).systemPrompt(), "无来源时 system prompt 不应被改动");
    }

    @Test
    void blankSectionNotAppended() {
        LlmProvider provider = textProvider();
        var source = new RecordingSource("");
        AgentLoop loop = loopWith(provider, emptyTools(), source);

        loop.processTurn(new Message.User("安装 Java")).block();

        assertEquals(BASE_PROMPT, captured(provider).systemPrompt(), "空记忆段不应引入多余分隔符");
    }
}
