package com.example.agent.web.stream;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.AgentLoopFactory;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Web 运行时装配（add-web-ui-v0-1 / D2）。
 *
 * <p>注入共享的 {@link LlmProvider} / {@link ToolRegistry} / {@link TokenEstimator}（由
 * {@link com.example.agent.web.config.WebRuntimeConfig} 提供，与 CLI 的装配一致）。每个 web 会话
 * 调用 {@link #createLoop(String, SessionLogSink, PermissionConfirmer)} 生成独立
 * {@link AgentLoop}（各会话独立 history）。
 *
 * <p>集成测试可用 {@code @MockBean LlmProvider} 替换 provider，注入固定 chunk 序列。
 */
@Service
@Profile("web")
public class WebAgentRuntime {

    /** agent 数据目录（{@code ~/.agent-demo}）。 */
    private final Path agentDataDir;

    /** 已装配的共享 provider。 */
    private final LlmProvider provider;

    /** 已装配的共享工具注册表。 */
    private final ToolRegistry tools;

    /** token 估算器（history 创建用）。 */
    private final TokenEstimator estimator;

    /** 模型名（web 固定 deepseek-chat；v0.2 支持前端传参）。 */
    private final String model;

    /** 已加载的配置。 */
    private final AgentConfig cfg;

    public WebAgentRuntime(LlmProvider provider, ToolRegistry tools, TokenEstimator estimator) {
        this.provider = provider;
        this.tools = tools;
        this.estimator = estimator;
        this.model = "deepseek-chat";
        this.cfg =
                new ConfigLoader()
                        .load(
                                Paths.get(
                                        System.getProperty("user.home"),
                                        ".agent-demo",
                                        "config.yaml"));
        String userHome =
                System.getenv("AGENT_DEMO_HOME") != null
                                && !System.getenv("AGENT_DEMO_HOME").isBlank()
                        ? System.getenv("AGENT_DEMO_HOME")
                        : System.getProperty("user.home");
        this.agentDataDir = Paths.get(userHome, ".agent-demo");
    }

    /**
     * 为单个 web 会话生成 {@link AgentLoop}（独立 history）。
     *
     * <p>{@code sink} 为 web 的 SSE 通知（SseSessionLogSink）；{@code confirmer} 为权限交互桥
     * （web 用 PermissionBridge）。printer 用 no-op（stdout 打印交给 CLI；web 只通过
     * SessionLogSink 下发粗粒度事件）。
     *
     * @param streamId 当前流 id（留作扩展）
     * @param sink 会话日志观察者（web 转 SSE）
     * @param confirmer 权限确认器（可 null = fail-closed 拒绝）
     * @return 装配好的 {@link AgentLoop}
     */
    public AgentLoop createLoop(String streamId, SessionLogSink sink, PermissionConfirmer confirmer) {
        return AgentLoopFactory.buildLoop(
                cfg,
                provider,
                tools,
                new MessageHistory(estimator),
                new StreamingPrinter(),
                model,
                sink,
                agentDataDir,
                confirmer);
    }

    public ToolRegistry tools() {
        return tools;
    }

    public Path agentDataDir() {
        return agentDataDir;
    }
}
