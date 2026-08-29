package com.example.agent.cli;

import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.session.SessionStore;

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
            List.of("/help", "/clear", "/quit", "/history", "/resume");

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
                input, hist, totalPromptTokens, totalCompletionTokens, model, onClear, null, null);
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
            default -> System.out.println("[未知命令] 输入 /help 查看可用命令");
        }
        return true;
    }

    /** 打印可用 slash 命令列表到 stdout */
    private void printHelp() {
        System.out.println("可用命令:");
        for (String c : COMMANDS) System.out.println("  " + c);
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
    private void doResume(Path sessionsDir, Consumer<List<Message>> onResume) {
        if (onResume == null) {
            System.out.println("[/resume] 未启用（ChatCommand 未注入 onResume 回调）");
            return;
        }
        if (sessionsDir == null) {
            System.out.println("[/resume] sessions 目录未配置");
            onResume.accept(List.of());
            return;
        }
        List<com.example.agent.session.SessionEntry> entries = SessionStore.loadLatest(sessionsDir);
        List<Message> messages =
                entries.stream()
                        .map(
                                e -> {
                                    return switch (e.type()) {
                                        case "user" -> (Message) new Message.User(e.content());
                                        case "assistant" ->
                                                new Message.Assistant(
                                                        e.content(),
                                                        java.util.List.of());
                                        case "tool_result" ->
                                                new Message.ToolResult(
                                                        "",
                                                        e.content(),
                                                        false);
                                        case "system" -> new Message.System(e.content());
                                        default -> new Message.User(e.content());
                                    };
                                })
                        .toList();
        onResume.accept(messages);
        if (messages.isEmpty()) {
            System.out.println("[/resume] 无历史会话");
        } else {
            System.out.println("[/resume] 已恢复 " + messages.size() + " 条消息");
        }
    }

    /**
     * DeepSeek-chat 定价：输入 2 元/M tokens，输出 8 元/M tokens（占位，v0.2 读 config）。
     *
     * @param prompt 累计 prompt token
     * @param completion 累计 completion token
     * @param model 模型名（暂未用，预留 per-model 定价）
     * @return 估算费用（元，保留两位小数）
     */
    public int estimateCost(int prompt, int completion, String model) {
        double p = prompt / 1_000_000.0 * 2.0;
        double c = completion / 1_000_000.0 * 8.0;
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