/**
 * StreamChunk ThinkingDelta 单测（add-reasoning-thinking-streaming）。
 *
 * 验证：
 * 1. ThinkingDelta record 正常构造 + accept 派发
 * 2. StreamChunkVisitor 默认 visitThinkingDelta 是空实现（向后兼容老 visitor）
 * 3. 老 visitor 调 ThinkingDelta 不抛错
 */
package com.example.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.agent.llm.StreamChunk.Finished;
import com.example.agent.llm.StreamChunk.TextDelta;
import com.example.agent.llm.StreamChunk.ThinkingDelta;
import com.example.agent.llm.StreamChunk.Usage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamChunkThinkingDeltaTest {

    /** 老 Visitor（v0.1 实现）只覆盖 TextDelta，不覆盖 ThinkingDelta */
    static class LegacyVisitor implements StreamChunk.StreamChunkVisitor {
        final List<String> texts = new ArrayList<>();

        @Override
        public void visitTextDelta(TextDelta c) {
            texts.add(c.text());
        }
    }

    /** 新 Visitor（v0.2 完整实现）覆盖所有事件 */
    static class FullVisitor implements StreamChunk.StreamChunkVisitor {
        final List<String> texts = new ArrayList<>();
        final List<String> thinkings = new ArrayList<>();

        @Override
        public void visitTextDelta(TextDelta c) {
            texts.add(c.text());
        }

        @Override
        public void visitThinkingDelta(ThinkingDelta c) {
            thinkings.add(c.text());
        }
    }

    @Test
    void thinkingDeltaStoresText() {
        ThinkingDelta delta = new ThinkingDelta("用户问...");
        assertThat(delta.text()).isEqualTo("用户问...");
    }

    @Test
    void fullVisitorCollectsBothTextAndThinking() {
        FullVisitor v = new FullVisitor();
        new TextDelta("答案").accept(v);
        new ThinkingDelta("思考").accept(v);
        assertThat(v.texts).containsExactly("答案");
        assertThat(v.thinkings).containsExactly("思考");
    }

    @Test
    void legacyVisitorHandlesThinkingDeltaWithoutError() {
        LegacyVisitor v = new LegacyVisitor();
        // 老 visitor 不覆盖 visitThinkingDelta，应走默认空实现（不抛错）
        assertThatCode(() -> new ThinkingDelta("思考").accept(v)).doesNotThrowAnyException();
        // 不应记录到 texts（因为是 thinking 不是 text）
        assertThat(v.texts).isEmpty();
    }

    @Test
    void usageRecordAcceptsReasoningTokens() {
        Usage usage = new Usage(10, 20, 5);
        assertThat(usage.promptTokens()).isEqualTo(10);
        assertThat(usage.completionTokens()).isEqualTo(20);
        assertThat(usage.reasoningTokens()).isEqualTo(5);
    }

    @Test
    void finishedHoldsUsageWithReasoningTokens() {
        Finished finished = new Finished(FinishReason.STOP, new Usage(1, 1, 1));
        assertThat(finished.usage().reasoningTokens()).isEqualTo(1);
    }
}