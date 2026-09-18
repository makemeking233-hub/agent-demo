package com.example.agent.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * ProviderInference 单测（add-provider-catalog-abstract task 6.2）。
 *
 * <p>按前缀规则覆盖：openai（o1/o3/o4/gpt-） / anthropic（claude-） / deepseek / 未知模型。
 */
class ProviderInferenceTest {

    @Test
    void infersOpenAiFromO1Prefix() {
        assertEquals("openai", ProviderInference.inferProvider("o1-preview"));
        assertEquals("openai", ProviderInference.inferProvider("o1-mini"));
    }

    @Test
    void infersOpenAiFromO3Prefix() {
        assertEquals("openai", ProviderInference.inferProvider("o3"));
    }

    @Test
    void infersOpenAiFromO4Prefix() {
        assertEquals("openai", ProviderInference.inferProvider("o4-mini"));
    }

    @Test
    void infersOpenAiFromGptPrefix() {
        assertEquals("openai", ProviderInference.inferProvider("gpt-4o"));
        assertEquals("openai", ProviderInference.inferProvider("gpt-4o-mini"));
        assertEquals("openai", ProviderInference.inferProvider("gpt-3.5-turbo"));
    }

    @Test
    void infersAnthropicFromClaudePrefix() {
        assertEquals("anthropic", ProviderInference.inferProvider("claude-opus-4-20250514"));
        assertEquals("anthropic", ProviderInference.inferProvider("claude-3-5-sonnet"));
    }

    @Test
    void infersDeepSeekFromDeepSeekPrefix() {
        assertEquals("deepseek", ProviderInference.inferProvider("deepseek-chat"));
        assertEquals("deepseek", ProviderInference.inferProvider("deepseek-reasoner"));
        assertEquals("deepseek", ProviderInference.inferProvider("deepseek-v4-pro"));
    }

    @Test
    void prefixMatchIsCaseInsensitive() {
        assertEquals("deepseek", ProviderInference.inferProvider("DeepSeek-Chat"));
        assertEquals("openai", ProviderInference.inferProvider("GPT-4o"));
        assertEquals("anthropic", ProviderInference.inferProvider("Claude-Opus-4"));
    }

    @Test
    void returnsNullForUnknownModel() {
        // MiniMax 模型名无固定前缀，无法推断
        assertNull(ProviderInference.inferProvider("abab6.5s-chat"));
        assertNull(ProviderInference.inferProvider("gemini-pro"));
    }

    @Test
    void returnsNullForNullOrEmpty() {
        assertNull(ProviderInference.inferProvider(null));
        assertNull(ProviderInference.inferProvider(""));
    }
}
