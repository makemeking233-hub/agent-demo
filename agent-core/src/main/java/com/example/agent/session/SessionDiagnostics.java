package com.example.agent.session;

import com.example.agent.core.Message;
import com.example.agent.core.ToolCallPairing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话记录完整性诊断（improve-failure-observability）。
 *
 * <p>把 2026-09-13 事故里"手写 PowerShell 扫 JSONL 才定位到悬挂 tool_calls"的过程固化成一条可调用
 * 的能力：扫描会话存档的配对不变式，报告哪些会话的记录不完整、缺哪些 {@code toolCallId}。
 *
 * <p>纯读取：不改写任何存档文件；用 {@link SessionResumeLoader#toMessagesRaw} 观察**修复前**的真实
 * 状态（走修复路径的话问题就被掩盖了）。
 */
public final class SessionDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(SessionDiagnostics.class);

    /**
     * 单个不完整会话。
     *
     * @param sessionId 会话 id
     * @param danglingToolCallIds 缺失结果的 {@code tool_callId}（按出现顺序）
     * @param sizeBytes 存档文件字节数（便于判断影响面）
     */
    public record IncompleteSession(String sessionId, List<String> danglingToolCallIds, long sizeBytes) {}

    /**
     * 诊断报告。
     *
     * @param sessionsDir 被扫描的会话目录
     * @param logsDir 运行时日志根目录（便于排查时直接定位）
     * @param scannedSessions 实际扫描的会话数
     * @param incomplete 记录不完整的会话；全部完整时为空列表
     */
    public record Report(
            String sessionsDir, String logsDir, int scannedSessions, List<IncompleteSession> incomplete) {}

    private SessionDiagnostics() {}

    /**
     * 扫描会话目录。
     *
     * @param sessionsDir 会话目录（{@code ~/.agent-demo/sessions}）
     * @param logsDir 运行时日志根（{@code ~/.agent-demo/logs}），仅用于回报
     * @return 诊断报告；目录不存在时返回空报告
     */
    public static Report scan(Path sessionsDir, Path logsDir) {
        List<IncompleteSession> incomplete = new ArrayList<>();
        int scanned = 0;
        if (sessionsDir != null && Files.isDirectory(sessionsDir)) {
            try (Stream<Path> files = Files.list(sessionsDir)) {
                List<Path> jsonls =
                        files.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList();
                for (Path f : jsonls) {
                    scanned++;
                    String sessionId =
                            f.getFileName().toString().replaceFirst("\\.jsonl$", "");
                    try {
                        List<SessionEntry> entries = SessionStore.loadFile(f);
                        List<Message> messages =
                                SessionResumeLoader.toMessagesRaw(entries).messages();
                        List<String> dangling = ToolCallPairing.danglingCallIds(messages);
                        if (!dangling.isEmpty()) {
                            incomplete.add(
                                    new IncompleteSession(
                                            sessionId, dangling, Files.size(f)));
                        }
                    } catch (Exception e) {
                        // 单个文件坏掉不影响整体诊断
                        log.warn("诊断跳过损坏会话文件 {}: {}", f, e.toString());
                    }
                }
            } catch (IOException e) {
                log.warn("诊断扫描会话目录失败 {}: {}", sessionsDir, e.toString());
            }
        }
        return new Report(
                sessionsDir == null ? null : sessionsDir.toString(),
                logsDir == null ? null : logsDir.toString(),
                scanned,
                incomplete);
    }
}
