package com.example.agent.settings.exception;

/** PATCH 路径不存在时抛 → 404 */
public class SettingsNotFoundException extends RuntimeException {
    public SettingsNotFoundException(String message) {
        super(message);
    }
}
