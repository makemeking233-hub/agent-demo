package com.example.agent.provider;

import java.util.Map;

public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {}