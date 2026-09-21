package com.example.agent.web.session;

import com.example.agent.session.SessionStore;
import com.example.agent.session.WorkspaceStore;
import com.example.agent.web.stream.WebAgentRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sandbox mode 启动重放服务（rewrite-permission-mode-dsh T8.4；Q4 决策）。
 *
 * <p>进程崩溃时 escalate 状态可能未恢复（turn 未正常结束 → 未触发
 * {@code turn_end_restore}）。本服务在应用启动后扫描每个 workspace 的
 * {@code sessions/*.jsonl}，找出「有 {@code escalate} 事件但后续无 {@code turn_end_restore}」
 * 的会话，记录 WARN 日志供审计。
 *
 * <p>注意：进程重启后原 AgentLoop / SandboxPolicyService 实例已销毁；新会话从
 * {@code settings.yaml} 的 {@code general.permission.mode} 读默认值。因此本服务**不修改运行时
 * 状态**，只做崩溃残留的可见化（spec §"Open Questions" Q4）。
 */
@Component
@Profile("web")
public class SandboxPolicyReplayService {

    private static final Logger log = LoggerFactory.getLogger(SandboxPolicyReplayService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebAgentRuntime runtime;

    public SandboxPolicyReplayService(WebAgentRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 启动钩子：扫描所有 workspace 的会话存档，报告未恢复的 escalate。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        ReplayReport report = replayAll(runtime.agentDataDir());
        if (report.unrecoveredCount() > 0) {
            log.warn("sandbox/mode 重放: 扫描 {} 个会话, {} 个存在未恢复的 escalate: {}",
                    report.scannedCount(), report.unrecoveredCount(), report.unrecoveredSessions());
        } else {
            log.info("sandbox/mode 重放: 扫描 {} 个会话, 无未恢复的 escalate", report.scannedCount());
        }
    }

    /**
     * 扫描报告。
     *
     * @param scannedCount        扫描的会话数
     * @param unrecoveredSessions 存在未恢复 escalate 的会话 id 列表
     */
    public record ReplayReport(int scannedCount, List<String> unrecoveredSessions) {
        /** 未恢复 escalate 的会话数 */
        public int unrecoveredCount() {
            return unrecoveredSessions.size();
        }
    }

    /**
     * 扫描指定数据目录下所有 workspace 的 conversations（可测试入口）。
     *
     * @param agentDataDir agent 数据目录（{@code ~/.agent-demo}）
     * @return 扫描报告
     */
    public ReplayReport replayAll(Path agentDataDir) {
        List<String> unrecovered = new ArrayList<>();
        int scanned = 0;
        if (agentDataDir == null || !Files.isDirectory(agentDataDir)) {
            return new ReplayReport(0, List.of());
        }
        for (WorkspaceStore.Workspace ws : WorkspaceStore.list(agentDataDir)) {
            Path sessionsDir = WorkspaceStore.sessionsDirFor(agentDataDir, ws.name());
            for (String sessionId : SessionStore.listSessions(sessionsDir)) {
                scanned++;
                Path jsonl = sessionsDir.resolve(sessionId + ".jsonl");
                if (findUnrecoveredEscalate(jsonl).isPresent()) {
                    unrecovered.add(sessionId);
                }
            }
        }
        return new ReplayReport(scanned, List.copyOf(unrecovered));
    }

    /**
     * 读取一个 session.jsonl，判断是否存在未恢复的 escalate。
     *
     * <p>判定：按顺序扫 {@code sandbox/mode} 事件，末尾若为 {@code escalate}（且不是被后续
     * {@code turn_end_restore} 抵消）→ 未恢复。
     *
     * @param jsonl session.jsonl 路径
     * @return 未恢复的 escalate 的 {@code to_mode}；无则 empty
     */
    public static Optional<String> findUnrecoveredEscalate(Path jsonl) {
        if (jsonl == null || !Files.isRegularFile(jsonl)) return Optional.empty();
        String pendingEscalate = null;
        try {
            for (String line : Files.readAllLines(jsonl)) {
                if (line == null || line.isBlank()) continue;
                JsonNode node;
                try {
                    node = JSON.readTree(line);
                } catch (IOException ignore) {
                    continue;  // 单行解析失败跳过（append-only 文件可能被并发写入截断）
                }
                if (!"sandbox/mode".equals(node.path("type").asText())) continue;
                String reason = node.path("reason").asText("");
                switch (reason) {
                    case "escalate" -> pendingEscalate = node.path("to_mode").asText("");
                    case "turn_end_restore" -> pendingEscalate = null;
                    default -> { /* initial / user_set 不改变 escalate 配对状态 */ }
                }
            }
        } catch (IOException e) {
            log.warn("读取 session.jsonl 失败: {}", jsonl, e);
            return Optional.empty();
        }
        return Optional.ofNullable(pendingEscalate);
    }
}
