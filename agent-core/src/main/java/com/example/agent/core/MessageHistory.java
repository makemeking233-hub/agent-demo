package com.example.agent.core;

import com.example.agent.llm.TokenEstimator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 主循环的对话历史容器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护有序的 message 列表（追加顺序 = 发送给模型的顺序）
 *   <li>累计 token 估算（用 {@link TokenEstimator}）
 *   <li>维护连续压缩失败计数器（多 session 隔离；避免被一个 session 污染所有 session）
 *   <li>缓存最近 ReadFileTool 读过的文件（Post-Compact 重注入用）
 * </ul>
 */
public class MessageHistory {
    /** Token 估算器（用于 {@link #estimateTokens()} 与压缩触发阈值） */
    private final TokenEstimator estimator;

    /** 有序消息列表（追加顺序 = 发送给模型的顺序） */
    private final List<Message> messages = new ArrayList<>();

    /** 最近 ReadFileTool 读过的文件内容（按插入顺序，Post-Compact 重注入用） */
    private final Map<String, String> recentFileContents = new LinkedHashMap<>();

    /** 连续压缩失败计数（达到 {@code maxConsecutiveCompactFailures} 触发熔断） */
    private final AtomicInteger compactFailures = new AtomicInteger(0);

    /**
     * 构造消息历史容器。
     *
     * @param estimator token 估算器（不可空）
     */
    public MessageHistory(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    /**
     * @return 当前消息总数（user / assistant / tool / system 全部计入）
     */
    public int size() {
        return messages.size();
    }

    /**
     * @return 不可变消息列表快照（外部修改不影响内部状态）
     */
    public List<Message> all() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * @return 最后一条消息；空历史时返回 {@code null}
     */
    public Message last() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * 追加单条消息到历史末尾。
     *
     * @param m 待追加消息
     */
    public void append(Message m) {
        messages.add(m);
    }

    /**
     * 把工具调用结果批量追加为 {@link Message.ToolResult}（回流给模型的格式）。
     *
     * @param results 工具结果信封列表
     */
    public void appendToolResults(List<ToolResultEnvelope> results) {
        for (var r : results) {
            messages.add(new Message.ToolResult(r.toolCallId(), r.content(), r.isError()));
        }
    }

    /**
     * @return 累计 token 估算（每条消息 content 的 token 数相加）
     */
    public int estimateTokens() {
        return messages.stream().mapToInt(m -> estimator.estimate(m.content())).sum();
    }

    /**
     * @return 当前连续压缩失败次数（自上次成功压缩以来）
     */
    public int consecutiveCompactFailures() {
        return compactFailures.get();
    }

    /** 增加连续压缩失败计数（一次失败时调用） */
    public void incrementCompactFailures() {
        compactFailures.incrementAndGet();
    }

    /** 重置连续压缩失败计数（压缩成功时调用） */
    public void resetCompactFailures() {
        compactFailures.set(0);
    }

    /**
     * 整体替换消息历史（v0.2 /resume 命令用）。
     *
     * <p>行为：
     *
     * <ul>
     *   <li>清空当前 {@link #messages} 列表
     *   <li>重置 {@link #compactFailures} 计数器（resume 后 compact 应从 0 开始计数）
     *   <li>不重置 {@link #recentFileContents}（Post-Compact 重注入仍可能复用旧文件缓存）
     * </ul>
     *
     * @param newMessages 新消息列表（{@code null} 视为空 list）
     */
    public void replaceAll(List<Message> newMessages) {
        this.messages.clear();
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        // reset compact counter：resume 后从干净状态开始计压缩失败
        this.compactFailures.set(0);
    }

    /** 在 history 头部插入"前面的对话已被压缩为摘要"的 system 边界消息（M4 Post-Compact 用） */
    public void prependSystemBoundaryMessage(String summary) {
        messages.add(0, new Message.System("[COMPACTED]\n" + summary));
    }

    /** ReadFileTool 成功读取后调用，记录文件内容供 Post-Compact 重注入 */
    public void rememberFileContent(String path, String content) {
        recentFileContents.put(path, content);
    }

    /** 把最近读过的文件内容（前 maxLines）作为 system 消息插入头部 */
    public void reinjectRecentFileContents(int maxLines) {
        if (recentFileContents.isEmpty()) return;
        StringBuilder sb = new StringBuilder("[RECENT FILES]\n");
        for (var e : recentFileContents.entrySet()) {
            String content = e.getValue();
            String[] lines = content.split("\n", -1);
            if (lines.length > maxLines) {
                content =
                        String.join("\n", Arrays.copyOfRange(lines, 0, maxLines))
                                + "\n[... truncated ...]";
            }
            sb.append("=== ").append(e.getKey()).append(" ===\n").append(content).append("\n");
        }
        messages.add(0, new Message.System(sb.toString()));
        recentFileContents.clear();
    }

    /**
     * 最近读过的文件路径（只读视图；可观测性 context/snapshot 用）。
     *
     * @return 最近 ReadFileTool 读过的文件路径列表（插入序）
     */
    public List<String> recentFilePaths() {
        return List.copyOf(recentFileContents.keySet());
    }

    /**
     * 工具调用结果传输信封（仅内部包内可见，避免污染公共 API）。
     *
     * @param toolCallId 关联的工具调用 ID（用于回流给模型时匹配 assistant.tool_calls）
     * @param content 工具输出内容（错误时为错误信息）
     * @param isError 是否为错误结果
     */
    public record ToolResultEnvelope(String toolCallId, String content, boolean isError) {}

    /**
     * @return 调试用字符串（含 size / estTokens / compactFailures / recentFiles）
     */
    @Override
    public String toString() {
        return "MessageHistory{size="
                + messages.size()
                + ", estTokens="
                + estimateTokens()
                + ", compactFailures="
                + compactFailures.get()
                + ", recentFiles="
                + recentFileContents.size()
                + "}";
    }
}
