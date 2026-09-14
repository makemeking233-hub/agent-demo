package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.api.dto.VoiceCorrectionRequest;
import com.example.agent.web.api.dto.VoiceCorrectionResponse;
import com.example.agent.web.api.voice.VoiceCorrectionService;
import com.example.agent.web.api.voice.VoiceRateLimitException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** improve-voice-accuracy T7.4/T7.5：Controller 单测覆盖正常/缺 sessionId/429。 */
class VoiceCorrectionControllerTest {

    /** 成功 stub。 */
    private static VoiceCorrectionService okService() {
        return req -> new VoiceCorrectionResponse("现在我明白了", false, 120L);
    }

    /** 限流 stub：每次调用都抛。 */
    private static VoiceCorrectionService rateLimitedService() {
        return req -> {
            throw new VoiceRateLimitException("voice-correction 限流");
        };
    }

    @Test
    void returnsOkWithCorrectedText() {
        VoiceCorrectionController c = new VoiceCorrectionController(okService());
        VoiceCorrectionRequest req = new VoiceCorrectionRequest("邪念眼角舍小", "s1", List.of());
        ResponseEntity<?> resp = c.correct(req).block();
        assertThat(resp).isNotNull();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        VoiceCorrectionResponse body = (VoiceCorrectionResponse) resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.corrected()).isEqualTo("现在我明白了");
    }

    @Test
    void returns400WhenRawTextBlank() {
        VoiceCorrectionController c = new VoiceCorrectionController(okService());
        VoiceCorrectionRequest req = new VoiceCorrectionRequest("", "s1", List.of());
        ResponseEntity<?> resp = c.correct(req).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("raw_text_empty");
    }

    @Test
    void returns400WhenSessionIdBlank() {
        VoiceCorrectionController c = new VoiceCorrectionController(okService());
        VoiceCorrectionRequest req = new VoiceCorrectionRequest("你好", "", List.of());
        ResponseEntity<?> resp = c.correct(req).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("session_id_empty");
    }

    @Test
    void returns429WhenServiceThrowsRateLimit() {
        VoiceCorrectionController c = new VoiceCorrectionController(rateLimitedService());
        VoiceCorrectionRequest req = new VoiceCorrectionRequest("你好", "s1", List.of());
        ResponseEntity<?> resp = c.correct(req).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getBody()).extracting("error").isEqualTo("rate_limited");
    }

    @Test
    void returnsOkWithNullCorrectedWhenServiceDegrades() {
        // 模拟 service 内部降级（DeepSeek 失败）
        VoiceCorrectionService degraded = req -> new VoiceCorrectionResponse(null, false, 1500L);
        VoiceCorrectionController c = new VoiceCorrectionController(degraded);
        VoiceCorrectionRequest req = new VoiceCorrectionRequest("你好", "s1", List.of());
        ResponseEntity<?> resp = c.correct(req).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        VoiceCorrectionResponse body = (VoiceCorrectionResponse) resp.getBody();
        assertThat(body.corrected()).isNull();
    }
}