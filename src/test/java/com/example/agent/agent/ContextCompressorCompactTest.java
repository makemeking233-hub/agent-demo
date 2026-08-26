package com.example.agent.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.provider.FinishReason;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.StreamChunk;
import com.example.agent.provider.TokenEstimator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ContextCompressorCompactTest {

  @Test
  void compactCollapsesAndSummarizes() {
    LlmProvider provider = mock(LlmProvider.class);
    // 用小窗口让阈值易触发
    when(provider.contextWindow()).thenReturn(200);
    when(provider.maxOutputTokens()).thenReturn(8);
    when(provider.streamChat(any()))
        .thenReturn(
            Flux.just(
                new StreamChunk.TextDelta("[摘要] 用户目标：构建脚手架。已完成 M0-M3。"),
                new StreamChunk.Finished(FinishReason.STOP, null)));

    MessageHistory hist = new MessageHistory(new TokenEstimator());
    for (int i = 0; i < 10; i++)
      hist.append(new Message.User("msg-" + i + " " + "很长的内容 ".repeat(50)));

    ContextCompressor comp = new ContextCompressor(provider, 0, 3, "deepseek-chat");
    StepVerifier.create(comp.compactIfNeeded(hist))
        .assertNext(
            h -> {
              // 应该有 system [SUMMARY] + system 边界 + 最近几轮
              boolean hasSummary =
                  h.all().stream()
                      .anyMatch(
                          m -> m instanceof Message.System s && s.content().contains("[SUMMARY]"));
              org.junit.jupiter.api.Assertions.assertTrue(hasSummary, "应有 [SUMMARY] 系统消息");
            })
        .verifyComplete();
  }

  @Test
  void compactFailureIncrementsCounter() {
    LlmProvider provider = mock(LlmProvider.class);
    when(provider.contextWindow()).thenReturn(200);
    when(provider.maxOutputTokens()).thenReturn(8);
    // streamChat 抛错
    when(provider.streamChat(any())).thenReturn(Flux.error(new RuntimeException("network")));

    MessageHistory hist = new MessageHistory(new TokenEstimator());
    hist.append(new Message.User("很长的内容 ".repeat(100)));

    ContextCompressor comp = new ContextCompressor(provider, 0, 3, "deepseek-chat");
    StepVerifier.create(comp.compactIfNeeded(hist)).expectError().verify();
    org.junit.jupiter.api.Assertions.assertEquals(1, hist.consecutiveCompactFailures());
  }
}
