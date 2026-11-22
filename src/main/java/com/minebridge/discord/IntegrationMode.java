package com.minebridge.discord;

import java.util.Locale;

public enum IntegrationMode {
    BOT,
    WEBHOOK,
    HYBRID;

    public static IntegrationMode fromString(String value) {
        return fromString(value, BOT);
    }

    public static IntegrationMode fromString(String value, IntegrationMode defaultMode) {
        if (value == null || value.isBlank()) {
            return defaultMode;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return IntegrationMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return defaultMode;
        }
    }
}
