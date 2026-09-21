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

    // ---------- 反向配对修复（snip-pairing-repair）----------

    private static Message.ToolResult result(String id) {
        return new Message.ToolResult(id, "结果-" + id, false);
    }

    @Test
    void injectsSkeletonForSingleOrphanResult() {
        // 重放历史被从头部裁掉父 assistant 后，tool 结果就成了无前置 tool_calls 的孤儿
        List<Message> out =
                ToolCallPairing.repairOrphanResults(
                        List.of(new Message.User("u"), result("c1")));

        assertThat(out).hasSize(3);
        assertThat(out.get(1)).isInstanceOf(Message.Assistant.class);
        Message.Assistant skeleton = (Message.Assistant) out.get(1);
        assertThat(skeleton.toolCalls()).hasSize(1);
        assertThat(skeleton.toolCalls().get(0).id()).isEqualTo("c1");
        assertThat(out.get(2)).isInstanceOf(Message.ToolResult.class);
    }

    @Test
    void mergesConsecutiveOrphanResultsIntoOneSkeleton() {
        // 一次 assistant 的并行 tool_calls 被整组裁掉 → 结果应共用一条合成骨架，
        // 而不是每个结果各插一条（后者会把历史切碎）
        List<Message> out =
                ToolCallPairing.repairOrphanResults(
                        List.of(new Message.User("u"), result("c1"), result("c2"), new Message.User("v")));

        assertThat(out).hasSize(5);
        Message.Assistant skeleton = (Message.Assistant) out.get(1);
        assertThat(skeleton.toolCalls()).extracting(ToolCall::id).containsExactly("c1", "c2");
        assertThat(out.get(2)).isEqualTo(result("c1"));
        assertThat(out.get(3)).isEqualTo(result("c2"));
        assertThat(out.get(4)).isInstanceOf(Message.User.class);
    }

    @Test
    void stopsOrphanGroupAtResultThatHasPrecedingCall() {
        // c1 有前置 assistant，不是孤儿；c2 没有 → 只给 c2 插骨架
        List<Message> out =
                ToolCallPairing.repairOrphanResults(
                        List.of(assistant("c1"), result("c1"), result("c2"), new Message.User("v")));

        assertThat(out).hasSize(5);
        assertThat(out.get(0)).isEqualTo(assistant("c1"));
        assertThat(out.get(1)).isEqualTo(result("c1"));
        Message.Assistant skeleton = (Message.Assistant) out.get(2);
        assertThat(skeleton.toolCalls()).extracting(ToolCall::id).containsExactly("c2");
        assertThat(out.get(3)).isEqualTo(result("c2"));
    }

    @Test
    void leavesCleanHistoryUntouchedOnOrphanRepair() {
        List<Message> input = List.of(assistant("c1"), result("c1"));

        assertThat(ToolCallPairing.repairOrphanResults(input)).isEqualTo(input);
    }

    @Test
    void orphanRepairIsIdempotent() {
        List<Message> once =
                ToolCallPairing.repairOrphanResults(List.of(new Message.User("u"), result("c1")));

        assertThat(ToolCallPairing.repairOrphanResults(once)).isEqualTo(once);
    }

    @Test
    void doesNotMutateInputListOnOrphanRepair() {
        List<Message> input = new java.util.ArrayList<>(List.of(new Message.User("u"), result("c1")));

        ToolCallPairing.repairOrphanResults(input);

        assertThat(input).hasSize(2);
    }

    @Test
    void combinedRepairSatisfiesBothDirections() {
        // 请求路径的组合拳：正向悬挂（assistant c9 无结果）+ 反向孤儿（c8 无前置）
        List<Message> out =
                ToolCallPairing.repairOrphanResults(
                        ToolCallPairing.repair(
                                List.of(result("c8"), assistant("c9"), new Message.User("u"))));

        assertThat(ToolCallPairing.danglingCallIds(out)).isEmpty();
        assertThat(out).anyMatch(m -> m instanceof Message.Assistant a
                && a.toolCalls() != null
                && a.toolCalls().stream().anyMatch(tc -> tc.id().equals("c8")));
    }
}
