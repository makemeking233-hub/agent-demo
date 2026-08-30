package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class MemoryRetrieverTest {
    @TempDir Path tmp;

    private AgentConfig.SideQuery sq() {
        return new AgentConfig.SideQuery(true, 8, 3);
    }

    private MemoryEntry entry(String title, String desc, String file, MemoryScope scope) {
        return new MemoryEntry(title, desc, file, scope);
    }

    @Test
    void literalRecallOnlyWhenNoProvider() throws Exception {
        Path base = tmp.resolve("base");
        var userDir = new MemoryDir(base.resolve("mem"), MemoryScope.USER);
        var index = new MemoryIndex(userDir.indexFile(), MemoryScope.USER);
        index.write(List.of(entry("Java 17 安装", "JDK 安装", "java17.md", MemoryScope.USER)));

        var retriever = new MemoryRetriever(null, "deepseek-chat", new MemoryRecall(), sq());
        Map<MemoryScope, List<MemoryEntry>> result =
                retriever.retrieve("安装 Java", List.of(userDir), 5);
        assertTrue(result.containsKey(MemoryScope.USER));
        assertEquals("java17.md", result.get(MemoryScope.USER).get(0).filename());
    }

    @Test
    void sideQuerySupplementsWhenLiteralMisses() throws Exception {
        Path base = tmp.resolve("base");
        var userDir = new MemoryDir(base.resolve("mem"), MemoryScope.USER);
        var index = new MemoryIndex(userDir.indexFile(), MemoryScope.USER);
        // 无字面重叠候选（描述不含 query 词），只能靠 sideQuery
        index.write(List.of(
                entry("DeepSeek 配置", "API key 设置", "ds.md", MemoryScope.USER),
                entry("Rust 所有权", "borrow checker", "rust.md", MemoryScope.USER),
                entry("Python 装饰器", "wraps 用法", "py.md", MemoryScope.USER)));

        // fake provider 返回 ds.md → sideQuery 应把它作为补充条目召回
        LlmProvider fake = new LlmProvider() {
            @Override
            public String name() { return "fake"; }
            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.just(new StreamChunk.TextDelta("ds.md\n"),
                        new StreamChunk.Finished(FinishReason.STOP, null));
            }
            @Override
            public int contextWindow() { return 1000; }
            @Override
            public int maxOutputTokens() { return 1000; }
        };
        var retriever = new MemoryRetriever(fake, "deepseek-chat", new MemoryRecall(), sq());
        Map<MemoryScope, List<MemoryEntry>> result =
                retriever.retrieve("怎么配置 API key", List.of(userDir), 5);
        assertTrue(result.containsKey(MemoryScope.USER));
        List<MemoryEntry> hits = result.get(MemoryScope.USER);
        assertTrue(hits.stream().anyMatch(e -> e.filename().equals("ds.md")), "sideQuery 应补充 ds.md");
    }

    @Test
    void sideQueryDisabledUsesOnlyLiteral() throws Exception {
        Path base = tmp.resolve("base");
        var userDir = new MemoryDir(base.resolve("mem"), MemoryScope.USER);
        var index = new MemoryIndex(userDir.indexFile(), MemoryScope.USER);
        index.write(List.of(entry("Java 17", "JDK 安装", "java17.md", MemoryScope.USER)));

        var retriever = new MemoryRetriever(null, "deepseek-chat", new MemoryRecall(),
                new AgentConfig.SideQuery(false, 8, 3));
        Map<MemoryScope, List<MemoryEntry>> result =
                retriever.retrieve("配置 API key", List.of(userDir), 5);
        // 查询与候选无字面重叠 → 空
        assertTrue(result.isEmpty() || result.getOrDefault(MemoryScope.USER, List.of()).isEmpty());
    }

    @Test
    void localScopeIsSkipped() throws Exception {
        Path base = tmp.resolve("base");
        var localDir = MemoryDir.forScope(MemoryScope.LOCAL, base.toString(), base.toString());
        var retriever = new MemoryRetriever(null, "deepseek-chat", new MemoryRecall(), sq());
        Map<MemoryScope, List<MemoryEntry>> result =
                retriever.retrieve("anything", List.of(localDir), 5);
        assertTrue(result.isEmpty(), "LOCAL 无磁盘，不返回条目");
    }
}
