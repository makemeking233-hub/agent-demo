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

/**
 * REPL 主循环（v0.1 简化版）：
 * <ul>
 *   <li>读取 stdin 输入，调 {@link AgentLoop#processTurn(Message.User)}</li>
 *   <li>{@code /help /clear /quit /history} 通过 {@link SlashCommand} 处理</li>
 *   <li>支持 {@code --input} 一次性输入（E2E 测试用）</li>
 *   <li>支持 {@code --auto-approve-write} 跳过写操作权限确认</li>
 * </ul>
 *
 * <p>v0.2 升级：JLine3 raw mode + 历史补全 + Ctrl+C 中断（详见 design.md §17）。
 */
@Component
@Command(
    name = "chat",
    mixinStandardHelpOptions = true,
    description = "启动交互式 REPL，进入多轮对话")
public class ChatCommand implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ChatCommand.class);

    /** 默认 API base URL（DeepSeek 官方） */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    /** 默认单轮工具调用上限（与 Claude Code 对齐） */
    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 25;

    @Option(names = {"--model"}, description = "覆盖默认模型")
    String model;

    @Option(names = {"--api-key"}, description = "覆盖 API key（仅本次）")
    String apiKey;

    @Option(names = {"--system-prompt"}, description = "覆盖默认 system prompt")
    String systemPrompt;

    @Option(names = "--input", description = "测试用：注入一次性输入")
    String injectedInput;

    @Option(names = "--auto-approve-write", description = "测试用：跳过写权限确认")
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
        MessageHistory[] histRef = {new MessageHistory(estimator)};
        ToolRegistry tools = new ToolRegistry();
        ToolRegistry.registerMemoryTools(tools);
        StreamingPrinter printer = new StreamingPrinter();
        PermissionManager perms = new PermissionManager();  // v0.1 占位：autoApproveWrite 行为后续完善

        ContextCompressor compressor = new ContextCompressor(provider,
            cfg.context().compactBuffer(),
            cfg.context().maxConsecutiveCompactFailures(),
            resolvedModel);

        Path workingDir = Paths.get(System.getProperty("user.dir"));
        AgentLoop loop = new AgentLoop(provider, tools, histRef[0], printer,
            DEFAULT_MAX_TOOL_ITERATIONS, resolvedModel, workingDir);

        AtomicBoolean aborted = new AtomicBoolean(false);
        AbortSignal abortSignal = () -> aborted.get();
        SlashCommand slash = new SlashCommand();
        int[] totalPrompt = {0};
        int[] totalCompletion = {0};

        runReplLoop(histRef, estimator, loop, slash, totalPrompt, totalCompletion, resolvedModel, aborted);
    }

    /** 加载用户 config（拆出来降低 run() 嵌套/行数，规范 14） */
    private AgentConfig loadConfig() {
        Path cfgPath = Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml");
        return new ConfigLoader().load(cfgPath);
    }

    /** REPL 主循环：读 stdin → 调 SlashCommand 或 AgentLoop（拆出来降低 run() 行数） */
    private void runReplLoop(MessageHistory[] histRef, TokenEstimator estimator,
                             AgentLoop loop, SlashCommand slash,
                             int[] totalPrompt, int[] totalCompletion, String resolvedModel,
                             AtomicBoolean aborted) {
        InputStream stdin = injectedInput != null
            ? new ByteArrayInputStream(injectedInput.getBytes(StandardCharsets.UTF_8))
            : System.in;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8))) {
            System.out.println("agent-demo v0.1 chat (model=" + resolvedModel + ")，输入 /help 查看命令，/quit 退出");
            String line;
            while ((line = reader.readLine()) != null && !aborted.get()) {
                if (line.isBlank()) continue;
                if (handleLine(line, histRef, estimator, loop, slash, totalPrompt, totalCompletion, resolvedModel)) {
                    continue;
                }
                TurnResult result = loop.processTurn(new Message.User(line)).block();
                if (result != null) {
                    totalPrompt[0] += result.totalPromptTokens();
                    totalCompletion[0] += result.totalCompletionTokens();
                }
            }
        } catch (IOException e) {
            log.error("[chat] 读取输入失败", e);
        }
    }

    /** 处理单行：slash 命令直接处理；普通输入返回 false 让调用方走 AgentLoop */
    private boolean handleLine(String line, MessageHistory[] histRef, TokenEstimator estimator,
                               AgentLoop loop, SlashCommand slash,
                               int[] totalPrompt, int[] totalCompletion, String resolvedModel) {
        return slash.dispatch(line, histRef[0], totalPrompt, totalCompletion, resolvedModel,
            () -> {
                MessageHistory fresh = new MessageHistory(estimator);
                histRef[0] = fresh;
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