package com.example.agent.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogger;
import com.example.agent.provider.deepseek.DeepSeekProvider;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.testutil.SessionEventAssertions;
import com.example.agent.tools.ToolRegistry;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 日志驱动 E2E（observability 设计 D5）：真实 SessionLogger + AgentLoop 跑一轮含
 * 工具调用的对话，断言 session.jsonl 事件类型序列与 golden 一致（归一化后）。
 */
class ObservabilityE2ETest extends E2ETestBase {

    @TempDir Path tmp;

    private void stubRoundTrip() throws Exception {
        // 第一轮：tool_call（读文件）
        wireMock.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario("round")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                resourceBytes(
                                                        "e2e/deepseek-stream-tool-call.txt")))
                        .willSetStateTo("done"));
        // 第二轮：纯文本回复
        wireMock.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario("round")
                        .whenScenarioStateIs("done")
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                resourceBytes("e2e/deepseek-stream-hello.txt"))));
    }

    @Test
    void goldenEventSequenceMatchesWithToolCall() throws Exception {
        stubRoundTrip();

        AgentConfig.Logging logging =
                new AgentConfig.Logging(true, tmp.toString(), 100, 2000, 30, 50);
        SessionLogger logger = new SessionLogger(logging, "e2e-session");

        ToolRegistry tools = new ToolRegistry();
        tools.register(new com.example.agent.tools.file.ReadFileTool());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        new DeepSeekProvider("test-key", deepseekBaseUrl()),
                        tools,
                        hist,
                        new StreamingPrinter(),
                        10,
                        "deepseek-chat",
                        tmp,
                        "system",
                        logger,
                        null);

        loop.processTurn(new Message.User("读一下 a.txt")).block();
        logger.close();

        // 1) 事件类型序列 == golden（逐行对比）
        List<Map<String, Object>> events =
                SessionEventAssertions.readEvents(logger.sessionDir().resolve("session.jsonl"));
        List<String> actualTypes = SessionEventAssertions.typeSequence(events);
        List<String> golden =
                new String(
                                resourceBytes("e2e/events/read-file-round.txt"),
                                java.nio.charset.StandardCharsets.UTF_8)
                        .lines()
                        .filter(l -> !l.isBlank())
                        .toList();
        assertEquals(golden, actualTypes, "事件类型序列应匹配 golden");

        // 2) context/snapshot 记录工具列表（首轮 + 工具后续推各一次）
        List<Map<String, Object>> snapshots =
                SessionEventAssertions.byType(events, "context/snapshot");
        assertEquals(2, snapshots.size(), "首轮与工具后续推各产生一次快照");
        assertTrue(
                String.valueOf(snapshots.get(0).get("toolNames")).contains("ReadFile"),
                "快照 toolNames 应含 ReadFile");
    }
}
