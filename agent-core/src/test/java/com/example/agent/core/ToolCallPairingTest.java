package com.example.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.llm.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ToolCallPairing}（repair-dangling-tool-calls）。
 *
 * <p>DeepSeek 要求 assistant 的每个 {@code tool_calls[].id} 都必须紧跟对应的 tool 消息，否则 400：
 * {@code An assistant message with 'tool_calls' must be followed by tool messages ...}。
 * 本类验证修复函数把「有 tool_calls 无 tool_result」的悬挂状态补齐。
 */
class ToolCallPairingTest {

    private static ToolCall call(String id) {
        return new ToolCall(id, "ReadFile", "{}");
    }

    private static Message.Assistant assistant(String... callIds) {
        return new Message.Assistant(
                "", java.util.Arrays.stream(callIds).map(ToolCallPairingTest::call).toList());
    }

    @Test
    void injectsErrorResultWhenAssistantHasNoFollowingResult() {
        // 真实事故形状：assistant(EditFile) 之后直接是 user（tool_result 缺失）
        List<Message> repaired =
                ToolCallPairing.repair(
                        List.of(assistant("c1"), new Message.User("怎么还是没有流式输出")));

        assertThat(repaired).hasSize(3);
        assertThat(repaired.get(0)).isInstanceOf(Message.Assistant.class);
        Message.ToolResult injected = (Message.ToolResult) repaired.get(1);
        assertThat(injected.toolCallId()).isEqualTo("c1");
        assertThat(injected.isError()).isTrue();
        assertThat(injected.content()).isNotBlank();
        assertThat(repaired.get(2)).isInstanceOf(Message.User.class);
    }

    @Test
    void injectsOnlyMissingResultsKeepingCallOrder() {
        List<Message> repaired =
                ToolCallPairing.repair(
                        List.of(
                                assistant("c1", "c2"),
                                new Message.ToolResult("c1", "ok", false)));

        assertThat(repaired).hasSize(3);
        assertThat(((Message.ToolResult) repaired.get(1)).toolCallId()).isEqualTo("c1");
        assertThat(((Message.ToolResult) repaired.get(1)).isError()).isFalse();
        assertThat(((Message.ToolResult) repaired.get(2)).toolCallId()).isEqualTo("c2");
        assertThat(((Message.ToolResult) repaired.get(2)).isError()).isTrue();
    }

    @Test
    void leavesSatisfiedPairsUntouched() {
        List<Message> input =
                List.of(assistant("c1"), new Message.ToolResult("c1", "ok", false));

        assertThat(ToolCallPairing.repair(input)).isEqualTo(input);
    }

    @Test
    void isIdempotent() {
        List<Message> once =
                ToolCallPairing.repair(List.of(assistant("c1"), new Message.User("hi")));

        assertThat(ToolCallPairing.repair(once)).isEqualTo(once);
    }

    @Test
    void ignoresAssistantWithoutToolCalls() {
        List<Message> input =
                List.of(new Message.Assistant("纯文本回复", List.of()), new Message.User("hi"));

        assertThat(ToolCallPairing.repair(input)).isEqualTo(input);
    }

    @Test
    void insertsBeforeTheNextNonToolMessage() {
        // 插到历史末尾是错的：中间的下一条消息会打断配对，依然 400
        List<Message> repaired =
                ToolCallPairing.repair(List.of(assistant("c1"), new Message.Assistant("下一轮", List.of())));

        assertThat(repaired).hasSize(3);
        assertThat(repaired.get(1)).isInstanceOf(Message.ToolResult.class);
        assertThat(repaired.get(2)).isInstanceOf(Message.Assistant.class);
    }

    @Test
    void doesNotMutateInputList() {
        List<Message> input = new java.util.ArrayList<>(List.of(assistant("c1")));

        ToolCallPairing.repair(input);

        assertThat(input).hasSize(1);
    }

    @Test
    void handlesMultipleDanglingAssistants() {
        List<Message> repaired =
                ToolCallPairing.repair(
                        List.of(
                                assistant("c1"),
                                new Message.User("u1"),
                                assistant("c2"),
                                new Message.User("u2")));

        assertThat(repaired).hasSize(6);
        assertThat(((Message.ToolResult) repaired.get(1)).toolCallId()).isEqualTo("c1");
        assertThat(((Message.ToolResult) repaired.get(4)).toolCallId()).isEqualTo("c2");
    }
}
