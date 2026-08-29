package com.example.agent.web.api.dto;

public record AbortResponse(boolean aborted, String reason) {
    public static AbortResponse ofAborted() {
        return new AbortResponse(true, null);
    }
    public static AbortResponse ofAlreadyStopped() {
        return new AbortResponse(false, "already_stopped");
    }
}
