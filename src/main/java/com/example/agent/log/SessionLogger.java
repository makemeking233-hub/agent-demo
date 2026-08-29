package com.example.agent.log;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.tools.ToolResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话结构化日志实现（详见 logging-design.md §3-§4）。
 *
 * <p>为每个会话创建独立目录，产出四类日志文件：
 *
 * <ul>
 *   <li>{@code session.jsonl} - 事件流真相源（header + 带 seq 的事件）
 *   <li>{@code chat.log} - 每轮人类可读对话
 *   <li>{@code thinking.log} - 思考过程
 *   <li>{@code tools.log} - 工具调用
 * </ul>
 *
 * <p>写入策略：所有 writer 用 {@link BufferedWriter}，关键事件后 flush（读到的文件可实时反映）；写失败仅
 * {@code log.warn} 并继续，绝不让日志故障打断对话。
 */
public class SessionLogger implements SessionLogSink, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SessionLogger.class);

    /** JSON 序列化器（事件流用） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** chat.log 时间戳格式 */
    private static final DateTimeFormatter CHAT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** tools.log 时间戳格式 */
    private static final DateTimeFormatter TOOL_TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 会话事件流文件名 */
    static final String SESSION_LOG = "session.jsonl";
    /** 每轮聊天日志文件名 */
    static final String CHAT_LOG = "chat.log";
    /** 思考过程日志文件名 */
    static final String THINK_LOG = "thinking.log";
    /** 工具调用日志文件名 */
    static final String TOOL_LOG = "tools.log";

    private final Path sessionDir;
    private final int resultMaxChars;

    private final BufferedWriter sessionWriter;
    private final BufferedWriter chatWriter;
    private final BufferedWriter thinkWriter;
    private final BufferedWriter toolWriter;

    private final AtomicLong seq = new AtomicLong(0);
    private final AtomicInteger turn = new AtomicInteger(-1);
    private volatile boolean closed = false;

    /**
     * 构造会话日志器：创建会话目录、权限，打开四个 writer，写入 header。
     *
     * @param logging 日志配置
     * @param sessionId 会话 ID（同时作为目录名）
     * @throws IOException 目录创建或文件打开失败
     */
    public SessionLogger(AgentConfig.Logging logging, String sessionId) throws IOException {
        this.sessionDir = Path.of(logging.dir()).resolve("sessions").resolve(sessionId);
        this.resultMaxChars = logging.resultMaxChars();
        Files.createDirectories(sessionDir);
        try {
            Files.setPosixFilePermissions(
                    sessionDir,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Windows 跳过
        }
        sessionWriter = writer(SESSION_LOG);
        chatWriter = writer(CHAT_LOG);
        thinkWriter = writer(THINK_LOG);
        toolWriter = writer(TOOL_LOG);
        writeHeader();
    }

    /** 以 append 模式打开并设置 0600 权限的 writer */
    private BufferedWriter writer(String name) throws IOException {
        Path p = sessionDir.resolve(name);
        BufferedWriter w =
                Files.newBufferedWriter(
                        p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        try {
            Files.setPosixFilePermissions(
                    p, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows 跳过
        }
        return w;
    }

    private void writeHeader() throws IOException {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("type", "session");
        h.put("version", 1);
        h.put("id", sessionDir.getFileName().toString());
        h.put("createdAt", System.currentTimeMillis());
        h.put("cwd", Path.of(System.getProperty("user.dir")).toString());
        writeSessionLine(h);
        sessionWriter.flush();
    }

    // 事件流：写一行 JSON（不换行由 write 自行处理）
    private void writeSessionLine(Map<String, Object> data) throws IOException {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("seq", seq.getAndIncrement());
        Object ts = data.get("timestamp");
        line.put("timestamp", ts != null ? ts : System.currentTimeMillis());
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!e.getKey().equals("timestamp") && !e.getKey().equals("seq")) line.put(e.getKey(), e.getValue());
        }
        sessionWriter.write(JSON.writeValueAsString(line));
        sessionWriter.newLine();
        sessionWriter.flush();
    }

    // ---------- SessionLogSink ----------

    @Override
    public void onTurnStart(int turn) {
        this.turn.set(turn);
        safe(() -> writeSessionLine(Map.of("type", "turn/start", "turn", turn)));
    }

    @Override
    public void onUser(Message.User user) {
        safe(() -> {
            writeSessionLine(Map.of("type", "user/message", "role", "user", "content", user.content()));
            chatWriter.write("──[" + CHAT_TS.format(LocalDateTime.now()) + "] 用户 ──\n");
            chatWriter.write(user.content());
            chatWriter.newLine();
            chatWriter.newLine();
            chatWriter.flush();
        });
    }

    @Override
    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
        safe(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", "assistant/message");
            data.put("role", "assistant");
            data.put("content", assistant.content());
            if (assistant.toolCalls() != null && !assistant.toolCalls().isEmpty()) {
                data.put("toolCalls", assistant.toolCalls());
            }
            writeSessionLine(data);
            // chat.log 只写正文
            if (assistant.content() != null && !assistant.content().isBlank()) {
                chatWriter.write("──[" + CHAT_TS.format(LocalDateTime.now()) + "] 助手 ──\n");
                chatWriter.write(assistant.content());
                chatWriter.newLine();
                chatWriter.newLine();
                chatWriter.flush();
            }
            // thinking.log
            if (thinking != null && !thinking.isEmpty()) {
                for (String t : thinking) {
                    thinkWriter.write("[" + CHAT_TS.format(LocalDateTime.now()) + "] thinking> " + t + "\n");
                }
                thinkWriter.flush();
            }
        });
    }

    @Override
    public void onToolCall(ToolCall call) {
        safe(() -> {
            writeSessionLine(
                    Map.of(
                            "type", "tool/call",
                            "callId", call.id(),
                            "name", call.name(),
                            "arguments", truncate(call.argumentsJson())));
            toolWriter.write("[" + TOOL_TS.format(LocalTime.now()) + "] TOOL> " + call.name() + "   callId=" + call.id() + "\n");
            toolWriter.write("  args: " + truncate(call.argumentsJson()) + "\n");
            toolWriter.flush();
        });
    }

    @Override
    public void onToolResult(ToolResult<?> result, long elapsedMs) {
        safe(() -> {
            String content = truncate(result.toModelContent());
            writeSessionLine(
                    Map.of(
                            "type", "tool/result",
                            "callId", String.valueOf(result.toolCallId()),
                            "name", "",
                            "isError", result.isError(),
                            "result", content,
                            "elapsedMs", elapsedMs));
            if (result.isError()) {
                toolWriter.write("[" + TOOL_TS.format(LocalTime.now()) + "] TOOL< ERROR in " + elapsedMs + "ms: " + content + "\n");
            } else {
                toolWriter.write("[" + TOOL_TS.format(LocalTime.now()) + "] TOOL< done in " + elapsedMs + "ms, " + content.length() + " chars\n");
                // 设计 §3.5/§6: tools.log 与 session.jsonl 都写截断后的结果（模型看到的同一份内容）
                toolWriter.write("  result: " + content + "\n");
            }
            toolWriter.flush();
        });
    }

    @Override
    public void onTurnEnd(TurnResult result) {
        safe(() -> {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt", result.totalPromptTokens());
            usage.put("completion", result.totalCompletionTokens());
            writeSessionLine(Map.of("type", "turn/end", "turn", Math.max(turn.get(), 0), "usage", usage));
        });
    }

    // ---------- 辅助 ----------

    /** 结果截断（超上限时保留前 N 字符并加截断标记） */
    private String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= resultMaxChars) return s;
        return s.substring(0, resultMaxChars) + "\n[... truncated: " + (s.length() - resultMaxChars) + " chars omitted ...]";
    }

    /** 统一异常包装：日志故障只 warn，不向上抛 */
    private void safe(IoRunnable r) {
        if (closed) return;
        try {
            r.run();
        } catch (Exception e) {
            log.warn("会话日志写入失败", e);
        }
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws IOException;
    }

    /** 立即 flush 全部 writer（/clear 等关键节点调用） */
    public synchronized void flush() {
        safe(() -> {
            sessionWriter.flush();
            chatWriter.flush();
            thinkWriter.flush();
            toolWriter.flush();
        });
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        flush();
        sessionWriter.close();
        chatWriter.close();
        thinkWriter.close();
        toolWriter.close();
    }

    /** @return 会话日志目录（调试用） */
    public Path sessionDir() {
        return sessionDir;
    }
}
