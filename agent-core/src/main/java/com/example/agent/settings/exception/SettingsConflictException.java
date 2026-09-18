package com.example.agent.settings.exception;

/** PATCH revision 冲突 → 409 */
public class SettingsConflictException extends RuntimeException {
    public SettingsConflictException(String message) {
        super(message);
    }
}
