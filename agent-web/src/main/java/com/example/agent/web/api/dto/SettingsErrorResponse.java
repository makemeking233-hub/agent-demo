package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SettingsErrorResponse(String error, String path, Long revision) {
    public static SettingsErrorResponse of(String error) {
        return new SettingsErrorResponse(error, null, null);
    }

    public static SettingsErrorResponse ofPath(String error, String path) {
        return new SettingsErrorResponse(error, path, null);
    }
}
