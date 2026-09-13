package com.example.agent.web.api;

import com.example.agent.session.SessionDiagnostics;
import com.example.agent.web.api.dto.DiagnosticsDto;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Path;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 诊断端点（improve-failure-observability）。
 *
 * <p>{@code GET /api/diagnostics[?workspace=]} 返回会话记录完整性报告：哪些会话存在「有
 * {@code tool_calls} 无 {@code tool_result}」的悬挂记录、缺哪些 {@code toolCallId}，以及运行时日志根
 * 目录。这类记录会让该会话此后每一轮都被上游 400，而此前只能靠手写脚本扫 JSONL 才能发现。
 */
@RestController
@RequestMapping("/api/diagnostics")
@Profile("web")
public class DiagnosticsController {

    private final WebAgentRuntime runtime;

    public DiagnosticsController(WebAgentRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 扫描会话存档的配对不变式并回报。
     *
     * @param workspace 归属工作区（可选，缺省为默认工作区）
     * @return 诊断报告
     */
    @GetMapping
    public DiagnosticsDto get(
            @RequestParam(name = "workspace", required = false) String workspace) {
        Path sessionsDir = runtime.sessionsDirFor(workspace);
        Path logsDir =
                Path.of(
                        System.getProperty("user.home"),
                        ".agent-demo",
                        "logs");
        return DiagnosticsDto.from(SessionDiagnostics.scan(sessionsDir, logsDir));
    }
}
