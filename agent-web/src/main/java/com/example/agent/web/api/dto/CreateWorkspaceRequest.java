package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/workspaces body（align-dsh-workspace v2）：只需 {@code path}。
 *
 * <p>name 与 title 都由后端从 {@code basename(path)} 派生（DSH 语义）。
 * 同 canonical path 重复创建返回现有 record。
 */
public record CreateWorkspaceRequest(@JsonProperty("path") String path) {}