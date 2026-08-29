package com.example.agent.cli;

import java.util.List;

import org.jline.reader.Completer;
import org.jline.reader.impl.completer.StringsCompleter;

/**
 * JLine3 命令补全（/help /clear /quit /history）。
 */
public class Completion {
    /**
     * 构建 slash 命令补全器。
     *
     * @param commands slash 命令列表（如 {@code ["/help","/clear","/quit","/history"]}）
     * @return JLine3 Completer 实例
     */
    public static Completer build(List<String> commands) {
        return new StringsCompleter(commands);
    }
}
