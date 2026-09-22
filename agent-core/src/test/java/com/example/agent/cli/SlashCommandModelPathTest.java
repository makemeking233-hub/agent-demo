package com.example.agent.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.agent.core.MessageHistory;
import com.example.agent.llm.TokenEstimator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SlashCommand `/model` provider/model 路径（add-provider-catalog-abstract task 12.4）。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>`/model &lt;provider&gt;/&lt;model&gt;` 完整路径（校验 provider 白名单）
 *   <li>`/model chat` / `/model reasoning` v0.1 别名向后兼容
 *   <li>`/model &lt;model&gt;` 简写按前缀推断 provider
 *   <li>未知 provider / 未知 model 拒绝（不调回调）
 *   <li>onSelection 优先于 onModel
 * </ul>
 */
class SlashCommandModelPathTest {

    /** 调用 dispatch 并捕获 onSelection 的 (provider, model)。 */
    private static List<String> dispatchWithSelection(SlashCommand cmd, String input) {
        List<String> captured = new ArrayList<>();
        cmd.setOnSelection((provider, model) -> {
            captured.add(provider);
            captured.add(model);
        });
        cmd.dispatch(
                input,
                new MessageHistory(new TokenEstimator()),
                new int[] {0},
                new int[] {0},
                "deepseek-chat",
                () -> {},
                null,
                null,
                null);
        return captured;
    }

    private static String dispatchWithSingleModel(SlashCommand cmd, String input) {
        java.util.concurrent.atomic.AtomicReference<String> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        cmd.dispatch(
                input,
                new MessageHistory(new TokenEstimator()),
                new int[] {0},
                new int[] {0},
                "deepseek-chat",
                () -> {},
                null,
                null,
                captured::set);
        return captured.get();
    }

    @Test
    void fullProviderModelPathSwitchesBoth() {
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model openai/gpt-4o");
        assertEquals(List.of("openai", "gpt-4o"), got);
    }

    @Test
    void fullPathSupportsAnthropic() {
        List<String> got =
                dispatchWithSelection(new SlashCommand(), "/model anthropic/claude-opus-4-20250514");
        assertEquals(List.of("anthropic", "claude-opus-4-20250514"), got);
    }

    @Test
    void fullPathProviderIsCaseInsensitive() {
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model DeepSeek/deepseek-reasoner");
        assertEquals(List.of("deepseek", "deepseek-reasoner"), got);
    }

    @Test
    void unknownProviderIsRejected() {
        assertNull(dispatchWithSingleModel(new SlashCommand(), "/model bogus/gpt-4o"));
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model bogus/gpt-4o");
        assertEquals(List.of(), got);
    }

    @Test
    void emptyModelAfterSlashIsRejected() {
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model openai/");
        assertEquals(List.of(), got);
    }

    @Test
    void aliasChatMapsToDeepSeekChat() {
        // v0.1 向后兼容：/model chat → deepseek/deepseek-chat
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model chat");
        assertEquals(List.of("deepseek", "deepseek-chat"), got);
    }

    @Test
    void aliasReasoningMapsToDeepSeekReasoner() {
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model reasoning");
        assertEquals(List.of("deepseek", "deepseek-reasoner"), got);
    }

    @Test
    void aliasIsCaseInsensitive() {
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model REASONING");
        assertEquals(List.of("deepseek", "deepseek-reasoner"), got);
    }

    @Test
    void shorthandInfersProviderFromPrefix() {
        // /model gpt-4o → 前缀推断 openai
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model gpt-4o");
        assertEquals(List.of("openai", "gpt-4o"), got);
    }

    @Test
    void shorthandDeepSeekModelInfersDeepSeek() {
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model deepseek-reasoner");
        assertEquals(List.of("deepseek", "deepseek-reasoner"), got);
    }

    @Test
    void shorthandUnknownModelIsRejected() {
        // gemini-99 前缀无法推断（非 o1/o3/o4/gpt- /claude- /deepseek-），且不在白名单 → 拒绝
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model gemini-99");
        assertEquals(List.of(), got);
    }

    @Test
    void shorthandGptPrefixIsAcceptedViaInference() {
        // 注：gpt-99 属于 gpt- 前缀 → 推断为 openai，**会被接受**（task 12.1 前缀推断的预期行为；
        // v0.1 版本会拒绝，agent-core SlashCommandTest 的旧断言已随之调整为 gemini-99）
        List<String> got = dispatchWithSelection(new SlashCommand(), "/model gpt-99");
        assertEquals(List.of("openai", "gpt-99"), got);
    }

    @Test
    void defaultProviderIsReservedForUnrecognizableWhitelistedModel() {
        // 当前 SUPPORTED_MODELS 全是 deepseek- 前缀 → defaultProvider 实际不会被走到。
        // 本测试固定该事实（避免后人误以为 defaultProvider 已生效）；将来白名单加入
        // 无固定前缀的 model（如 minimax 的 abab6.5s-chat）时，改这里为期望 minimax。
        SlashCommand cmd = new SlashCommand();
        cmd.setDefaultProvider("minimax");
        List<String> got = new ArrayList<>();
        cmd.setOnSelection((provider, model) -> {
            got.add(provider);
            got.add(model);
        });
        cmd.dispatch(
                "/model deepseek-chat",
                new MessageHistory(new TokenEstimator()),
                new int[] {0},
                new int[] {0},
                "x",
                () -> {},
                null,
                null,
                null);
        assertEquals(List.of("deepseek", "deepseek-chat"), got, "deepseek-chat 前缀可推断，不走 default");
    }

    @Test
    void onSelectionTakesPrecedenceOverOnModel() {
        SlashCommand cmd = new SlashCommand();
        List<String> selectionCalls = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<String> onModelCalls =
                new java.util.concurrent.atomic.AtomicReference<>();
        cmd.setOnSelection((provider, model) -> selectionCalls.add(provider + "/" + model));
        cmd.dispatch(
                "/model openai/gpt-4o",
                new MessageHistory(new TokenEstimator()),
                new int[] {0},
                new int[] {0},
                "x",
                () -> {},
                null,
                null,
                onModelCalls::set);
        assertEquals(List.of("openai/gpt-4o"), selectionCalls);
        assertNull(onModelCalls.get(), "onSelection 注入后不应再调 onModel");
    }

    @Test
    void withoutSelectionFallsBackToOnModel() {
        // 未注入 onSelection 时仍走 onModel（向后兼容 v0.1）
        assertEquals(
                "deepseek-reasoner",
                dispatchWithSingleModel(new SlashCommand(), "/model deepseek-reasoner"));
    }
}
