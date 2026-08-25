package com.example.agent.provider;

import reactor.core.publisher.Flux;

public interface LlmProvider {
    String name();
    Flux<StreamChunk> streamChat(ChatRequest request);
    int contextWindow();
    int maxOutputTokens();
}