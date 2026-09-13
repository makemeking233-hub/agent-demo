package com.example.agent.cli;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.session.SessionResumeLoader;
import com.example.agent.session.SessionStore;
import com.example.agent.worktree.WorktreeManager;

import java.nio.file.Path;
import java.util.List;
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

    /** v0.2 支持的 model 列表（DeepSeek 系） */
    private static final List<String> SUPPORTED_MODELS =
            List.of("deepseek-chat", "deepseek-reasoner");

    /** add-models-dropdown-v0：合法的 reasoningEffort 白名单 */
    private static final List<String> SUPPORTED_EFFORTS = List.of("low", "medium", "high");

    /** 成本配置（v0.2 从 AgentConfig.cost 注入；null 时用 DeepSeek-chat 默认 2/8） */
    private AgentConfig.Cost cost = new AgentConfig.Cost(2.0, 8.0, 4.0, 5.0);

    /** Worktree 管理器（/worktree 用；null 时 /worktree 退化为提示信息） */
    private WorktreeManager worktreeManager;

    /** add-models-dropdown-v0：/effort 回调（ChatCommand 启动时注入；null 时 /effort 退化为提示信息） */
    private Consumer<String> onEffort;

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
     * @param trimmed 完整输入（已 trim）
     * @param currentModel 当前 model（用于无参数时显示）
     * @param onModel setter 回调（null 时只 list 不调 setter）
     */
    private void doModel(String trimmed, String currentModel, Consumer<String> onModel) {
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            // /model 无参数：列出当前 + 支持
            System.out.println("当前 model: " + currentModel);
            System.out.println("支持: " + String.join(", ", SUPPORTED_MODELS));
            return;
        }
        String target = parts[1].trim();
        if (!SUPPORTED_MODELS.contains(target)) {
            System.out.println(
                    "[未知 model: " + target + "] 支持: " + String.join(", ", SUPPORTED_MODELS));
            return;
        }
        if (onModel != null) onModel.accept(target);
        System.out.println("[/model] 切换到 " + target);
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