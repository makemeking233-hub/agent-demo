package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.AgentPaths;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryIndex;
import com.example.agent.memory.MemoryScope;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 装配层记忆段测试（fix-memory-recall-wiring T5）。
 *
 * <p>这是本 change 的关键回归防线：此前单类测试（{@code MemoryRetrieverTest} /
 * {@code MemoryPromptBuilderTest}）全绿，但装配路径传空 query 导致召回链路不可达。
 * 本测试直接断言装配入口的产物形态——动态模式返回「基础段 + 记忆段来源」，静态模式内联全量索引。
 *
 * <p><b>数据隔离</b>（全局规则 §10）：通过 {@code agent.demo.home} 系统属性 + 临时 {@code user.dir}
 * 把 USER / PROJECT 两个 scope 都指向 {@link TempDir}，绝不触碰用户真实 {@code ~/.agent-demo/}。
 */
class AgentLoopFactoryMemoryTest {

    @TempDir Path tmp;

    private String originalHome;
    private String originalUserDir;

    @BeforeEach
    void isolateAgentHome() {
        originalHome = System.getProperty(AgentPaths.HOME_PROPERTY);
        originalUserDir = System.getProperty("user.dir");
        System.setProperty(AgentPaths.HOME_PROPERTY, tmp.toString());
        // PROJECT scope 从 user.dir 解析；一并指向临时目录，避免在真实工作区建 .agent-demo/
        System.setProperty("user.dir", tmp.resolve("cwd").toString());
    }

    @AfterEach
    void restoreAgentHome() {
        if (originalHome == null) {
            System.clearProperty(AgentPaths.HOME_PROPERTY);
        } else {
            System.setProperty(AgentPaths.HOME_PROPERTY, originalHome);
        }
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    /** 在临时 USER scope 目录写入一条记忆索引。 */
    private void writeUserMemory(String filename, String title, String desc) throws Exception {
        Path memDir = Path.of(tmp.toString(), ".agent-demo", "memory");
        Files.createDirectories(memDir);
        new MemoryIndex(memDir.resolve("MEMORY.md"), MemoryScope.USER)
                .write(List.of(new MemoryEntry(title, desc, filename, MemoryScope.USER)));
    }

    /** 复制一份配置，改 dynamicRetrieval 开关。 */
    private static AgentConfig withDynamicRetrieval(AgentConfig base, boolean dynamic) {
        return new AgentConfig(
                base.provider(),
                base.permission(),
                base.cost(),
                base.context(),
                base.shell(),
                base.memoryInject(),
                base.logging(),
                new AgentConfig.Memory(base.memory().sideQuery(), dynamic, base.memory().embedding()),
                base.mcp(),
                base.worktree(),
                base.plugins(),
                base.search(),
                base.voice());
    }

    @Test
    void dynamicModeReturnsBasePromptWithoutMemorySection() {
        AgentConfig cfg = AgentConfig.defaults();
        assertTrue(cfg.memory().dynamicRetrieval(), "缺省应为动态召回");

        AgentLoopFactory.PromptParts parts =
                AgentLoopFactory.buildSystemPromptParts(cfg, "deepseek-chat", null, null);

        assertNotNull(parts.memorySectionSource(), "动态模式应返回记忆段来源");
        assertFalse(
                parts.basePrompt().contains("Persistent Agent Memory"),
                "动态模式下基础段不应内联记忆段");
        assertTrue(parts.basePrompt().contains("agent-demo"), "基础段应含身份声明");
    }

    @Test
    void dynamicModeRetrievesRelevantEntriesOnDemand() throws Exception {
        writeUserMemory("java17.md", "Java 17 安装", "JDK 安装步骤与版本切换");
        AgentConfig cfg = AgentConfig.defaults();

        AgentLoopFactory.PromptParts parts =
                AgentLoopFactory.buildSystemPromptParts(cfg, "deepseek-chat", null, null);
        String section = parts.memorySectionSource().sectionFor("安装 Java");

        assertTrue(section.contains("(relevant)"), "应按 query 产出召回格式记忆段");
        assertTrue(section.contains("java17.md"), "应含命中条目的文件名");
    }

    @Test
    void staticModeInlinesFullIndexAndHasNoSource() throws Exception {
        writeUserMemory("java17.md", "Java 17 安装", "JDK 安装步骤与版本切换");
        AgentConfig cfg = withDynamicRetrieval(AgentConfig.defaults(), false);

        AgentLoopFactory.PromptParts parts =
                AgentLoopFactory.buildSystemPromptParts(cfg, "deepseek-chat", null, null);

        assertNull(parts.memorySectionSource(), "静态模式不应返回记忆段来源");
        assertTrue(
                parts.basePrompt().contains("Persistent Agent Memory"),
                "静态模式应内联记忆段模板");
        assertTrue(parts.basePrompt().contains("java17.md"), "静态模式应含全量索引条目");
        assertFalse(parts.basePrompt().contains("(relevant)"), "静态模式不应出现召回格式");
    }

    @Test
    void legacyBuildSystemPromptReturnsStaticCompletePrompt() throws Exception {
        writeUserMemory("java17.md", "Java 17 安装", "JDK 安装步骤与版本切换");
        AgentConfig cfg = AgentConfig.defaults();

        String prompt = AgentLoopFactory.buildSystemPrompt(cfg, "deepseek-chat", null);

        // 旧 API 无 query 上下文 → 始终返回含静态全量索引的完整 prompt
        assertTrue(prompt.contains("Persistent Agent Memory"));
        assertTrue(prompt.contains("java17.md"));
        assertTrue(prompt.contains("agent-demo"));
    }

    @Test
    void noMemoryFilesStillYieldsUsablePrompt() {
        // 临时目录下无任何记忆文件：动态模式仍应返回来源（sectionFor 返回空串），基础段完整
        AgentConfig cfg = AgentConfig.defaults();

        AgentLoopFactory.PromptParts parts =
                AgentLoopFactory.buildSystemPromptParts(cfg, "deepseek-chat", null, null);

        assertNotNull(parts.memorySectionSource());
        assertTrue(parts.basePrompt().contains("agent-demo"));
        assertTrue(
                parts.memorySectionSource().sectionFor("任意问题").contains("Persistent Agent Memory")
                        || parts.memorySectionSource().sectionFor("任意问题").isEmpty(),
                "无记忆文件时记忆段应为空串或仅含模板");
    }

    // ---- T6.1 端到端装配连线：buildLoop 必须把记忆段来源接到 AgentLoop 上 ----

    /**
     * 这是最接近真实运行路径的回归防线。前面的用例分别验证了「装配产物形态」与「AgentLoop 每轮
     * 拼装」，但都无法发现「buildLoop 忘记把 memorySectionSource 传给 AgentLoop」这类连线遗漏——
     * 而这正是本次要修的缺陷形态（对象被创建却没被使用）。
     */
    @Test
    void buildLoopWiresMemorySourceAndInjectsRecalledEntry() throws Exception {
        writeUserMemory("java17.md", "Java 17 安装", "JDK 安装步骤与版本切换");
        AgentConfig cfg = AgentConfig.defaults();

        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("已了解你的 Java 环境。"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(1, 1, 0))));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        AgentLoop loop =
                AgentLoopFactory.buildLoop(
                        cfg,
                        provider,
                        tools,
                        new MessageHistory(new TokenEstimator()),
                        new StreamingPrinter(),
                        "deepseek-chat",
                        null,
                        null,
                        null);

        loop.processTurn(new Message.User("安装 Java")).block();

        org.mockito.ArgumentCaptor<ChatRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.atLeastOnce())
                .streamChat(captor.capture());
        String systemPrompt = captor.getValue().systemPrompt();

        assertTrue(systemPrompt.contains("agent-demo"), "应保留基础段（身份声明）");
        assertTrue(
                systemPrompt.contains("(relevant)"),
                "buildLoop 必须把记忆段来源接到 AgentLoop 上（本 change 的核心修复点）");
        assertTrue(systemPrompt.contains("java17.md"), "当轮提问相关的记忆条目应被注入");
        assertFalse(
                systemPrompt.contains("# Memory Index"),
                "动态模式下不应同时内联全量索引（避免索引与召回重复）");
    }
}
