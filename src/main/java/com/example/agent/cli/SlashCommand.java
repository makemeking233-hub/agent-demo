package com.example.agent.cli;

import com.example.agent.agent.MessageHistory;
import com.example.agent.provider.TokenEstimator;

import java.util.List;

/**
 * Slash 命令分发（v0.1：/help /clear /quit /history）。
 *
 * <p>v0.1 简化版：{@code /history} 费用估算硬编码 DeepSeek-chat 价格（2/8 ¥/M token）。
 * v0.2 改为读 {@code AgentConfig.cost()}。
 */
public class SlashCommand {
    private static final List<String> COMMANDS = List.of("/help", "/clear", "/quit", "/history");

    public boolean dispatch(String input, MessageHistory hist, int[] totalPromptTokens,
                            int[] totalCompletionTokens, String model, Runnable onClear) {
        String trimmed = input.trim();
        if (!trimmed.startsWith("/")) return false;
        switch (trimmed) {
            case "/help" -> printHelp();
            case "/clear" -> { onClear.run(); System.out.println("[已清空会话历史]"); }
            case "/quit" -> { System.exit(0); }
            case "/history" -> printHistory(hist, totalPromptTokens[0], totalCompletionTokens[0], model);
            default -> System.out.println("[未知命令] 输入 /help 查看可用命令");
        }
        return true;
    }

    private void printHelp() {
        System.out.println("可用命令:");
        for (String c : COMMANDS) System.out.println("  " + c);
    }

    private void printHistory(MessageHistory hist, int prompt, int completion, String model) {
        int cost = estimateCost(prompt, completion, model);
        System.out.println("消息数: " + hist.size()
            + " | 累计 token: " + prompt + " in / " + completion + " out"
            + " | 估算费用: ¥" + cost);
    }

    /** DeepSeek-chat 定价：输入 2 元/M tokens，输出 8 元/M tokens（占位） */
    public int estimateCost(int prompt, int completion, String model) {
        double p = prompt / 1_000_000.0 * 2.0;
        double c = completion / 1_000_000.0 * 8.0;
        return (int) Math.round((p + c) * 100) / 100;
    }

    public List<String> complete(String prefix) {
        return COMMANDS.stream().filter(c -> c.startsWith(prefix)).toList();
    }
}