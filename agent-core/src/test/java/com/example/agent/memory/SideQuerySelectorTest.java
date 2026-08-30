package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

import java.util.List;

class SideQuerySelectorTest {

    private LlmProvider providerReturning(String text) {
        return new LlmProvider() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.just(new StreamChunk.TextDelta(text),
                        new StreamChunk.Finished(FinishReason.STOP, null));
            }

            @Override
            public int contextWindow() {
                return 1000;
            }

            @Override
            public int maxOutputTokens() {
                return 1000;
            }
        };
    }

    private LlmProvider failingProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.error(new RuntimeException("network"));
            }

            @Override
            public int contextWindow() {
                return 1000;
            }

            @Override
            public int maxOutputTokens() {
                return 1000;
            }
        };
    }

    private List<MemoryEntry> candidates() {
        return List.of(
                new MemoryEntry("Java 17 安装", "JDK 安装步骤", "java17.md", MemoryScope.USER),
                new MemoryEntry("Python 装饰器", "wraps 用法", "py.md", MemoryScope.USER));
    }

    @Test
    void selectsRelevantFilenames() {
        var selector = new SideQuerySelector(providerReturning("java17.md\n"), "deepseek-chat");
        var result = selector.select("如何安装 Java", candidates(), 1);
        assertEquals(List.of("java17.md"), result);
    }

    @Test
    void returnsEmptyOnProviderFailure() {
        var selector = new SideQuerySelector(failingProvider(), "deepseek-chat");
        var result = selector.select("如何安装 Java", candidates(), 2);
        assertTrue(result.isEmpty(), "provider 故障应静默降级为空");
    }

    @Test
    void returnsEmptyWhenProviderNull() {
        var selector = new SideQuerySelector(null, "deepseek-chat");
        var result = selector.select("如何安装 Java", candidates(), 2);
        assertTrue(result.isEmpty());
    }

    @Test
    void capsResultAtK() {
        var selector = new SideQuerySelector(providerReturning("a.md\nb.md\nc.md\n"), "deepseek-chat");
        // 候选仅 2 个，输出 3 个 filename，但 limit 截断
        var result = selector.select("q", candidates(), 2);
        assertTrue(result.size() <= 2);
    }
}
