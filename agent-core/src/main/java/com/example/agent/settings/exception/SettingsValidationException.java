package com.example.agent.settings.exception;

/** PATCH 值非法 → 400 */
public class SettingsValidationException extends RuntimeException {
    public SettingsValidationException(String message) {
        super(message);
    }
}
