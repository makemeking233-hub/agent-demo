package com.example.agent.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmRetryTest {

  @Test
  void retriesOnIoException() {
    AtomicInteger attempts = new AtomicInteger();
    Mono<String> source =
        Mono.fromCallable(
            () -> {
              if (attempts.incrementAndGet() < 3) throw new IOException("network");
              return "ok";
            });
    StepVerifier.create(LlmRetry.retryOnTransient(source)).expectNext("ok").verifyComplete();
    assertEquals(3, attempts.get());
  }

  @Test
  void classifies5xxAsTransient() {
    var ex = WebClientResponseException.create(503, "Service Unavailable", null, null, null);
    assertTrue(LlmRetry.isTransientError(ex));
  }

  @Test
  void classifies401AsNonTransient() {
    var ex = WebClientResponseException.create(401, "Unauthorized", null, null, null);
    assertFalse(LlmRetry.isTransientError(ex));
  }

  @Test
  void classifiesIoExceptionAsTransient() {
    assertTrue(LlmRetry.isTransientError(new IOException("disk error")));
  }
}
