package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ContextCompressor 可观测性：压缩成功/失败广播 system/compact 事件。
 */
class ContextCompressorObservabilityTest {

    static final class CapturingSink implements SessionLogSink {
        final List<String> compacts = new ArrayList<>();

        @Override
        public void onSystemEvent(String type, Map<String, Object> payload) {
            if ("system/compact".equals(type)) compacts.add(String.valueOf(payload));
        }
    }

    @Test
    void compactSuccessBroadcastsEvent() {
        CapturingSink sink = new CapturingSink();
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(200);
        when(provider.maxOutputTokens()).thenReturn(8);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("[摘要] 用户目标：构建脚手架。"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        for (int i = 0; i < 10; i++)
            hist.append(new Message.User("msg-" + i + " " + "很长的内容 ".repeat(50)));

        ContextCompressor comp = new ContextCompressor(provider, 0, 3, "deepseek-chat", sink);
        StepVerifier.create(comp.compactIfNeeded(hist)).expectNextCount(1).verifyComplete();

        assertEquals(1, sink.compacts.size());
        Map<String, Object> ev = parseFirst(sink.compacts.get(0));
        assertEquals(true, ev.get("success"));
        assertTrue((Integer) ev.get("beforeTokens") > 0);
        assertTrue(String.valueOf(ev.get("summary")).contains("构建脚手架"));
    }

    @Test
    void compactFailureBroadcastsEvent() {
        CapturingSink sink = new CapturingSink();
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(200);
        when(provider.maxOutputTokens()).thenReturn(8);
        when(provider.streamChat(any())).thenReturn(Flux.error(new RuntimeException("network")));

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        hist.append(new Message.User("很长的内容 ".repeat(100)));

        ContextCompressor comp = new ContextCompressor(provider, 0, 3, "deepseek-chat", sink);
        StepVerifier.create(comp.compactIfNeeded(hist)).expectError().verify();

        assertEquals(1, sink.compacts.size());
        Map<String, Object> ev = parseFirst(sink.compacts.get(0));
        assertEquals(false, ev.get("success"));
        assertEquals("RuntimeException", ev.get("errorClass"));
    }

    /** 从 payload 字符串中解析出第一条事件（测试用简化解析） */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseFirst(String payload) {
        // payload 形如 {beforeTokens=..., success=true, ...}
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("beforeTokens=(\\d+)").matcher(payload);
        int before = m.find() ? Integer.parseInt(m.group(1)) : 0;
        m = java.util.regex.Pattern.compile("success=(\\w+)").matcher(payload);
        boolean success = m.find() ? Boolean.parseBoolean(m.group(1)) : false;
        m = java.util.regex.Pattern.compile("summary=([^,}]+)").matcher(payload);
        String summary = m.find() ? m.group(1) : "";
        m = java.util.regex.Pattern.compile("errorClass=([^,}]+)").matcher(payload);
        String errorClass = m.find() ? m.group(1) : "";
        return Map.of(
                "beforeTokens", before,
                "success", success,
                "summary", summary,
                "errorClass", errorClass);
    }
}
