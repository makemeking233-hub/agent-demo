package com.example.agent.web.api.dto;

/** POST /api/chat/send response (spec §Requirement: Chat Send). */
public record SendResponse(String streamId, String sessionId, String model) {}