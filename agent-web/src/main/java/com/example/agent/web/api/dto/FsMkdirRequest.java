package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/fs/mkdir 请求体 (add-workspace-picker-modal). */
public record FsMkdirRequest(@JsonProperty("path") String path) {}
