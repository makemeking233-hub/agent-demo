package com.example.agent.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * agent-demo 顶层配置（详见 design.md §9）。
 *
 * <p>三层优先级：环境变量 > {@code ~/.agent-demo/config.yaml} > 内置默认（{@link #defaults()}）。
 *
 * @param provider LLM provider 配置（DeepSeek / OpenAI / 自定义）
 * @param permission 权限策略
 * @param cost 成本控制阈值（按 model 分桶，v0.1 简化）
 * @param context 上下文压缩参数
 * @param shell ShellTool 沙箱参数
 * @param memoryInject 注入到 system prompt 的额外 memory 指引
 * @param logging 会话结构化日志配置
 * @param memory Memory 相关配置（含 sideQuery 语义召回）
 * @param mcp MCP 客户端配置
 * @param worktree Worktree 隔离工作区配置
 * @param plugins 插件列表（add-plugin-system v1.0）。每个 Plugin 启动时由 PluginManager.init() 串行 init, 关闭时反序 close.
 * @param search 网络搜索配置（add-web-search-tool）：provider 选择 + 结果数 + 超时
 * @param voice 语音相关配置（improve-voice-accuracy T8）：含 postProcess 开关
 */
public record AgentConfig(
        Provider provider,
        Permission permission,
        Cost cost,
        Context context,
        Shell shell,
        List<String> memoryInject,
        Logging logging,
        Memory memory,
        Mcp mcp,
        Worktree worktree,
        List<PluginConfig> plugins,
        Search search,
        Voice voice,
        Skill skill) {

    /**
     * 兼容构造器（13 参，{@code skill} 取默认值）。
     *
     * <p>add-skill-demand-loading 新增 {@code skill} 字段后，既有调用方（大量测试与
     * {@code ConfigLoader} 的部分路径）仍按 13 参构造——本构造器让它们无需改动即保持原行为
     * （默认 {@code search} 档）。
     */
    public AgentConfig(
            Provider provider,
            Permission permission,
            Cost cost,
            Context context,
            Shell shell,
            List<String> memoryInject,
            Logging logging,
            Memory memory,
            Mcp mcp,
            Worktree worktree,
            List<PluginConfig> plugins,
            Search search,
            Voice voice) {
        this(
                provider,
                permission,
                cost,
                context,
                shell,
                memoryInject,
                logging,
                memory,
                mcp,
                worktree,
                plugins,
                search,
                voice,
                Skill.defaults());
    }

    /**
     * 技能暴露档位（add-skill-demand-loading T1.1）。
     *
     * <ul>
     *   <li>{@link #ALL}：全部 skill 直露为工具（不限制数量，等价重构前行为）
     *   <li>{@link #SEARCH}：不直露任何 skill，仅注册 {@code skill_search}（**默认**，最安全）
     *   <li>{@link #HYBRID}：按热门规则直露至多 {@code hybridDirectLimit} 个 + {@code skill_search}
     * </ul>
     */
    public enum SkillMode {
        ALL,
        SEARCH,
        HYBRID;

        /**
         * 解析档位名（大小写不敏感）。
         *
         * @param raw 原始值（{@code null} / 空白 / 非法值均回退 {@link #SEARCH}）
         * @return 生效档位
         */
        public static SkillMode from(String raw) {
            if (raw == null || raw.isBlank()) return SEARCH;
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return SEARCH;
            }
        }
    }

    /**
     * 技能按需加载配置（add-skill-demand-loading）。
     *
     * @param mode                     暴露档位（缺省 {@link SkillMode#SEARCH}）
     * @param hybridDirectLimit        hybrid 档直露总数上限（{@code <= 0} 回退 10）
     * @param hybridProjectQuota       hybrid 档中 PROJECT scope 的名额（clamp 到 directLimit）
     * @param recallK                  候选名单长度（{@code <= 0} 回退 20）
     * @param decayLambda              频次指数衰减系数（{@code <= 0} 回退 0.05，半衰期约 14 天）
     * @param descriptionTruncateChars 候选名单描述截断字符数（{@code <= 0} 回退 40）
     */
    public record Skill(
            SkillMode mode,
            int hybridDirectLimit,
            int hybridProjectQuota,
            int recallK,
            double decayLambda,
            int descriptionTruncateChars) {

        /** 默认直露上限。 */
        public static final int DEFAULT_DIRECT_LIMIT = 10;
        /** 默认 PROJECT 配额。 */
        public static final int DEFAULT_PROJECT_QUOTA = 6;
        /** 默认候选名单长度。 */
        public static final int DEFAULT_RECALL_K = 20;
        /** 默认衰减系数（半衰期 ln2/0.05 ≈ 13.9 天）。 */
        public static final double DEFAULT_DECAY_LAMBDA = 0.05;
        /** 默认描述截断字符数。 */
        public static final int DEFAULT_TRUNCATE_CHARS = 40;

        /** 紧凑构造：非法值回退默认，project quota clamp 到 direct limit。 */
        public Skill {
            mode = mode == null ? SkillMode.SEARCH : mode;
            hybridDirectLimit =
                    hybridDirectLimit > 0 ? hybridDirectLimit : DEFAULT_DIRECT_LIMIT;
            int quota = hybridProjectQuota > 0 ? hybridProjectQuota : DEFAULT_PROJECT_QUOTA;
            hybridProjectQuota = Math.min(quota, hybridDirectLimit);
            recallK = recallK > 0 ? recallK : DEFAULT_RECALL_K;
            decayLambda = decayLambda > 0 ? decayLambda : DEFAULT_DECAY_LAMBDA;
            descriptionTruncateChars =
                    descriptionTruncateChars > 0
                            ? descriptionTruncateChars
                            : DEFAULT_TRUNCATE_CHARS;
        }

        /** @return 默认配置（search 档 + 全部默认值） */
        public static Skill defaults() {
            return new Skill(
                    SkillMode.SEARCH,
                    DEFAULT_DIRECT_LIMIT,
                    DEFAULT_PROJECT_QUOTA,
                    DEFAULT_RECALL_K,
                    DEFAULT_DECAY_LAMBDA,
                    DEFAULT_TRUNCATE_CHARS);
        }
    }

    public record PluginConfig(String className, Map<String, Object> config) {
        public PluginConfig {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("PluginConfig.className 不能为空");
            }
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record Provider(
            String type, String apiKey, String baseUrl, String model, int maxOutputTokens) {}

    public record Permission(String defaultPolicy, List<String> shellDenylist) {}

    public record Cost(
            double inputPerMTokens,
            double outputPerMTokens,
            double warnThreshold,
            double stopThreshold) {}

    public record Context(int compactBuffer, int maxConsecutiveCompactFailures) {}

    public record Shell(int timeoutMs, int maxOutputBytes) {}

    public record Logging(
            boolean enabled,
            String dir,
            int resultMaxChars,
            int snapshotMaxChars,
            int retentionMaxAgeDays,
            int retentionKeepSessions) {}

    /**
     * Memory 配置。
     *
     * @param sideQuery        sideQuery 语义召回补充配置
     * @param dynamicRetrieval 是否启用「每轮按当前用户提问动态召回」（fix-memory-recall-wiring）。
     *                         {@code true}（默认）= 每轮请求组装时按当轮 query 召回并注入记忆段；
     *                         {@code false} = 回退为启动期注入各 scope 全量索引（截断 200 行 / 25KB），
     *                         即 v0.1 兼容行为。关闭可省去每轮的索引解析与 sideQuery 调用开销。
     * @param embedding       embedding 向量检索配置（add-embedding-rag）。关闭时三层召回退化为
     *                         「字面 + sideQuery」两层；模型缺失时静默降级（不阻断主对话）。
     */
    public record Memory(SideQuery sideQuery, boolean dynamicRetrieval, Embedding embedding) {}

    public record SideQuery(boolean enabled, int maxCandidates, int minCandidates) {}

    /**
     * embedding 向量检索配置（add-embedding-rag）。
     *
     * @param enabled   是否启用 embedding 粗排层。{@code true}（默认）= 三层架构；
     *                  {@code false} = 退回「字面 + sideQuery」两层
     * @param modelPath ONNX 模型文件路径；缺省 {@code <agentHome>/models/bge-small-zh-v1.5/model.onnx}
     * @param hnsw      Lucene HNSW 索引参数（m / efConstruction）
     */
    public record Embedding(boolean enabled, String modelPath, HnswConfig hnsw) {}

    /**
     * Lucene HNSW 索引参数。{@code m} 是图节点的最大连接数（越大越精确但越慢），
     * {@code efConstruction} 是构建时的搜索深度（越大索引质量越高但构建越慢）。
     */
    public record HnswConfig(int m, int efConstruction) {}

    public record Mcp(List<McpServer> servers) {
        public Mcp {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    public record McpServer(String name, String url) {}

    public record Worktree(boolean enabled, String baseDir) {}

    /**
     * 网络搜索配置（add-web-search-tool）。
     *
     * @param provider   provider 名（{@code deepseek} / {@code tavily}）；空字符串表示按模型自动推断
     * @param maxResults 默认最大结果数
     * @param timeoutMs  搜索超时（毫秒）
     */
    public record Search(String provider, int maxResults, int timeoutMs) {}

    /**
     * 语音相关配置（improve-voice-accuracy T8）。
     *
     * @param postProcess ASR 后处理（含 partial 状态机 + DeepSeek 语义纠错 + dedupeRepeats）。
     *                    关闭后前端跳过 contextCorrect 调用，仅依赖本地 dedupeRepeats。
     */
    public record Voice(PostProcess postProcess) {
        public Voice {
            postProcess = postProcess == null ? new PostProcess(true) : postProcess;
        }
    }

    /**
     * ASR 后处理开关（improve-voice-accuracy T8.3）。
     *
     * <p>{@code enabled=true} 时前端 useVoiceChat 会调 {@code /api/chat/voice-correction}
     * 让 DeepSeek 做语义纠错；{@code enabled=false} 时跳过此调用（保留 dedupeRepeats 本地去重）。
     *
     * <p>启动门禁：{@code enabled=true} 但 DeepSeek API key 未配置 → 启动失败（详见
     * {@link com.example.agent.web.config.WebConfig#validateVoiceConfig}）。
     *
     * @param enabled 是否启用 ASR 后处理（默认 true）
     */
    public record PostProcess(boolean enabled) {}

    public static AgentConfig defaults() {
        return new AgentConfig(
                new Provider("deepseek", "", "https://api.deepseek.com", "deepseek-v4-flash", 8192),
                new Permission(
                        "ask-write",
                        List.of(
                                "rm -rf /",
                                "mkfs",
                                "dd if=.*of=/dev/.*",
                                "chmod -R 777 /",
                                "shutdown",
                                "reboot",
                                "format",
                                "rd /s /q C:\\",
                                "del /f /s /q C:\\*",
                                "diskpart",
                                "bcdedit",
                                "reg delete HKLM")),
                new Cost(2.0, 8.0, 4.0, 5.0),
                new Context(8000, 3),
                new Shell(120_000, 1_000_000),
                List.of(),
                new Logging(
                        true,
                        // 日志根固定为 <agent 数据目录>/logs（improve-failure-observability）：
                        // 此前是 ${user.dir}/logs，随进程工作目录漂移，且与 /api/logs 读取位置不一致。
                        // fix-agent-home-isolation：改由 AgentPaths 单一入口解析，使系统属性
                        // agent.demo.home 也能覆盖日志根（此前只有 WebAgentRuntime 认它，
                        // 导致测试日志照样写进用户真实 ~/.agent-demo/logs）。
                        AgentPaths.logsDir(),
                        30_000,
                        2_000,
                        30,
                        50),
                // add-embedding-rag：缺省开启 embedding 粗排；模型路径指向 agentHome 下 models/ 目录
                new Memory(
                        new SideQuery(true, 8, 3),
                        true,
                        new Embedding(
                                true,
                                Path.of(AgentPaths.agentHome().toString(), "models", "bge-small-zh-v1.5", "model.onnx")
                                        .toString(),
                                new HnswConfig(16, 200))),
                new Mcp(List.of()),
                new Worktree(false, AgentPaths.worktreesDir()),
                List.of(),
                new Search("", 5, 60_000),
                // improve-voice-accuracy T8: 默认开启 ASR 后处理（语义纠错）；DeepSeek key 由启动门禁校验。
                new Voice(new PostProcess(true)),
                Skill.defaults());
    }
}
