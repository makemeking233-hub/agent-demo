package com.example.agent.provider;

import com.example.agent.agent.Message;

import java.util.List;
import java.util.Map;

public record ChatRequest(
    String model,
    String systemPrompt,
    List<Message> messages,
    List<ToolSpec> tools,
    Double temperature,
    Integer maxTokens,
    Map<String, Object> extra
) {}