package com.example.agent.web.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.web.api.dto.VoiceCorrectionRequest;
import com.example.agent.web.api.dto.VoiceCorrectionResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** improve-voice-accuracy T7.4：Service 单测覆盖正常/超时/5xx/缓存命中。 */
class DeepSeekVoiceCorrectionServiceTest {

    /** 构造返回固定 TextDelta 序列的 stub provider。 */
    private static LlmProvider stubProvider(String... deltas) {
        return new LlmProvider() {
            @Override
            public String name() {
                return "stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.fromArray(deltas).map(StreamChunk.TextDelta::new);
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 慢响应 provider：每 200ms 吐一个 chunk，模拟上游慢。 */
    private static LlmProvider slowProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "slow-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.interval(Duration.ofMillis(200))
                        .map(i -> (StreamChunk) new StreamChunk.TextDelta("delta"));
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 抛异常的 provider：模拟 5xx。 */
    private static LlmProvider errorProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "error-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.error(new RuntimeException("[HTTP 502] bad gateway"));
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    @Test
    void correctsSuccessfullyWhenProviderReturnsText() {
        LlmProvider p = stubProvider("现在", "我", "明白了");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("邪念眼角舍小", "s1"));
        assertThat(resp.corrected()).isEqualTo("现在我明白了");
        assertThat(resp.cached()).isFalse();
    }

    @Test
    void degradesOnTimeout() {
        LlmProvider p = slowProvider();
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("测试文本", "s1"));
        assertThat(resp.corrected()).isNull();
        assertThat(resp.cached()).isFalse();
    }

    @Test
    void degradesOn5xx() {
        LlmProvider p = errorProvider();
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("测试文本", "s1"));
        assertThat(resp.corrected()).isNull();
        assertThat(resp.cached()).isFalse();
    }

    @Test
    void cachesCorrectedResultWithinTtl() {
        LlmProvider p = stubProvider("缓存", "命中");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionRequest r = req("同样的原始文本", "s1");
        VoiceCorrectionResponse first = svc.correct(r);
        assertThat(first.cached()).isFalse();
        assertThat(first.corrected()).isEqualTo("缓存命中");
        // 第二次同请求应命中缓存，不再走 provider
        VoiceCorrectionResponse second = svc.correct(r);
        assertThat(second.cached()).isTrue();
        assertThat(second.corrected()).isEqualTo("缓存命中");
    }

    @Test
    void rateLimitReturns429OnExceeding5PerSecond() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        String sid = "rl-session";
        // 5 次正常通过
        for (int i = 0; i < 5; i++) {
            svc.correct(req("text-" + i, sid));
        }
        // 第 6 次立即同 session → 限流
        assertThatThrownBy(() -> svc.correct(req("text-5", sid)))
                .isInstanceOf(VoiceRateLimitException.class);
    }

    @Test
    void differentSessionIdsHaveIndependentBuckets() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        // s1 用满 5 次
        for (int i = 0; i < 5; i++) {
            svc.correct(req("text-" + i, "s1"));
        }
        // s2 仍可用（独立桶）
        VoiceCorrectionResponse resp = svc.correct(req("text-s2", "s2"));
        assertThat(resp.corrected()).isEqualTo("ok");
    }

    @Test
    void rejectsBlankRawText() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(req("", "s1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSessionId() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(req("raw", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cacheKeyDiffersByRecentTurns() {
        VoiceCorrectionRequest a1 = new VoiceCorrectionRequest("你好", "s1",
                List.of(new VoiceCorrectionRequest.RecentTurn("之前用户说的话", "之前助手回复")));
        VoiceCorrectionRequest a2 = new VoiceCorrectionRequest("你好", "s1",
                List.of(new VoiceCorrectionRequest.RecentTurn("不同的历史", "不同的回复")));
        assertThat(DeepSeekVoiceCorrectionService.cacheKey(a1))
                .isNotEqualTo(DeepSeekVoiceCorrectionService.cacheKey(a2));
    }

    private static VoiceCorrectionRequest req(String raw, String sid) {
        return new VoiceCorrectionRequest(raw, sid, List.of());
    }
}