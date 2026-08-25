package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Shell 命令执行（含黑名单 + 超时 + 输出上限 + 环境清理 + 进程树回收）。
 *
 * <p>详见 design.md §6.6 进程沙箱：
 * <ul>
 *   <li>单次超时：默认 120s</li>
 *   <li>输出上限：默认 1MB（stdout + stderr 累计）</li>
 *   <li>env 清理：剥离 {@code *KEY*} / {@code *TOKEN*} / {@code *SECRET*} / {@code *PASSWORD*} 等敏感变量</li>
 *   <li>进程树回收：Unix {@code ProcessHandle.descendants()}；Windows {@code taskkill /T /F}</li>
 *   <li>无持久 shell：每次调用独立进程</li>
 * </ul>
 */
public class ShellTool implements Tool<ShellTool.Input, String> {
    private static final Logger log = LoggerFactory.getLogger(ShellTool.class);

    public record Input(String command) {}

    private final ShellAdapter adapter;
    private final int timeoutSec;
    private final int maxOutputBytes;
    private final boolean killProcessTree;

    public ShellTool(ShellAdapter adapter, int timeoutSec, int maxOutputBytes, boolean killProcessTree) {
        this.adapter = adapter;
        this.timeoutSec = timeoutSec;
        this.maxOutputBytes = maxOutputBytes;
        this.killProcessTree = killProcessTree;
    }

    @Override public String name() { return "Shell"; }
    @Override public String description() { return "执行 shell 命令（跨平台，含黑名单与超时）"; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("command", Map.of("type", "string")),
            "required", List.of("command"));
    }
    @Override public boolean isDestructive(Input i) { return true; }

    @Override
    public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
        return PermissionDecision.ask();   // AgentLoop 会基于黑名单再次二次确认
    }

    @Override public String renderUse(Input i) { return "Shell(" + i.command() + ")"; }
    @Override public String renderResult(String s) { return s; }

    @Override
    public Mono<ToolResult<String>> execute(Input input, ToolContext ctx) {
        return Mono.fromCallable(() -> {
            if (adapter.isDenylisted(input.command())) {
                return ToolResult.<String>error("命令命中黑名单，拒绝执行: " + input.command());
            }
            List<String> argv = adapter.commandLine(input.command());
            ProcessBuilder pb = new ProcessBuilder(argv)
                .redirectErrorStream(true)
                .directory(ctx.workingDirectory().toFile());
            sanitizeEnv(pb.environment());
            Process proc;
            try {
                proc = pb.start();
            } catch (IOException e) {
                return ToolResult.<String>error("启动进程失败: " + e.getMessage());
            }
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<String> output = pool.submit(() -> readBounded(proc.getInputStream()));
            try {
                if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                    killTree(proc);
                    String partial = safeGet(output);
                    return ToolResult.<String>error("[TIMEOUT after " + timeoutSec + "s] " + partial);
                }
                String result = output.get(2, TimeUnit.SECONDS);
                pool.shutdownNow();
                return ToolResult.ok(result, "<auto>");
            } catch (Exception e) {
                killTree(proc);
                return ToolResult.<String>error("执行失败: " + e.getMessage());
            } finally {
                pool.shutdownNow();
            }
        });
    }

    private String readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) > 0) {
            if (total + n > maxOutputBytes) {
                int allowed = Math.max(0, maxOutputBytes - total);
                if (allowed > 0) buf.write(chunk, 0, allowed);
                buf.write(("\n[truncated: output exceeded " + maxOutputBytes + " bytes]").getBytes());
                break;
            }
            buf.write(chunk, 0, n);
            total += n;
        }
        return buf.toString();
    }

    /** 剥离敏感环境变量（详见 design.md §6.6） */
    private void sanitizeEnv(Map<String, String> env) {
        env.keySet().removeIf(k -> {
            String up = k.toUpperCase();
            return up.contains("API_KEY") || up.contains("TOKEN") || up.contains("SECRET")
                || up.contains("PASSWORD") || up.contains("PRIVATE_KEY");
        });
    }

    private String safeGet(Future<String> f) {
        try { return f.get(2, TimeUnit.SECONDS); } catch (Exception e) { return ""; }
    }

    private void killTree(Process proc) {
        if (!killProcessTree) { proc.destroy(); return; }
        proc.descendants().forEach(ProcessHandle::destroy);
        proc.destroy();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/T", "/F", "/PID", String.valueOf(proc.pid())).start();
            } catch (IOException ignored) {}
        }
    }
}