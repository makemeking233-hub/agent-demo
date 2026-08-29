package com.example.agent.cli;

import com.example.agent.AbortSignal;
import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.ContextCompressor;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.permission.PermissionManager;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * REPL main loop (v0.1 simplified):
 *
 * <ul>
 *   <li>Read stdin input, dispatch to {@link AgentLoop#processTurn(Message.User)}
 *   <li>Slash commands via {@link SlashCommand}: /help /clear /quit /history
 *   <li>Supports --input (one-shot for E2E tests)
 *   <li>Supports --auto-approve-write (skip write permissions for E2E)
 * </ul>
 *
 * <p>v0.2 upgrade: JLine3 raw mode + history completion + Ctrl+C interrupt (see design.md §17).
 */
@Component
@Command(
    name = "chat",
    mixinStandardHelpOptions = true,
    description = "Start interactive REPL with multi-turn dialog")
public class ChatCommand implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ChatCommand.class);

  /** Default max tool iterations per turn (aligns with Claude Code) */
  private static final int DEFAULT_MAX_TOOL_ITERATIONS = 25;

  /** --model：覆盖默认模型名 */
  @Option(
      names = {"--model"},
      description = "Override default model")
  String model;

  /** --api-key：覆盖 API key（本次会话优先） */
  @Option(
      names = {"--api-key"},
      description = "Override API key (this session only)")
  String apiKey;

  /** --system-prompt：覆盖默认 system prompt */
  @Option(
      names = {"--system-prompt"},
      description = "Override default system prompt")
  String systemPrompt;

  /** --input：E2E 测试用一次性输入（跳过 REPL 循环） */
  @Option(names = "--input", description = "TEST: inject one-shot input (skips REPL loop)")
  String injectedInput;

  /** --auto-approve-write：E2E 测试用（跳过写权限确认） */
  @Option(names = "--auto-approve-write", description = "TEST: skip write permission confirmation")
  boolean autoApproveWrite;

  /** picocli 入口：装配 Provider / Tools / AgentLoop / Permission，启动 REPL。 */
  @Override
  public void run() {
    AgentConfig cfg = loadConfig();
    String resolvedKey =
        pickFirstNonBlank(apiKey, System.getenv("DEEPSEEK_API_KEY"), cfg.provider().apiKey());
    String resolvedModel =
        pickFirstNonBlank(model, System.getenv("AGENT_MODEL"), cfg.provider().model());
    // baseUrl 由具体 Provider 内部决定（DeepSeek / MiniMax 各自硬编码），此处不再读 cfg
    // 环境变量 base URL 暂时未使用（v0.2 可加 provider-specific 覆盖）

    // 按 provider.type() 路由到具体实现
    LlmProvider provider =
        switch (cfg.provider().type() == null ? "deepseek" : cfg.provider().type().toLowerCase()) {
          case "deepseek" -> new com.example.agent.provider.deepseek.DeepSeekProvider(resolvedKey);
          case "minimax" -> new com.example.agent.provider.minimax.MiniMaxProvider(resolvedKey);
          default -> throw new IllegalArgumentException(
              "未知 provider 类型: " + cfg.provider().type() + "（支持 deepseek / minimax）");
        };
    TokenEstimator estimator = new TokenEstimator();
    // AtomicReference: lambda-friendly mutable holder for the active MessageHistory
    // (AtomicReference replaces single-element MessageHistory[] array used in v0.1)
    AtomicReference<MessageHistory> history = new AtomicReference<>(new MessageHistory(estimator));
    ToolRegistry tools = new ToolRegistry();
    ToolRegistry.registerMemoryTools(tools);
    StreamingPrinter printer = new StreamingPrinter();
    PermissionManager perms = new PermissionManager(); // v0.1 placeholder

    ContextCompressor compressor =
        new ContextCompressor(
            provider,
            cfg.context().compactBuffer(),
            cfg.context().maxConsecutiveCompactFailures(),
            resolvedModel);

    Path workingDir = Paths.get(System.getProperty("user.dir"));
    AgentLoop loop =
        new AgentLoop(
            provider,
            tools,
            history.get(),
            printer,
            DEFAULT_MAX_TOOL_ITERATIONS,
            resolvedModel,
            workingDir);

    AtomicBoolean aborted = new AtomicBoolean(false);
    AbortSignal abortSignal = () -> aborted.get();
    SlashCommand slash = new SlashCommand();
    // Token accumulators (single-element arrays; passed by ref to SlashCommand which mutates [0])
    int[] totalPrompt = {0};
    int[] totalCompletion = {0};

    ReplContext ctx =
        new ReplContext(
            history, estimator, loop, slash, totalPrompt, totalCompletion, resolvedModel, aborted);
    runReplLoop(ctx);
  }

  /** Load user config (extracted to reduce run() nesting) */
  private AgentConfig loadConfig() {
    Path cfgPath = Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml");
    return new ConfigLoader().load(cfgPath);
  }

  /**
   * REPL 主循环：读取 stdin → 派发 slash 命令或 AgentLoop。
   *
   * @param ctx REPL 共享状态
   */
  private void runReplLoop(ReplContext ctx) {
    InputStream stdin =
        injectedInput != null
            ? new ByteArrayInputStream(injectedInput.getBytes(StandardCharsets.UTF_8))
            : System.in;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8))) {
      System.out.println(
          "agent-demo v0.1 chat (model="
              + ctx.resolvedModel()
              + "), /help for commands, /quit to exit");
      String line;
      while ((line = reader.readLine()) != null && !ctx.aborted().get()) {
        if (line.isBlank()) continue;
        if (handleLine(line, ctx)) {
          continue;
        }
        TurnResult result = null;
        try {
          result = ctx.loop().processTurn(new Message.User(line)).block();
        } catch (Exception e) {
          // 不让单次失败退出 REPL：打印错误让用户重试（/clear 清空历史）
          System.err.println("\n[error] " + friendlyError(e) + "\n");
          log.debug("REPL turn failed", e);
        }
        if (result != null) {
          ctx.totalPrompt()[0] += result.totalPromptTokens();
          ctx.totalCompletion()[0] += result.totalCompletionTokens();
        }
      }
    } catch (IOException e) {
      log.error("[chat] failed to read input", e);
    }
  }

  /** 把异常翻译成用户能看懂的提示（401 / 404 / 5xx / 网络等） */
  private static String friendlyError(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    if (msg != null && msg.contains("401")) {
      return "401 Unauthorized — DEEPSEEK_API_KEY 未设或失效。设环境变量后重启：\n"
          + "  set DEEPSEEK_API_KEY=sk-... （Windows: $env:DEEPSEEK_API_KEY='sk-...'）";
    }
    if (msg != null && msg.contains("404")) {
      return "404 Not Found — baseUrl 或 model 名错（默认 https://api.deepseek.com / deepseek-chat）";
    }
    if (msg != null && (msg.contains("429") || msg.contains("rate limit"))) {
      return "429 限流 — 稍等 30s 再试，或检查账户余额";
    }
    if (msg != null && (msg.contains("connect") || msg.contains("timeout"))) {
      return "网络错误 — 检查 baseUrl / 代理 / 防火墙";
    }
    return msg;
  }

  /**
   * Handle a single line: slash commands processed directly; return true if consumed. Other lines
   * return false so the caller passes them to AgentLoop.
   */
  private boolean handleLine(String line, ReplContext ctx) {
    return ctx.slash()
        .dispatch(
            line,
            ctx.history().get(),
            ctx.totalPrompt(),
            ctx.totalCompletion(),
            ctx.resolvedModel(),
            () -> {
              MessageHistory fresh = new MessageHistory(ctx.estimator());
              ctx.history().set(fresh);
              ctx.loop().setHistory(fresh);
            });
  }

  /**
   * REPL 循环共享状态 record（聚合 8 个参数，消除 runReplLoop/handleLine 的参数列表膨胀）。
   *
   * @param history 当前消息历史（/clear 时切换）
   * @param estimator token 估算器
   * @param loop Agent 主循环
   * @param slash slash 命令分发器
   * @param totalPrompt 累计 prompt token 累加器
   * @param totalCompletion 累计 completion token 累加器
   * @param resolvedModel 解析后的模型名
   * @param aborted 中断标志（Ctrl+C 置 true）
   */
  private record ReplContext(
      AtomicReference<MessageHistory> history,
      TokenEstimator estimator,
      AgentLoop loop,
      SlashCommand slash,
      int[] totalPrompt,
      int[] totalCompletion,
      String resolvedModel,
      AtomicBoolean aborted) {}

  /**
   * 取第一个非空白字符串（用于多源配置优先级：CLI flag &gt; env &gt; config.yaml）。
   *
   * @param candidates 候选项
   * @return 第一个非 null 且非空白的值；全部为空时返回 {@code null}
   */
  private static String pickFirstNonBlank(String... candidates) {
    for (String s : candidates) {
      if (s != null && !s.isBlank()) return s;
    }
    return null;
  }
}
