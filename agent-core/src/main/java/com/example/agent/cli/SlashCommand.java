package com.example.agent.cli;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.provider.ProviderInference;
import com.example.agent.session.SessionResumeLoader;
import com.example.agent.session.SessionStore;
import com.example.agent.worktree.WorktreeManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Slash 命令分发（v0.1：/help /clear /quit /history；v0.2 加 /resume）。
 *
 * <p>v0.1 简化版：{@code /history} 费用估算硬编码 DeepSeek-chat 价格（2/8 元/M token）。 v0.2 改为读 {@code
 * AgentConfig.cost()}。
 */
public class SlashCommand {
    /** v0.2 支持的 slash 命令清单（用于 help 输出与补全） */
    private static final List<String> COMMANDS =
            List.of("/help", "/clear", "/quit", "/history", "/resume", "/model", "/effort");

    /** v0.2 支持的 model 列表（DeepSeek 系；无 `provider/` 前缀时的简写白名单） */
    private static final List<String> SUPPORTED_MODELS =
            List.of("deepseek-chat", "deepseek-reasoner");

    /**
     * add-provider-catalog-abstract task 12.1：已知 provider id（校验 `&lt;provider&gt;/&lt;model&gt;` 路径）。
     */
    private static final List<String> SUPPORTED_PROVIDERS =
            List.of("deepseek", "openai", "anthropic", "minimax");

    /**
     * add-provider-catalog-abstract task 12.2：`/model` 简写别名 → `{provider, model}`。
     *
     * <p>向后兼容 v0.1 的 `/model chat` / `/model reasoning` 用法。
     */
    private static final Map<String, String[]> MODEL_ALIASES =
            Map.of(
                    "chat", new String[] {"deepseek", "deepseek-chat"},
                    "reasoning", new String[] {"deepseek", "deepseek-reasoner"});

    /** add-models-dropdown-v0：合法的 reasoningEffort 白名单 */
    private static final List<String> SUPPORTED_EFFORTS = List.of("low", "medium", "high");

    /** 成本配置（v0.2 从 AgentConfig.cost 注入；null 时用 DeepSeek-chat 默认 2/8） */
    private AgentConfig.Cost cost = new AgentConfig.Cost(2.0, 8.0, 4.0, 5.0);

    /** Worktree 管理器（/worktree 用；null 时 /worktree 退化为提示信息） */
    private WorktreeManager worktreeManager;

    /** add-models-dropdown-v0：/effort 回调（ChatCommand 启动时注入；null 时 /effort 退化为提示信息） */
    private Consumer<String> onEffort;

    /**
     * add-provider-catalog-abstract task 12.1：`/model` provider+model 复合回调。
     *
     * <p>注入后优先于 {@link #onModel}；接收 {@code (providerId, modelId)}。ChatCommand 用它同时调
     * {@code AgentLoop.setProviderId} + {@code setModel}。
     */
    private BiConsumer<String, String> onSelection;

    /**
     * add-provider-catalog-abstract task 12.1：默认 provider（`/model foo` 无前缀且无法推断时兜底）。
     */
    private String defaultProvider = "deepseek";

    /**
     * 注入成本配置（ChatCommand 启动时调；v0.3+ 可 per-model 覆盖）。
     *
     * @param cost AgentConfig.cost（不可空；null 视为不修改）
     */
    public void setCost(AgentConfig.Cost cost) {
        if (cost != null) this.cost = cost;
    }

    /**
     * 注入 Worktree 管理器（ChatCommand 启动时调）。
     *
     * @param manager Worktree 管理器（可 null）
     */
    public void setWorktreeManager(WorktreeManager manager) {
        this.worktreeManager = manager;
    }

    /**
     * add-models-dropdown-v0：注入 /effort 回调（ChatCommand 启动时调）。
     *
     * @param onEffort 思考强度切换回调（接收新 effort；{@code null} = /effort 退化为提示信息）
     */
    public void setOnEffort(Consumer<String> onEffort) {
        this.onEffort = onEffort;
    }

    /**
     * add-provider-catalog-abstract task 12.1：注入 `/model` provider+model 回调。
     *
     * <p>注入后优先于 `dispatch(...)` 里传入的单个 `onModel` 回调。
     *
     * @param onSelection 接收 (providerId, modelId) 的回调；{@code null} = 退回 onModel
     */
    public void setOnSelection(BiConsumer<String, String> onSelection) {
        this.onSelection = onSelection;
    }

    /**
     * add-provider-catalog-abstract task 12.1：注入默认 provider（无前缀且无法推断时兜底）。
     *
     * @param defaultProvider provider id（{@code null} / blank = 保持 deepseek）
     */
    public void setDefaultProvider(String defaultProvider) {
        if (defaultProvider != null && !defaultProvider.isBlank()) {
            this.defaultProvider = defaultProvider;
        }
    }

    /**
     * 分发单行输入到 slash 命令处理（v0.1 兼容版：不支持 /resume）。
     *
     * @param input 原始输入
     * @param hist 当前消息历史（/history 读、/clear 替换）
     * @param totalPromptTokens 累计 prompt token（{@code int[1]}，/history 读、AgentLoop 累加）
     * @param totalCompletionTokens 累计 completion token（同上）
     * @param model 当前模型名（/history 显示用）
     * @param onClear /clear 触发的回调（清空 history + 切换 AgentLoop）
     * @return true=该行被 slash 命令消费（不传给 AgentLoop）；false=普通输入
     */
    public boolean dispatch(
            String input,
            MessageHistory hist,
            int[] totalPromptTokens,
            int[] totalCompletionTokens,
            String model,
            Runnable onClear) {
        return dispatch(
                input, hist, totalPromptTokens, totalCompletionTokens, model, onClear, null, null, null);
    }

    /**
     * 分发单行输入到 slash 命令处理（v0.2 完整版：支持 /resume）。
     *
     * @param input 原始输入
     * @param hist 当前消息历史（/history 读、/clear 替换）
     * @param totalPromptTokens 累计 prompt token
     * @param totalCompletionTokens 累计 completion token
     * @param model 当前模型名（/history 显示用）
     * @param onClear /clear 触发的回调
     * @param sessionsDir /resume 用的 sessions 目录（{@code null} 时 /resume 退化为提示信息）
     * @param onResume /resume 触发的回调（接收加载的 entry 列表；空 list 表示无历史）
     * @return true=该行被 slash 命令消费
     */
    public boolean dispatch(
            String input,
            MessageHistory hist,
            int[] totalPromptTokens,
            int[] totalCompletionTokens,
            String model,
            Runnable onClear,
            Path sessionsDir,
            Consumer<List<Message>> onResume) {
        return dispatch(
                input,
                hist,
                totalPromptTokens,
                totalCompletionTokens,
                model,
                onClear,
                sessionsDir,
                // 兼容旧调用：把 ResumeResult 的 messages 传给 List<Message> 回调
                rr -> onResume.accept(rr.messages()),
                null);
    }

    /**
     * 分发单行输入到 slash 命令处理（v0.2 完整版：支持 /resume / /model）。
     *
     * @param input 原始输入
     * @param hist 当前消息历史（/history 读、/clear 替换）
     * @param totalPromptTokens 累计 prompt token
     * @param totalCompletionTokens 累计 completion token
     * @param model 当前模型名（/history 显示用 + /model 校验）
     * @param onClear /clear 触发的回调
     * @param sessionsDir /resume 用的 sessions 目录
     * @param onResume /resume 触发的回调
     * @param onModel /model 触发的回调（接收新 model 名；null 时 /model 退化为 list-only）
     * @return true=该行被 slash 命令消费
     */
    public boolean dispatch(
            String input,
            MessageHistory hist,
            int[] totalPromptTokens,
            int[] totalCompletionTokens,
            String model,
            Runnable onClear,
            Path sessionsDir,
            Consumer<SessionResumeLoader.ResumeResult> onResume,
            Consumer<String> onModel) {
        String trimmed = input.trim();
        if (!trimmed.startsWith("/")) return false;
        switch (trimmed) {
            case "/help" -> printHelp();
            case "/clear" -> {
                onClear.run();
                System.out.println("[已清空会话历史]");
            }
            case "/quit" -> {
                System.exit(0);
            }
            case "/history" -> printHistory(
                    hist, totalPromptTokens[0], totalCompletionTokens[0], model);
            case "/resume" -> doResume(sessionsDir, onResume);
            default -> {
                if (trimmed.startsWith("/model")) {
                    doModel(trimmed, model, onModel);
                } else if (trimmed.startsWith("/effort")) {
                    doEffort(trimmed);
                } else if (trimmed.startsWith("/worktree")) {
                    doWorktree(trimmed);
                } else {
                    System.out.println("[未知命令] 输入 /help 查看可用命令");
                }
            }
        }
        return true;
    }

    /**
     * /model 处理：列表（无参数）/ 切换（有参数）。
     *
     * <p>add-provider-catalog-abstract task 12.1/12.2 起支持三种写法：
     *
     * <ol>
     *   <li>`/model &lt;provider&gt;/&lt;model&gt;` — 完整路径（provider 必须在
     *       {@link #SUPPORTED_PROVIDERS} 白名单内）
     *   <li>`/model chat` / `/model reasoning` — v0.1 别名，内部映射到 `deepseek/deepseek-chat`
     *       / `deepseek/deepseek-reasoner`（向后兼容）
     *   <li>`/model &lt;model&gt;` — 简写；provider 用 {@code ProviderInference} 按前缀推断，
     *       推断失败回退 {@link #defaultProvider}
     * </ol>
     *
     * @param trimmed 完整输入（已 trim）
     * @param currentModel 当前 model（用于无参数时显示）
     * @param onModel 单 model setter 回调（null 时只 list 不调 setter；{@link #onSelection} 优先）
     */
    private void doModel(String trimmed, String currentModel, Consumer<String> onModel) {
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            // /model 无参数：列出当前 + 支持
            System.out.println("当前 model: " + currentModel);
            System.out.println("支持: " + String.join(", ", SUPPORTED_MODELS));
            System.out.println(
                    "也可用 <provider>/<model> 指定 provider（支持: "
                            + String.join(", ", SUPPORTED_PROVIDERS)
                            + "）；别名: "
                            + String.join(", ", MODEL_ALIASES.keySet()));
            return;
        }
        String target = parts[1].trim();
        String providerId;
        String modelId;

        int slash = target.indexOf('/');
        if (slash > 0) {
            // 完整路径 <provider>/<model>
            providerId = target.substring(0, slash).toLowerCase(Locale.ROOT);
            modelId = target.substring(slash + 1).trim();
            if (modelId.isEmpty()) {
                System.out.println("[model 为空] 用法: /model <provider>/<model>");
                return;
            }
            if (!SUPPORTED_PROVIDERS.contains(providerId)) {
                System.out.println(
                        "[未知 provider: " + providerId + "] 支持: "
                                + String.join(", ", SUPPORTED_PROVIDERS));
                return;
            }
        } else if (MODEL_ALIASES.containsKey(target.toLowerCase(Locale.ROOT))) {
            // v0.1 别名向后兼容
            String[] alias = MODEL_ALIASES.get(target.toLowerCase(Locale.ROOT));
            providerId = alias[0];
            modelId = alias[1];
        } else {
            // 简写：按前缀推断 provider
            modelId = target;
            String inferred = ProviderInference.inferProvider(modelId);
            if (inferred == null && !SUPPORTED_MODELS.contains(modelId)) {
                System.out.println(
                        "[未知 model: " + modelId + "] 支持: "
                                + String.join(", ", SUPPORTED_MODELS)
                                + "，或用 <provider>/<model> 指定 provider");
                return;
            }
            providerId = inferred != null ? inferred : defaultProvider;
        }

        if (onSelection != null) {
            onSelection.accept(providerId, modelId);
        } else if (onModel != null) {
            onModel.accept(modelId);
        }
        System.out.println("[/model] 切换到 " + providerId + "/" + modelId);
    }

    /**
     * /effort <low|medium|high> 处理（add-models-dropdown-v0）。
     *
     * <p>无参数时列出可选档位；非法档位报错不切换；onEffort 为 null 时只显示信息不真正切换。
     */
    private void doEffort(String trimmed) {
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("可用 effort: " + String.join(", ", SUPPORTED_EFFORTS));
            return;
        }
        String target = parts[1].trim().toLowerCase();
        if (!SUPPORTED_EFFORTS.contains(target)) {
            System.out.println(
                    "[思考强度必须是 " + String.join(" / ", SUPPORTED_EFFORTS) + ", 当前未变]");
            return;
        }
        if (onEffort == null) {
            System.out.println("[/effort] 未启用（ChatCommand 未注入 onEffort 回调）");
            return;
        }
        onEffort.accept(target);
        System.out.println("[/effort] 已切换思考强度为 " + target);
    }

    /**
     * /worktree 处理：create [branch] / list / remove [name]。
     *
     * @param trimmed 完整输入（已 trim）
     */
    private void doWorktree(String trimmed) {
        if (worktreeManager == null) {
            System.out.println("[/worktree] 未启用（ChatCommand 未注入 WorktreeManager）");
            return;
        }
        String[] parts = trimmed.split("\\s+", 3);
        String op = parts.length >= 2 ? parts[1] : "list";
        switch (op) {
            case "create" -> {
                String branch = parts.length >= 3 ? parts[2].trim() : null;
                String name = "wt-" + java.util.UUID.randomUUID().toString().substring(0, 8);
                java.nio.file.Path path = worktreeManager.create(name, branch);
                if (path != null) {
                    System.out.println("[/worktree] 已创建: " + path + "（分支 " + (branch != null ? branch : "默认") + "）");
                } else {
                    System.out.println("[/worktree] 创建失败（非 git 仓库？请用 /worktree list 检查）");
                }
            }
            case "remove" -> {
                String name = parts.length >= 3 ? parts[2].trim() : null;
                if (name == null) {
                    System.out.println("[/worktree remove] 用法: /worktree remove <name>");
                } else if (worktreeManager.remove(name)) {
                    System.out.println("[/worktree] 已移除: " + name);
                } else {
                    System.out.println("[/worktree] 移除失败: " + name);
                }
            }
            default -> {
                var list = worktreeManager.list();
                if (list.isEmpty()) {
                    System.out.println("[/worktree list] 无 worktree（或当前目录非 git 仓库）");
                } else {
                    System.out.println("[/worktree list]");
                    for (var info : list) System.out.println("  " + info.path() + "  (" + info.branch() + ")");
                }
            }
        }
    }

    /** 打印可用 slash 命令列表到 stdout */
    private void printHelp() {
        System.out.println("可用命令:");
        for (String c : COMMANDS) System.out.println("  " + c);
        // add-models-dropdown-v0：help 增加 /effort 提示
        System.out.println("提示：/model 切换模型；/effort 切换思考强度 (low/medium/high)");
    }

    /**
     * 打印当前会话统计（消息数 + token + 估算费用）到 stdout。
     *
     * @param hist 当前消息历史
     * @param prompt 累计 prompt token
     * @param completion 累计 completion token
     * @param model 当前模型名
     */
    private void printHistory(MessageHistory hist, int prompt, int completion, String model) {
        int cost = estimateCost(prompt, completion, model);
        System.out.println(
                "消息数: "
                        + hist.size()
                        + " | 累计 token: "
                        + prompt
                        + " in / "
                        + completion
                        + " out"
                        + " | 估算费用: ¥"
                        + cost);
    }

    /**
     * 执行 /resume：从 {@code sessionsDir} 加载最近 session，调 {@code onResume} 回调。 始终调回调（无历史时传空 list），便于调用方统一处理 UI 提示。
     */
    private void doResume(Path sessionsDir, Consumer<SessionResumeLoader.ResumeResult> onResume) {
        if (onResume == null) {
            System.out.println("[/resume] 未启用（ChatCommand 未注入 onResume 回调）");
            return;
        }
        if (sessionsDir == null) {
            System.out.println("[/resume] sessions 目录未配置");
            onResume.accept(new SessionResumeLoader.ResumeResult(List.of(), 0, 0));
            return;
        }
        SessionResumeLoader.ResumeResult result = SessionResumeLoader.load(sessionsDir);
        onResume.accept(result);
        if (result.messages().isEmpty()) {
            System.out.println("[/resume] 无历史会话");
        } else {
            System.out.println("[/resume] 已恢复 " + result.messages().size() + " 条消息");
        }
    }

    /**
     * 估算累计费用（v0.2 改读注入的 cost 配置，不再硬编码）。
     *
     * @param prompt 累计 prompt token
     * @param completion 累计 completion token
     * @param model 模型名（v0.2 暂未用，预留 per-model 定价）
     * @return 估算费用（元，保留两位小数）
     */
    public int estimateCost(int prompt, int completion, String model) {
        double p = prompt / 1_000_000.0 * cost.inputPerMTokens();
        double c = completion / 1_000_000.0 * cost.outputPerMTokens();
        return (int) Math.round((p + c) * 100) / 100;
    }

    /**
     * 列出与前缀匹配的所有 slash 命令（用于 REPL Tab 补全）。
     *
     * @param prefix 前缀字符串
     * @return 匹配的命令列表
     */
    public List<String> complete(String prefix) {
        return COMMANDS.stream().filter(c -> c.startsWith(prefix)).toList();
    }
}