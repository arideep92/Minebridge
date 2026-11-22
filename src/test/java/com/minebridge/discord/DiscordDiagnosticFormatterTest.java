package com.minebridge.discord;

import net.dv8tion.jda.api.requests.CloseCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DiscordDiagnosticFormatterTest {

    @Test
    void formatsSimpleTypeName() {
        assertEquals("UnknownError", DiscordDiagnosticFormatter.simpleTypeName(null));
        assertEquals("IOException", DiscordDiagnosticFormatter.simpleTypeName(new IOException("disk full")));
        assertEquals("RuntimeException", DiscordDiagnosticFormatter.simpleTypeName(new RuntimeException()));
    }

    @Test
    void formatsNullErrorDiagnostic() {
        String result = DiscordDiagnosticFormatter.formatFailureDiagnostic("Failed to deliver", null);
        assertEquals("Failed to deliver (UnknownError).", result);

        String resultWithoutPeriod = DiscordDiagnosticFormatter.formatFailureDiagnostic("Failed to deliver.", null);
        assertEquals("Failed to deliver (UnknownError).", resultWithoutPeriod);
    }

    @Test
    void formatsStandardExceptionDiagnostic() {
        IOException ex = new IOException("connection reset");
        String result = DiscordDiagnosticFormatter.formatFailureDiagnostic("Webhook send failed", ex);
        assertEquals("Webhook send failed (IOException).", result);
    }

    @Test
    void formatsExceptionWithCauseDiagnostic() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid json");
        RuntimeException wrapper = new RuntimeException("dispatch failed", cause);
        String result = DiscordDiagnosticFormatter.formatFailureDiagnostic("Dispatch failed", wrapper);
        assertTrue(result.contains("RuntimeException"));
        assertTrue(result.contains("cause=IllegalArgumentException"));
    }

    @Test
    void formatsGatewayDisconnectAndShutdown() {
        String nullDisconnect = DiscordDiagnosticFormatter.formatGatewayDisconnectDiagnostic(null, false);
        assertEquals("Discord gateway disconnected; JDA will attempt to resume or reconnect.", nullDisconnect);

        String reconnectable = DiscordDiagnosticFormatter.formatGatewayDisconnectDiagnostic(CloseCode.RECONNECT, true);
        assertTrue(reconnectable.contains("server"));
        assertTrue(reconnectable.contains("JDA will attempt to resume or reconnect"));

        String nonReconnectable = DiscordDiagnosticFormatter.formatGatewayDisconnectDiagnostic(CloseCode.AUTHENTICATION_FAILED, false);
        assertTrue(nonReconnectable.contains("client"));
        assertTrue(nonReconnectable.contains("non-reconnectable close code 4004"));

        String nullShutdown = DiscordDiagnosticFormatter.formatGatewayShutdownDiagnostic(null, 4999);
        assertEquals("Discord gateway shut down (code 4999).", nullShutdown);

        String codeShutdown = DiscordDiagnosticFormatter.formatGatewayShutdownDiagnostic(CloseCode.DISALLOWED_INTENTS, 4014);
        assertTrue(codeShutdown.contains("4014"));
        assertTrue(codeShutdown.contains(CloseCode.DISALLOWED_INTENTS.getMeaning()));
    }

    @Test
    void formatsRetryAndMaxRetries() {
        String retry = DiscordDiagnosticFormatter.formatRetry("HTTP 502", 1000L, 2, 5);
        assertEquals("HTTP 502; retrying in 1000ms (attempt 2/5).", retry);

        String maxRetries = DiscordDiagnosticFormatter.formatMaxRetriesExceeded("HTTP 502", 5);
        assertEquals("HTTP 502; exceeded 5 attempts, message dropped.", maxRetries);
    }
}
