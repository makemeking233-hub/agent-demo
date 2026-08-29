package com.example.agent.web.api.dto;

/** POST /api/chat/send body (spec §Requirement: Chat Send / Scenario: Valid message starts turn). */
public record SendRequest(String content, String sessionId) {}