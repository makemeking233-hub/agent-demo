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
import com.example.agent.session.SessionStore;
import com.example.agent.tools.ToolRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Web 运行时装配（add-web-ui-v0-1 / D2）。
 *
 * <p>持有 CLI 与 web 共享的 provider / tools / systemPrompt 快照（由 {@link AgentLoopFactory}
 * 生成，保证与 CLI 的 provider 类型路由、tool 沙箱参数一致）。每个 web 会话调用
 * {@link #createLoop(String, SessionLogSink, PermissionConfirmer)} 生成一个独立
 * {@link AgentLoop}（各会话独立 history）。
 *
 * <p>v0.1 简化：忽略 context 压缩与会话归档（web 复用 CLI 的记忆/会话目录逻辑留待后续），
 * 但走同一 provider / tools / systemPrompt 装配，确保行为一致。
 */
@Service
@Profile("web")
public class WebAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(WebAgentRuntime.class);

    /** 会话历史保存目录（{@code ~/.agent-demo}）。 */
    private final Path agentDataDir;

    /** 已装配的共享 provider。 */
    private final LlmProvider provider;

    /** 已装配的共享工具注册表。 */
    private final ToolRegistry tools;

    /** token 估算器（history 创建用）。 */
    private final TokenEstimator estimator;

    /** 模型名（web 固定 deepseek-chat；v0.2 支持前端传参）。 */
    private final String model;

    /** web 会话时长上限（空闲回收；v0.1 简化不主动回收）。 */

    public WebAgentRuntime() {
        this.model = "deepseek-chat";
        AgentConfig cfg = new ConfigLoader().load(Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml"));
        String apiKey = pickFirstNonBlank(
                System.getenv("DEEPSEEK_API_KEY"),
                cfg.provider().apiKey());
        this.provider = AgentLoopFactory.buildProvider(cfg, apiKey);
        this.tools = AgentLoopFactory.buildTools(cfg);
        this.estimator = new TokenEstimator();
        this.agentDataDir =
                Paths.get(
                        System.getenv("AGENT_DEMO_HOME") != null
                                        && !System.getenv("AGENT_DEMO_HOME").isBlank()
                                ? System.getenv("AGENT_DEMO_HOME")
                                : System.getProperty("user.home"),
                        ".agent-demo");
    }

    /**
     * 为单个 web 会话生成 {@link AgentLoop}（独立 history）。
     *
     * <p>{@code sink} 为 web 的 SSE 通知（SseSessionLogSink）；{@code confirmer} 为权限交互桥
     * （web 用 PermissionBridge，把 ASK 转成前端卡片的 yes/no/always）。printer 用 no-op
     * （stdout 打印交给 CLI；web 只通过 SessionLogSink 下发粗粒度事件）。
     *
     * @param streamId 当前流 id（供 sink 关联）
     * @param sink 会话日志观察者（web 转 SSE）
     * @param confirmer 权限确认器（可 null = fail-closed 拒绝）
     * @return 装配好的 {@link AgentLoop}
     */
    public AgentLoop createLoop(String streamId, SessionLogSink sink, PermissionConfirmer confirmer) {
        return AgentLoopFactory.buildLoop(
                loadConfig(),
                provider,
                tools,
                new MessageHistory(estimator),
                new StreamingPrinter(),
                model,
                sink,
                agentDataDir,
                confirmer);
    }

    /** 工具注册表（暴露给需要 list 的调用者；v0.1 少用）。 */
    public ToolRegistry tools() {
        return tools;
    }

    private static AgentConfig loadConfig() {
        return new ConfigLoader()
                .load(Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml"));
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    /** 会话数据目录（暴露给 ChatStreamService 做 session 持久化）。 */
    public Path agentDataDir() {
        return agentDataDir;
    }
}
