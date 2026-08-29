package com.example.agent.web.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * T5.1: Slash 命令路由 (spec §Requirement: Slash Commands).
 *
 * <p>v0.1 web 简化版: 不直接调 agent-core 的 {@code SlashCommand} (那个用 System.out / System.exit
 * 跟 web 冲突), 改返回字符串 + 标识. 前端拿到后:
 * <ul>
 *   <li>/help / /clear / /resume → 把返回的 output 渲染为 message_delta</li>
 *   <li>/quit → 关当前 session + SSE</li>
 *   <li>未知 → 400 unknown_command</li>
 * </ul>
 *
 * <p>v0.2: 真正串到 SessionStore (clear) / SessionStore.loadLatest (resume) + 状态机.
 */
@Component
@Profile("web")
public class SlashCommandRouter {

    /** 命令结果. */
    public record Result(boolean consumed, String command, String output, boolean closeStream) {}

    /** 已知命令输出表 (v0.1 静态, v0.2 接 SlashCommand 动态生成). */
    private static final Map<String, String> OUTPUTS = new LinkedHashMap<>();
    static {
        OUTPUTS.put("/help",
                "可用命令:\n  /help    显示本帮助\n  /clear   清空当前 session 的消息历史\n  /resume  恢复最近一次 session\n  /history 显示当前 session 的消息统计\n  /quit    关闭当前 session");
        OUTPUTS.put("/clear", "已清空当前会话历史");
        OUTPUTS.put("/resume", "v0.1 占位: resume 等 add-resume-command archive 完整接入 SessionStore");
        OUTPUTS.put("/history", "v0.1 占位: history 等 v0.2 接入 token 统计");
    }

    /** 返回 ResponseEntity: 200 + Result, 或 400 unknown_command. */
    public ResponseEntity<Result> route(String content) {
        if (content == null) {
            return ResponseEntity.badRequest().body(new Result(false, null, "content_empty", false));
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("/")) {
            return ResponseEntity.ok(new Result(false, null, null, false));
        }
        if ("/quit".equals(trimmed)) {
            return ResponseEntity.ok(new Result(true, "/quit", "已关闭当前 session", true));
        }
        String output = OUTPUTS.get(trimmed);
        if (output == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new Result(false, trimmed, null, false));
        }
        return ResponseEntity.ok(new Result(true, trimmed, output, false));
    }

    /** /quit handler 暴露给 Controller 关闭 SSE 流. */
    public static boolean shouldCloseStream(Result r) {
        return r != null && r.closeStream();
    }

    public static List<String> knownCommands() {
        return List.copyOf(OUTPUTS.keySet());
    }
}
