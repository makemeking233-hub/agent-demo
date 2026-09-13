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
 *       {@code SessionResumeLoader.injectOrphanSkeletons} 处理）。
 * </ul>
 *
 * <p>正向为什么会被破坏：assistant 消息按消息顺序约束**先入 history**，工具结果随后回流。若某一轮
 * 在两者之间被打断（例如 2026-09-13 的 {@code NoClassDefFoundError} 逃出响应式链），存档就停在
 * 「有 tool_calls、无 tool_result」的中间态；此后该会话每轮重放这段历史都会 400，等于会话永久不可用。
 *
 * <p>修复方式是**补一条合成错误结果**而不是删掉 assistant：既满足协议，又如实告诉模型「这次调用没
 * 完成」，它会自行换策略重试；删掉则会让模型丢失自己刚做的决定。
 *
 * <p>纯函数：不修改入参列表，返回新列表；对已配对的历史逐元素相等（幂等）。
 */
public final class ToolCallPairing {

    private static final Logger log = LoggerFactory.getLogger(ToolCallPairing.class);

    /** 合成错误结果的文案模板（参数为工具名）。刻意写成明确的「未完成」而非伪装成功。 */
    static final String MISSING_RESULT_TEMPLATE =
            "[历史修复] 工具 %s 的调用未完成（会话记录缺少结果），如仍需要请重新发起。";

    private ToolCallPairing() {}

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
}
