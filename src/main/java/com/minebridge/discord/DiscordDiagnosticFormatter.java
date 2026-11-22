package com.minebridge.discord;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;

/**
 * Shared diagnostic and failure message formatter for Discord bot and webhook operations.
 */
public final class DiscordDiagnosticFormatter {

    private DiscordDiagnosticFormatter() {
    }

    public static String simpleTypeName(Throwable throwable) {
        if (throwable == null) {
            return "UnknownError";
        }
        String simpleName = throwable.getClass().getSimpleName();
        return simpleName.isEmpty() ? throwable.getClass().getName() : simpleName;
    }

    public static String formatFailureDiagnostic(String baseMessage, Throwable error) {
        String prefix = baseMessage == null || baseMessage.isBlank()
                ? "Discord operation failed"
                : baseMessage.endsWith(".")
                        ? baseMessage.substring(0, baseMessage.length() - 1)
                        : baseMessage;
        if (error == null) {
            return prefix + " (UnknownError).";
        }

        List<String> details = new ArrayList<>();
        details.add(simpleTypeName(error));
        if (error instanceof ErrorResponseException apiError) {
            details.add("code=" + apiError.getErrorCode());
            Response response = apiError.getResponse();
            if (response != null && response.code > 0) {
                details.add("HTTP " + response.code);
            }
            ErrorResponse errorResponse = apiError.getErrorResponse();
            if (errorResponse != null && errorResponse.getMeaning() != null
                    && !errorResponse.getMeaning().isBlank()) {
                details.add(errorResponse.getMeaning());
            }
        } else if (error instanceof PermissionException permissionError) {
            Permission permission = permissionError.getPermission();
            if (permission != null) {
                details.add("missing permission=" + permission.getName());
            }
        } else if (error instanceof RateLimitedException rateLimited) {
            details.add("retryAfterMs=" + rateLimited.getRetryAfter());
        } else if (error.getCause() != null && error.getCause() != error) {
            details.add("cause=" + simpleTypeName(error.getCause()));
        }
        return prefix + " (" + String.join(", ", details) + ").";
    }

    public static String formatGatewayDisconnectDiagnostic(CloseCode closeCode, boolean closedByServer) {
        if (closeCode == null) {
            return "Discord gateway disconnected; JDA will attempt to resume or reconnect.";
        }
        String origin = closedByServer ? "server" : "client";
        if (!closeCode.isReconnect()) {
            return "Discord gateway disconnected by " + origin + " with non-reconnectable close code "
                    + closeCode.getCode() + " (" + closeCode.getMeaning()
                    + "). Check bot token and configured gateway intents.";
        }
        return "Discord gateway disconnected by " + origin + " (code "
                + closeCode.getCode() + ": " + closeCode.getMeaning()
                + "); JDA will attempt to resume or reconnect.";
    }

    public static String formatGatewayShutdownDiagnostic(CloseCode closeCode, int code) {
        if (closeCode != null) {
            return "Discord gateway shut down (code " + closeCode.getCode()
                    + ": " + closeCode.getMeaning() + ").";
        }
        return "Discord gateway shut down (code " + code + ").";
    }

    public static String formatRetry(String reason, long backoffMs, int attempt, int maxRetries) {
        return reason + "; retrying in " + backoffMs + "ms (attempt " + attempt + "/" + maxRetries + ").";
    }

    public static String formatMaxRetriesExceeded(String reason, int maxRetries) {
        return reason + "; exceeded " + maxRetries + " attempts, message dropped.";
    }
}
