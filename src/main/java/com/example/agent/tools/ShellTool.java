package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Shell 命令执行（含黑名单 + 超时 + 输出上限 + 环境清理 + 进程树回收）。
 *
 * <p>详见 design.md §6.6 进程沙箱：
 *
 * <ul>
 *   <li>单次超时：默认 120s
 *   <li>输出上限：默认 1MB（stdout + stderr 累计）
 *   <li>env 清理：剥离 {@code *KEY*} / {@code *TOKEN*} / {@code *SECRET*} / {@code *PASSWORD*} 等敏感变量
 *   <li>进程树回收：Unix {@code ProcessHandle.descendants()}；Windows {@code taskkill /T /F}
 *   <li>无持久 shell：每次调用独立进程
 * </ul>
 */
public class ShellTool implements Tool<ShellTool.Input, String> {
  private static final Logger log = LoggerFactory.getLogger(ShellTool.class);

  /** I/O 缓冲：每次 read 的字节数（规范 13.5 大文件读写需缓冲） */
  private static final int IO_BUFFER_BYTES = 4096;

  /** safeGet 等 future 的最长等待 */
  private static final int FUTURE_WAIT_SEC = 2;

  /** 单线程 pool 的核心线程数（执行 readBounded 的唯一线程） */
  private static final int POOL_CORE_SIZE = 1;

  /** pool 任务队列容量（避免无界 OOM，规范 10.1-2） */
  private static final int POOL_QUEUE_CAPACITY = 16;

  /** pool keep-alive 时间（核心线程不超时） */
  private static final long POOL_KEEP_ALIVE_SEC = 60L;

  public record Input(String command) {}

  private final ShellAdapter adapter;
  private final int timeoutSec;
  private final int maxOutputBytes;
  private final boolean killProcessTree;

  public ShellTool(
      ShellAdapter adapter, int timeoutSec, int maxOutputBytes, boolean killProcessTree) {
    this.adapter = adapter;
    this.timeoutSec = timeoutSec;
    this.maxOutputBytes = maxOutputBytes;
    this.killProcessTree = killProcessTree;
  }

  @Override
  public String name() {
    return "Shell";
  }

  @Override
  public String description() {
    return "执行 shell 命令（跨平台，含黑名单与超时）";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("command", Map.of("type", "string")),
        "required",
        List.of("command"));
  }

  @Override
  public boolean isDestructive(Input i) {
    return true;
  }

  @Override
  public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
    return PermissionDecision.ask(); // AgentLoop 会基于黑名单再次二次确认
  }

  @Override
  public String renderUse(Input i) {
    return "Shell(" + i.command() + ")";
  }

  @Override
  public String renderResult(String s) {
    return s;
  }

  @Override
  public Mono<ToolResult<String>> execute(Input input, ToolContext ctx) {
    return Mono.fromCallable(
        () -> {
          if (adapter.isDenylisted(input.command())) {
            return ToolResult.<String>error("命令命中黑名单，拒绝执行: " + input.command());
          }
          List<String> argv = adapter.commandLine(input.command());
          ProcessBuilder pb =
              new ProcessBuilder(argv)
                  .redirectErrorStream(true)
                  .directory(ctx.workingDirectory().toFile());
          sanitizeEnv(pb.environment());
          Process proc;
          try {
            proc = pb.start();
          } catch (IOException e) {
            log.warn("启动进程失败: {}", input.command(), e);
            return ToolResult.<String>error("启动进程失败: " + e.getMessage());
          }
          ExecutorService pool = createIoPool();
          Future<String> output = pool.submit(() -> readBounded(proc.getInputStream()));
          try {
            if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
              killTree(proc);
              String partial = safeGet(output);
              return ToolResult.<String>error("[TIMEOUT after " + timeoutSec + "s] " + partial);
            }
            String result = output.get(FUTURE_WAIT_SEC, TimeUnit.SECONDS);
            pool.shutdownNow();
            return ToolResult.ok(result, "<auto>");
          } catch (Exception e) {
            killTree(proc);
            log.warn("执行 shell 失败: {}", input.command(), e);
            return ToolResult.<String>error("执行失败: " + e.getMessage());
          } finally {
            pool.shutdownNow();
          }
        });
  }

  /** 显式参数的 {@link ThreadPoolExecutor}（规范 10.1-1：禁止 Executors）。 单线程 + 有界队列（避免 OOM，规范 10.1-2）。 */
  private static ExecutorService createIoPool() {
    return new ThreadPoolExecutor(
        POOL_CORE_SIZE,
        POOL_CORE_SIZE,
        POOL_KEEP_ALIVE_SEC,
        TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(POOL_QUEUE_CAPACITY),
        r -> {
          Thread t = new Thread(r, "shell-io");
          t.setDaemon(true);
          return t;
        });
  }

  private String readBounded(InputStream in) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    // 循环外分配缓冲区（规范 13.2：循环内不创建对象）
    byte[] chunk = new byte[IO_BUFFER_BYTES];
    int total = 0;
    int n;
    while ((n = in.read(chunk)) > 0) {
      if (appendBounded(buf, chunk, n, total)) break;
      total += n;
    }
    return buf.toString();
  }

  /**
   * 追加字节到 buf，超过 maxOutputBytes 时追加截断标记并返回 true（表示停止读取）。 提取为独立方法以降低 readBounded 的嵌套层级（规范 14 嵌套 ≤
   * 4）。
   */
  private boolean appendBounded(ByteArrayOutputStream buf, byte[] chunk, int n, int total) {
    if (total + n > maxOutputBytes) {
      int allowed = Math.max(0, maxOutputBytes - total);
      if (allowed > 0) buf.write(chunk, 0, allowed);
      // 显式 byte[] + UTF-8 编码，避免 ByteArrayOutputStream.write(String) 抛 UnsupportedEncodingException
      byte[] marker =
          ("\n[truncated: output exceeded " + maxOutputBytes + " bytes]")
              .getBytes(StandardCharsets.UTF_8);
      buf.write(marker, 0, marker.length);
      return true;
    }
    buf.write(chunk, 0, n);
    return false;
  }

  /** 剥离敏感环境变量（详见 design.md §6.6） */
  private void sanitizeEnv(Map<String, String> env) {
    env.keySet()
        .removeIf(
            k -> {
              String up = k.toUpperCase();
              return up.contains("API_KEY")
                  || up.contains("TOKEN")
                  || up.contains("SECRET")
                  || up.contains("PASSWORD")
                  || up.contains("PRIVATE_KEY");
            });
  }

  private String safeGet(Future<String> f) {
    try {
      return f.get(FUTURE_WAIT_SEC, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.debug("safeGet 等待 future 超时/失败", e);
      return "";
    }
  }

  private void killTree(Process proc) {
    if (!killProcessTree) {
      proc.destroy();
      return;
    }
    proc.descendants().forEach(ProcessHandle::destroy);
    proc.destroy();
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      try {
        new ProcessBuilder("taskkill", "/T", "/F", "/PID", String.valueOf(proc.pid())).start();
      } catch (IOException e) {
        // taskkill 失败时仍尝试 destroy
        log.debug("taskkill 失败，依赖 destroy", e);
      }
    }
  }
}
