package com.example.agent.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * SystemPromptBuilder 单元测试。
 *
 * <p>覆盖：默认模板加载、provider/model 占位符注入、memory 段注入、存储位置段注入、附加指引注入、用户覆盖优先。
 */
class SystemPromptBuilderTest {

    @Test
    void loadsDefaultTemplate() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("deepseek", "deepseek-chat", "", "", List.of(), null);
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        // 身份声明（模型无关 + 占位符已替换）
        assertTrue(prompt.contains("agent-demo"));
        assertTrue(prompt.contains("deepseek-chat"));
        assertTrue(prompt.contains("deepseek"));
    }

    @Test
    void replacesProviderAndModelPlaceholders() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("minimax", "MiniMax-Text-01", "", "", List.of(), null);
        assertTrue(prompt.contains("minimax"));
        assertTrue(prompt.contains("MiniMax-Text-01"));
        // 模板中不应残留未替换占位符
        assertFalse(prompt.contains("{providerName}"));
        assertFalse(prompt.contains("{modelName}"));
        assertFalse(prompt.contains("{storageBlock}"));
    }

    @Test
    void injectsMemorySection() {
        var builder = new SystemPromptBuilder();
        String memory = "# Persistent Agent Memory\n- [foo](foo.md) — 说明";
        String prompt = builder.build("deepseek", "deepseek-chat", memory, "", List.of(), null);
        assertTrue(prompt.contains(memory));
    }

    @Test
    void injectsStorageSection() {
        var builder = new SystemPromptBuilder();
        String storage = "- 日志目录: `C:\\Users\\u\\.agent-demo\\logs`";
        String prompt = builder.build("deepseek", "deepseek-chat", "", storage, List.of(), null);
        assertTrue(prompt.contains("Runtime Storage / 运行时存储"));
        assertTrue(prompt.contains(storage));
    }

    @Test
    void omitsEmptySections() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("deepseek", "deepseek-chat", "", "", List.of(), null);
        // memory / storage / extra 为空时不应出现空段落标题
        assertFalse(prompt.contains("Memory / 长期记忆"));
        assertFalse(prompt.contains("Runtime Storage / 运行时存储"));
        assertFalse(prompt.contains("Extra Guidelines / 附加指引"));
    }

    @Test
    void injectsExtraGuidelines() {
        var builder = new SystemPromptBuilder();
        String prompt =
                builder.build("deepseek", "deepseek-chat", "", "", List.of("指引A", "指引B"), null);
        assertTrue(prompt.contains("指引A"));
        assertTrue(prompt.contains("指引B"));
    }

    @Test
    void userOverrideWins() {
        var builder = new SystemPromptBuilder();
        String prompt =
                builder.build(
                        "deepseek", "deepseek-chat", "# MEMORY", "", List.of("附加"), "自定义提示词");
        assertEquals("自定义提示词", prompt);
    }

    @Test
    void blankUserOverrideFallsBackToTemplate() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("deepseek", "deepseek-chat", "", "", List.of(), "   ");
        assertTrue(prompt.contains("agent-demo"));
    }

    // ---- fix-memory-recall-wiring T2: buildBase 排除 memory 段（供每轮动态拼装复用） ----

    /** buildBase 产物不含记忆段——记忆段改由每轮按 query 动态生成。 */
    @Test
    void buildBaseExcludesMemorySection() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.buildBase("deepseek", "deepseek-chat", "", List.of(), null);
        assertFalse(prompt.contains("Persistent Agent Memory"));
        assertTrue(prompt.contains("agent-demo"));
    }

    /** buildBase 仍正常替换 provider / model 占位符。 */
    @Test
    void buildBaseSubstitutesProviderAndModel() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.buildBase("minimax", "MiniMax-Text-01", "", List.of(), null);
        assertTrue(prompt.contains("minimax"));
        assertTrue(prompt.contains("MiniMax-Text-01"));
        assertFalse(prompt.contains("{providerName}"));
        assertFalse(prompt.contains("{modelName}"));
        assertFalse(prompt.contains("{memoryBlock}"));
    }

    /** buildBase 保留存储段与附加指引段（它们与查询无关，不需每轮重算）。 */
    @Test
    void buildBaseKeepsStorageAndExtraGuidelines() {
        var builder = new SystemPromptBuilder();
        String storage = "- 日志目录: /tmp/logs";
        String prompt =
                builder.buildBase("deepseek", "deepseek-chat", storage, List.of("指引A", "指引B"), null);
        assertTrue(prompt.contains("Runtime Storage / 运行时存储"));
        assertTrue(prompt.contains(storage));
        assertTrue(prompt.contains("指引A"));
        assertTrue(prompt.contains("指引B"));
    }

    /** buildBase 同样尊重用户覆盖（--system-prompt）。 */
    @Test
    void buildBaseUserOverrideWins() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.buildBase("deepseek", "deepseek-chat", "", List.of("附加"), "自定义提示词");
        assertEquals("自定义提示词", prompt);
    }
}

