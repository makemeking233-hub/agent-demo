package com.example.agent.cli;

import com.example.agent.AbortSignal;
import com.example.agent.agent.AgentLoop;
import com.example.agent.agent.ContextCompressor;
import com.example.agent.agent.Message;
import com.example.agent.agent.MessageHistory;
import com.example.agent.agent.TurnResult;
import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import com.example.agent.permission.PermissionManager;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.TokenEstimator;
import com.example.agent.provider.deepseek.DeepSeekProvider;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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

/**
 * REPL main loop (v0.1 simplified):
 * <ul>
 *   <li>Read stdin input, dispatch to {@link AgentLoop#processTurn(Message.User)}</li>
 *   <li>Slash commands via {@link SlashCommand}: /help /clear /quit /history</li>
 *   <li>Supports --input (one-shot for E2E tests)</li>
 *   <li>Supports --auto-approve-write (skip write permissions for E2E)</li>
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

    /** Default DeepSeek API base URL */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    /** Default max tool iterations per turn (aligns with Claude Code) */
    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 25;

    @Option(names = {"--model"}, description = "Override default model")
    String model;

    @Option(names = {"--api-key"}, description = "Override API key (this session only)")
    String apiKey;

    @Option(names = {"--system-prompt"}, description = "Override default system prompt")
    String systemPrompt;

    @Option(names = "--input", description = "TEST: inject one-shot input (skips REPL loop)")
    String injectedInput;

    @Option(names = "--auto-approve-write", description = "TEST: skip write permission confirmation")
    boolean autoApproveWrite;

    @Override
    public void run() {
        AgentConfig cfg = loadConfig();
        String resolvedKey = pickFirstNonBlank(apiKey, System.getenv("DEEPSEEK_API_KEY"), cfg.provider().apiKey());
        String resolvedModel = pickFirstNonBlank(model, System.getenv("AGENT_MODEL"), cfg.provider().model());
        String baseUrl = pickFirstNonBlank(System.getenv("DEEPSEEK_BASE_URL"), cfg.provider().baseUrl());
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = DEFAULT_BASE_URL;

        LlmProvider provider = new DeepSeekProvider(resolvedKey, baseUrl);
        TokenEstimator estimator = new TokenEstimator();
        // AtomicReference: lambda-friendly mutable holder for the active MessageHistory
        // (AtomicReference replaces single-element MessageHistory[] array used in v0.1)
        AtomicReference<MessageHistory> history = new AtomicReference<>(new MessageHistory(estimator));
        ToolRegistry tools = new ToolRegistry();
        ToolRegistry.registerMemoryTools(tools);
        StreamingPrinter printer = new StreamingPrinter();
        PermissionManager perms = new PermissionManager();  // v0.1 placeholder

        ContextCompressor compressor = new ContextCompressor(provider,
            cfg.context().compactBuffer(),
            cfg.context().maxConsecutiveCompactFailures(),
            resolvedModel);

        Path workingDir = Paths.get(System.getProperty("user.dir"));
        AgentLoop loop = new AgentLoop(provider, tools, history.get(), printer,
            DEFAULT_MAX_TOOL_ITERATIONS, resolvedModel, workingDir);

        AtomicBoolean aborted = new AtomicBoolean(false);
        AbortSignal abortSignal = () -> aborted.get();
        SlashCommand slash = new SlashCommand();
        // Token accumulators (single-element arrays; passed by ref to SlashCommand which mutates [0])
        int[] totalPrompt = {0};
        int[] totalCompletion = {0};

        runReplLoop(history, estimator, loop, slash, totalPrompt, totalCompletion, resolvedModel, aborted);
    }

    /** Load user config (extracted to reduce run() nesting) */
    private AgentConfig loadConfig() {
        Path cfgPath = Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml");
        return new ConfigLoader().load(cfgPath);
    }

    /** REPL main loop: read stdin -> dispatch slash or AgentLoop (extracted to reduce run() lines) */
    private void runReplLoop(AtomicReference<MessageHistory> history, TokenEstimator estimator,
                             AgentLoop loop, SlashCommand slash,
                             int[] totalPrompt, int[] totalCompletion, String resolvedModel,
                             AtomicBoolean aborted) {
        InputStream stdin = injectedInput != null
            ? new ByteArrayInputStream(injectedInput.getBytes(StandardCharsets.UTF_8))
            : System.in;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8))) {
            System.out.println("agent-demo v0.1 chat (model=" + resolvedModel + "), /help for commands, /quit to exit");
            String line;
            while ((line = reader.readLine()) != null && !aborted.get()) {
                if (line.isBlank()) continue;
                if (handleLine(line, history, estimator, loop, slash, totalPrompt, totalCompletion, resolvedModel)) {
                    continue;
                }
                TurnResult result = loop.processTurn(new Message.User(line)).block();
                if (result != null) {
                    totalPrompt[0] += result.totalPromptTokens();
                    totalCompletion[0] += result.totalCompletionTokens();
                }
            }
        } catch (IOException e) {
            log.error("[chat] failed to read input", e);
        }
    }

    /**
     * Handle a single line: slash commands processed directly; return true if consumed.
     * Other lines return false so the caller passes them to AgentLoop.
     */
    private boolean handleLine(String line, AtomicReference<MessageHistory> history, TokenEstimator estimator,
                               AgentLoop loop, SlashCommand slash,
                               int[] totalPrompt, int[] totalCompletion, String resolvedModel) {
        return slash.dispatch(line, history.get(), totalPrompt, totalCompletion, resolvedModel,
            () -> {
                MessageHistory fresh = new MessageHistory(estimator);
                history.set(fresh);
                loop.setHistory(fresh);
            });
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}