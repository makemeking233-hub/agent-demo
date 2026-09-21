package com.example.agent.core;

import com.example.agent.llm.ToolCall;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息配对修复（repair-dangling-tool-calls）。
 *
 * <p>会话历史必须同时满足两个配对不变式：
 *
 * <ul>
 *   <li><b>正向</b>：assistant 的每个 {@code tool_calls[].id}，都要在其后、下一条非 tool 消息之前，
 *       有对应的 tool 结果。缺了它 DeepSeek 直接 400：{@code An assistant message with 'tool_calls'
 *       must be followed by tool messages responding to each 'tool_call_id'}。
 *   <li><b>反向</b>：任何 tool 结果都必须有前置的 {@code assistant.tool_calls} 包含它的 id（由
 *       {@link #repairOrphanResults} 处理）。缺了它 DeepSeek 同样 400：{@code Messages with role
 *       'tool' must be a response to a preceding message with 'tool_calls'}。
 * </ul>
 *
 * <p>正向为什么会被破坏：assistant 消息按消息顺序约束**先入 history**，工具结果随后回流。若某一轮
 * 在两者之间被打断（例如 2026-09-13 的 {@code NoClassDefFoundError} 逃出响应式链），存档就停在
 * 「有 tool_calls、无 tool_result」的中间态；此后该会话每轮重放这段历史都会 400，等于会话永久不可用。
 *
 * <p>反向为什么会被破坏：{@code SessionResumeLoader.snip} 为压 token 会从**头部**裁剪历史。裁剪以
 * 单条消息为单位时，裁剪点可能落在 {@code assistant(tool_calls)} 与其结果之间——assistant 被丢掉、
 * 结果被保留，于是产生孤儿 tool 消息（snip-pairing-repair 实测：204 条存档裁剪后反向孤儿 0 → 1）。
 *
 * <p>两个方向都**补合成消息**而不是删除：正向补错误结果、反向补 assistant 骨架。删掉等于把事实从
 * 历史里抹掉，模型会重复调用或丢失自己刚做的决定。
 *
 * <p>纯函数：不修改入参列表，返回新列表；对已配对的历史逐元素相等（幂等）。
 */
public final class ToolCallPairing {

    private static final Logger log = LoggerFactory.getLogger(ToolCallPairing.class);

    /** 合成错误结果的文案模板（参数为工具名）。刻意写成明确的「未完成」而非伪装成功。 */
    static final String MISSING_RESULT_TEMPLATE =
            "[历史修复] 工具 %s 的调用未完成（会话记录缺少结果），如仍需要请重新发起。";

    /** 反向修复所用合成 assistant 骨架的工具名（与历史实现保持一致）。 */
    static final String ORPHAN_CALL_NAME = "resumed_tool";

    private ToolCallPairing() {}

    /**
     * 列出正向下悬挂的 {@code tool_call_id}（有 {@code tool_calls} 但在下一条非 tool 消息之前没有
     * 对应结果），不修改入参。
     *
     * <p>供 {@link #repair} 与诊断入口（{@code SessionDiagnostics}）共用同一套判定，避免两处逻辑漂移。
     *
     * @param messages 待扫描的消息列表（可空）
     * @return 悬挂的 id 列表（按出现顺序）；无悬挂时为空
     */
    public static List<String> danglingCallIds(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        List<String> dangling = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (!(messages.get(i) instanceof Message.Assistant a)
                    || a.toolCalls() == null
                    || a.toolCalls().isEmpty()) {
                continue;
            }
            Set<String> answered = new HashSet<>();
            int j = i + 1;
            while (j < messages.size() && messages.get(j) instanceof Message.ToolResult tr) {
                answered.add(tr.toolCallId());
                j++;
            }
            for (ToolCall tc : a.toolCalls()) {
                if (!answered.contains(tc.id())) dangling.add(tc.id());
            }
            i = j - 1;
        }
        return dangling;
    }

    /**
     * 为正向下悬挂的 {@code tool_calls} 补齐合成错误结果。
     *
     * <p>补丁插入位置是「该 assistant 已有结果之后、下一条非 tool 消息之前」——插到历史末尾是错的，
     * 中间的下一条消息会打断紧邻关系，依然 400。
     *
     * @param messages 待检查的消息列表（可为空；{@code null} 视为空）
     * @return 修复后的新列表；无需修复时返回与入参相等的副本
     */
    public static List<Message> repair(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }

        List<Message> out = new ArrayList<>(messages);
        int injected = 0;
        for (int i = 0; i < out.size(); i++) {
            if (!(out.get(i) instanceof Message.Assistant a)
                    || a.toolCalls() == null
                    || a.toolCalls().isEmpty()) {
                continue;
            }
            // 紧随其后的连续 tool 结果覆盖到的 id
            Set<String> answered = new HashSet<>();
            int j = i + 1;
            while (j < out.size() && out.get(j) instanceof Message.ToolResult tr) {
                answered.add(tr.toolCallId());
                j++;
            }
            // 在 j 处按 tool_calls 顺序补齐缺失项
            int at = j;
            for (ToolCall tc : a.toolCalls()) {
                if (answered.contains(tc.id())) continue;
                out.add(
                        at++,
                        new Message.ToolResult(
                                tc.id(),
                                String.format(MISSING_RESULT_TEMPLATE, tc.name()),
                                true));
                injected++;
            }
            i = at - 1; // 跳过刚插入的结果，继续扫描后面的消息
        }

        if (injected > 0) {
            log.warn("历史修复：为 {} 个未配对的 tool_call 补了合成错误结果（会话存档此处不完整）", injected);
        }
        return out;
    }

    /**
     * 为反向孤儿补齐合成 assistant 骨架（snip-pairing-repair）。
     *
     * <p>反向不变式：任何 tool 结果都必须有前置的 {@code assistant.tool_calls} 包含它的 id。它被破坏
     * 的典型途径是 {@code SessionResumeLoader.snip} 从头部裁剪历史——裁剪点若落在
     * {@code assistant(tool_calls)} 与其结果之间，父 assistant 被丢掉而结果保留，剩下的 tool 消息
     * 就成了孤儿。
     *
     * <p>同一段连续孤儿结果共用**一条**骨架（还原「一次 assistant 的并行 {@code tool_calls}」语义），
     * 避免每个结果各插一条把历史切碎；遇到「已有前置调用」的结果即结束该孤儿段——那属于前一组。
     *
     * <p>纯函数：不修改入参列表，返回新列表；对已满足反向配对的历史逐元素相等（幂等）。
     *
     * @param messages 待检查的消息列表（可空；{@code null} 视为空）
     * @return 修复后的新列表；无需修复时返回与入参相等的副本
     */
    public static List<Message> repairOrphanResults(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }

        List<Message> out = new ArrayList<>(messages);
        int injected = 0;
        for (int i = 0; i < out.size(); i++) {
            if (!(out.get(i) instanceof Message.ToolResult)) continue;

            // 从 i 起收集连续、且缺前置 tool_calls 的 tool 结果
            List<ToolCall> orphans = new ArrayList<>();
            int j = i;
            while (j < out.size() && out.get(j) instanceof Message.ToolResult tr) {
                if (hasMatchingCall(out, i, tr.toolCallId())) break; // 有前置 → 属于前一组，孤儿段结束
                orphans.add(new ToolCall(tr.toolCallId(), ORPHAN_CALL_NAME, "{}"));
                j++;
            }
            if (orphans.isEmpty()) continue;

            out.add(i, new Message.Assistant("", List.copyOf(orphans)));
            injected += orphans.size();
            i = j; // 跳过刚插入的骨架与这一组结果
        }

        if (injected > 0) {
            log.warn("历史修复：为 {} 个孤儿 tool 结果补了合成 assistant 骨架（历史前缀被裁掉）", injected);
        }
        return out;
    }

    /** 在 {@code [0, upTo)} 内是否存在带 {@code callId} 的 {@code assistant.tool_calls}。 */
    private static boolean hasMatchingCall(List<Message> messages, int upTo, String callId) {
        for (int i = 0; i < upTo; i++) {
            if (messages.get(i) instanceof Message.Assistant a
                    && a.toolCalls() != null
                    && a.toolCalls().stream().anyMatch(tc -> tc.id().equals(callId))) {
                return true;
            }
        }
        return false;
    }
}
