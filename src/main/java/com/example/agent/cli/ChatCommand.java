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
import com.example.agent.provider.deepseek.DeepSeekProvider;
import com.example.agent.provider.TokenEstimator;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;
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
        AgentConfig cfg = new ConfigLoader().load(Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml"));

        // API key 优先级：--api-key > env > config
        String resolvedKey = pickFirstNonBlank(apiKey, System.getenv("DEEPSEEK_API_KEY"), cfg.provider().apiKey());
        String resolvedModel = pickFirstNonBlank(model, System.getenv("AGENT_MODEL"), cfg.provider().model());
        String baseUrl = pickFirstNonBlank(System.getenv("DEEPSEEK_BASE_URL"), cfg.provider().baseUrl());
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.deepseek.com";

        LlmProvider provider = new DeepSeekProvider(resolvedKey, baseUrl);
        TokenEstimator estimator = new TokenEstimator();
        MessageHistory[] histRef = {new MessageHistory(estimator)};
        ToolRegistry tools = new ToolRegistry();
        ToolRegistry.registerMemoryTools(tools);
        StreamingPrinter printer = new StreamingPrinter();

        // 权限管理器（v0.1 简化版：auto-approve 时所有写都 allow）
        PermissionManager perms = new PermissionManager();
        if (autoApproveWrite) {
            // 注：当前 PermissionManager 默认对 write/ask；autoApproveWrite v0.1 仅是占位，
            // 真"自动批准"在 v0.2 通过 PermissionManager.decide() override 实现
        }

        // 上下文压缩
        ContextCompressor compressor = new ContextCompressor(provider,
            cfg.context().compactBuffer(),
            cfg.context().maxConsecutiveCompactFailures(),
            resolvedModel);

        Path workingDir = Paths.get(System.getProperty("user.dir"));
        AgentLoop loop = new AgentLoop(provider, tools, histRef[0], printer, 25,
            resolvedModel, workingDir);

        // 中断信号（v0.1 简化：JVM 关闭 hook）
        AtomicBoolean aborted = new AtomicBoolean(false);
        AbortSignal abortSignal = () -> aborted.get();

        // Slash 命令
        SlashCommand slash = new SlashCommand();
        int[] totalPrompt = {0};
        int[] totalCompletion = {0};

        // stdin 处理（--input 一次性注入用于 E2E 测试）
        InputStream stdin = injectedInput != null
            ? new ByteArrayInputStream(injectedInput.getBytes(StandardCharsets.UTF_8))
            : System.in;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8))) {
            System.out.println("agent-demo v0.1 chat (model=" + resolvedModel + ")，输入 /help 查看命令，/quit 退出");
            String line;
            while ((line = reader.readLine()) != null && !aborted.get()) {
                if (line.isBlank()) continue;
                if (slash.dispatch(line, histRef[0], totalPrompt, totalCompletion, resolvedModel,
                        () -> {
                            MessageHistory fresh = new MessageHistory(estimator);
                            histRef[0] = fresh;
                            loop.setHistory(fresh);
                        })) {
                    continue;
                }
                TurnResult result = loop.processTurn(new Message.User(line)).block();
                if (result != null) {
                    totalPrompt[0] += result.totalPromptTokens();
                    totalCompletion[0] += result.totalCompletionTokens();
                }
            }
        } catch (IOException e) {
            System.err.println("[chat] 读取输入失败: " + e.getMessage());
        }
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}