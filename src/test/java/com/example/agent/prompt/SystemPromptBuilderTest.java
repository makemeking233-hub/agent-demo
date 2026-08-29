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
 * <p>覆盖：默认模板加载、provider/model 占位符注入、memory 段注入、附加指引注入、用户覆盖优先。
 */
class SystemPromptBuilderTest {

    @Test
    void loadsDefaultTemplate() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("deepseek", "deepseek-chat", "", List.of(), null);
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
        String prompt = builder.build("minimax", "MiniMax-Text-01", "", List.of(), null);
        assertTrue(prompt.contains("minimax"));
        assertTrue(prompt.contains("MiniMax-Text-01"));
        // 模板中不应残留未替换占位符
        assertFalse(prompt.contains("{providerName}"));
        assertFalse(prompt.contains("{modelName}"));
    }

    @Test
    void injectsMemorySection() {
        var builder = new SystemPromptBuilder();
        String memory = "# Persistent Agent Memory\n- [foo](foo.md) — 说明";
        String prompt = builder.build("deepseek", "deepseek-chat", memory, List.of(), null);
        assertTrue(prompt.contains(memory));
    }

    @Test
    void omitsEmptySections() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("deepseek", "deepseek-chat", "", List.of(), null);
        // memory / extra 为空时不应出现空段落标题
        assertFalse(prompt.contains("Memory / 长期记忆"));
        assertFalse(prompt.contains("Extra Guidelines / 附加指引"));
    }

    @Test
    void injectsExtraGuidelines() {
        var builder = new SystemPromptBuilder();
        String prompt =
                builder.build("deepseek", "deepseek-chat", "", List.of("指引A", "指引B"), null);
        assertTrue(prompt.contains("指引A"));
        assertTrue(prompt.contains("指引B"));
    }

    @Test
    void userOverrideWins() {
        var builder = new SystemPromptBuilder();
        String prompt =
                builder.build(
                        "deepseek", "deepseek-chat", "# MEMORY", List.of("附加"), "自定义提示词");
        assertEquals("自定义提示词", prompt);
    }

    @Test
    void blankUserOverrideFallsBackToTemplate() {
        var builder = new SystemPromptBuilder();
        String prompt = builder.build("deepseek", "deepseek-chat", "", List.of(), "   ");
        assertTrue(prompt.contains("agent-demo"));
    }
}
