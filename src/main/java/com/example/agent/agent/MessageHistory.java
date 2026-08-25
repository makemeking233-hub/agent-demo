package com.example.agent.agent;

import com.example.agent.provider.TokenEstimator;

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
 * <ul>
 *   <li>维护有序的 message 列表（追加顺序 = 发送给模型的顺序）</li>
 *   <li>累计 token 估算（用 {@link TokenEstimator}）</li>
 *   <li>维护连续压缩失败计数器（多 session 隔离；避免被一个 session 污染所有 session）</li>
 *   <li>缓存最近 ReadFileTool 读过的文件（Post-Compact 重注入用）</li>
 * </ul>
 */
public class MessageHistory {
    private final TokenEstimator estimator;
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, String> recentFileContents = new LinkedHashMap<>();
    private final AtomicInteger compactFailures = new AtomicInteger(0);

    public MessageHistory(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    public int size() { return messages.size(); }

    public List<Message> all() { return Collections.unmodifiableList(messages); }

    public Message last() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public void append(Message m) { messages.add(m); }

    public void appendToolResults(List<ToolResultEnvelope> results) {
        for (var r : results) {
            messages.add(new Message.ToolResult(r.toolCallId(), r.content(), r.isError()));
        }
    }

    public int estimateTokens() {
        return messages.stream().mapToInt(m -> estimator.estimate(m.content())).sum();
    }

    public int consecutiveCompactFailures() { return compactFailures.get(); }
    public void incrementCompactFailures() { compactFailures.incrementAndGet(); }
    public void resetCompactFailures() { compactFailures.set(0); }

    /**
     * 在 history 头部插入"前面的对话已被压缩为摘要"的 system 边界消息（M4 Post-Compact 用）
     */
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
                content = String.join("\n", Arrays.copyOfRange(lines, 0, maxLines)) + "\n[... truncated ...]";
            }
            sb.append("=== ").append(e.getKey()).append(" ===\n").append(content).append("\n");
        }
        messages.add(0, new Message.System(sb.toString()));
        recentFileContents.clear();
    }

    public record ToolResultEnvelope(String toolCallId, String content, boolean isError) {}
}