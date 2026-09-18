package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.agent.web.api.catalog.ModelCatalog;
import com.example.agent.web.api.catalog.ProviderCatalogProperties;
import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.api.dto.SendRequest;
import com.example.agent.web.api.dto.SendResponse;
import com.example.agent.web.stream.ChatStreamService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Sinks;

/**
 * 模型解析与非法模型拒绝（fix-stale-model-fallback T3）。
 *
 * <p>不启动 Spring 上下文：直接 new {@link ChatController}，mock {@link ChatStreamService} +
 * {@link Environment}，目录用固定桩。
 *
 * <p>本测试锁死三条行为，其中第三条正是被修复的缺陷：
 *
 * <ol>
 *   <li>未指定 model → 用配置的 default-model，且该值必须真的在目录里；
 *   <li>合法 model → 原样透传，响应回显与服务端实际使用值一致；
 *   <li>非法 model（如已停用的 {@code deepseek-chat}）→ 400，且**不创建任何流**、
 *       不把非法 id 透传给下游。
 * </ol>
 *
 * <p>修复前的行为是：非法 model 被静默兜回 `deepseek-chat`（同样非法），
 * 最终原样发给上游 —— 这就是本次要根除的那条路径。
 */
class ChatControllerModelResolutionTest {

    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    private ChatStreamService streams;
    private Environment env;
    private ChatController controller;
    /** 捕获 streams.create(...) 实际收到的 model（第 2 个参数） */
    private final AtomicReference<String> capturedModel = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        streams = mock(ChatStreamService.class);
        env = mock(Environment.class);
        when(env.getProperty("DEEPSEEK_API_KEY")).thenReturn("sk-test-fake");
        when(streams.workspaceExists(any())).thenReturn(true);
        when(streams.start(any(), any())).thenReturn(true);
        when(streams.create(any(), any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            capturedModel.set(inv.getArgument(1));
                            return stubActiveStream(inv.getArgument(0), inv.getArgument(1));
                        });

        controller = new ChatController(streams, env, catalog(), props());
    }

    private static ModelCatalog catalog() {
        return new ModelCatalog(List.of(new ProviderGroup(
                "deepseek",
                "DeepSeek",
                List.of(
                        new ModelEntry(DEFAULT_MODEL, "DeepSeek-V4-Flash", false, List.of()),
                        new ModelEntry("deepseek-reasoner", "DeepSeek Reasoner", true, List.of()),
                        new ModelEntry("deepseek-v4-pro", "DeepSeek-V4-Pro", true, List.of())))));
    }

    private static ProviderCatalogProperties props() {
        return new ProviderCatalogProperties(
                catalog().providers(), "deepseek", DEFAULT_MODEL);
    }

    private static ChatStreamService.ActiveStream stubActiveStream(String sessionId, String model) {
        return new ChatStreamService.ActiveStream(
                "stream-1",
                sessionId,
                model,
                System.currentTimeMillis(),
                Sinks.many().replay().all(),
                null,
                null,
                new AtomicBoolean(false),
                null);
    }

    private ResponseEntity<?> send(String model) {
        return (ResponseEntity<?>) controller
                .send(new SendRequest("hi", "sess-1", "read_only", null, model, null))
                .block();
    }

    // ----- 未指定 model -----

    @Test
    void nullModelFallsBackToConfiguredDefault() {
        ResponseEntity<?> resp = send(null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(((SendResponse) resp.getBody()).model()).isEqualTo(DEFAULT_MODEL);
        assertThat(capturedModel.get()).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void blankModelFallsBackToConfiguredDefault() {
        ResponseEntity<?> resp = send("   ");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(((SendResponse) resp.getBody()).model()).isEqualTo(DEFAULT_MODEL);
        assertThat(capturedModel.get()).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void defaultModelIsAlwaysALegalCatalogId() {
        // 兜底值不能是目录外的 id —— 否则等于把非法 id 送给上游（修复前的缺陷形态）
        send(null);
        assertThat(catalog().modelById(capturedModel.get())).isPresent();
    }

    // ----- 合法 model -----

    @Test
    void legalModelIsPassedThroughUnchanged() {
        ResponseEntity<?> resp = send("deepseek-reasoner");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(((SendResponse) resp.getBody()).model()).isEqualTo("deepseek-reasoner");
        assertThat(capturedModel.get()).isEqualTo("deepseek-reasoner");
    }

    @Test
    void responseEchoesTheModelActuallyUsed() {
        ResponseEntity<?> resp = send("deepseek-v4-pro");
        assertThat(((SendResponse) resp.getBody()).model()).isEqualTo(capturedModel.get());
    }

    // ----- 非法 model：fail-closed -----

    @Test
    void retiredDeepseekChatIsRejectedWith400() {
        ResponseEntity<?> resp = send("deepseek-chat");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("error", "invalid_model");
        assertThat(body).containsEntry("requested", "deepseek-chat");
        assertThat(body).doesNotContainKey("stream_id");
    }

    @Test
    void invalidModelErrorListsAllLegalIds() {
        ResponseEntity<?> resp = send("deepseek-chat");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        // 顺序即 catalog 中 provider/model 的书写顺序（ModelCatalog.modelIds 保证）
        assertThat(body.get("supported"))
                .isEqualTo(List.of(DEFAULT_MODEL, "deepseek-reasoner", "deepseek-v4-pro"));
    }

    @Test
    void invalidModelDoesNotCreateAnyStream() {
        send("deepseek-chat");
        verify(streams, never()).create(any(), any(), any(), any(), any());
        verify(streams, never()).start(any(), any());
    }

    @Test
    void unknownModelIsAlsoRejectedNotSilentlyRerouted() {
        ResponseEntity<?> resp = send("gpt-4o");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verify(streams, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void blankRequestIsStillCheckedBeforeModelResolution() {
        // content 为空时先于 model 解析返回 400 content_empty（保持既有优先级）
        ResponseEntity<?> resp = (ResponseEntity<?>) controller
                .send(new SendRequest("   ", "sess-1", "read_only", null, "deepseek-chat", null))
                .block();
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("error", "content_empty");
    }
}
