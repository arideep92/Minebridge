package com.minebridge.discord;

import com.minebridge.command.MineBridgeStatus;
import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Dedicated service for Discord Webhook Mode (Issue #24).
 * Handles asynchronous, rate-limited HTTP delivery of Minecraft chat and embeds
 * via Discord webhooks without initializing JDA.
 */
public final class WebhookService {

    public static final int DEFAULT_QUEUE_CAPACITY = 100;
    public static final int MAX_RETRIES = 3;
    private static final long DEFAULT_DIAGNOSTIC_INTERVAL_NANOS = 30_000_000_000L;
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private final ConfigManager config;
    private final Call.Factory callFactory;
    private final OkHttpClient defaultClient;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final BlockingMessageQueue outgoingQueue;
    private final AtomicLong outgoingDropped = new AtomicLong(0);
    private final RateLimitedDiagnostics diagnostics =
            new RateLimitedDiagnostics(DEFAULT_DIAGNOSTIC_INTERVAL_NANOS);

    private volatile String state = "NOT_STARTED";
    private volatile boolean stopping = false;
    private volatile long rateLimitDeadlineNanos = 0L;
    private volatile RetryItem pendingRetry = null;
    private ScheduledExecutorService backgroundExecutor = null;

    record RetryItem(String payloadJson, int attemptCount) {
    }

    public WebhookService(ConfigManager config) {
        this(config, null, null, null);
    }

    public WebhookService(
            ConfigManager config,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        this(config, null, infoLogger, warningLogger);
    }

    public WebhookService(
            ConfigManager config,
            Call.Factory callFactory,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        this.config = config;
        this.infoLogger = infoLogger != null ? infoLogger : ignored -> { };
        this.warningLogger = warningLogger != null ? warningLogger : ignored -> { };
        this.outgoingQueue = new BlockingMessageQueue(DEFAULT_QUEUE_CAPACITY);

        if (callFactory == null) {
            this.defaultClient = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .readTimeout(Duration.ofSeconds(10))
                    .writeTimeout(Duration.ofSeconds(10))
                    .build();
            this.callFactory = this.defaultClient;
        } else {
            this.defaultClient = null;
            this.callFactory = callFactory;
        }
    }

    public synchronized void start() {
        if (stopping) {
            return;
        }
        state = "READY";
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            backgroundExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MineBridge-WebhookWorker");
                thread.setDaemon(true);
                return thread;
            });
            backgroundExecutor.scheduleWithFixedDelay(
                    this::processOutgoing, 250, 250, TimeUnit.MILLISECONDS);
        }
        infoLogger.accept("Discord Webhook integration initialized.");
    }

    public synchronized void stop() {
        stopping = true;
        state = "STOPPED";
        processOutgoing();
        outgoingQueue.close();
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
            try {
                backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            backgroundExecutor = null;
        }
        if (defaultClient != null) {
            defaultClient.dispatcher().executorService().shutdown();
            defaultClient.connectionPool().evictAll();
        }
        infoLogger.accept("Discord Webhook service stopped.");
    }

    public boolean send(String content) {
        ConfigManager.WebhookSettings webhook = getWebhookSettings();
        String username = webhook != null ? webhook.username() : "{player}";
        String avatarUrl = webhook != null ? webhook.avatarUrl() : "";
        if (username != null && (username.contains("{player}") || username.contains("{prefix}") || username.contains("{suffix}"))) {
            username = getServerName();
        }
        if (avatarUrl != null && (avatarUrl.contains("{player}") || avatarUrl.contains("{uuid}"))) {
            avatarUrl = "";
        }
        return send(username, avatarUrl, content);
    }

    public static String sanitizeWebhookUsername(String username) {
        if (username == null || username.isBlank()) {
            return "Minecraft";
        }
        String sanitized = username.trim()
                .replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere");
        if ("clyde".equalsIgnoreCase(sanitized)) {
            sanitized = "Player";
        }
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80).trim();
        }
        return sanitized.isEmpty() ? "Minecraft" : sanitized;
    }

    public static String sanitizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return "";
        }
        String trimmed = avatarUrl.trim();
        if ("none".equalsIgnoreCase(trimmed) || "disabled".equalsIgnoreCase(trimmed)) {
            return "";
        }
        if (trimmed.length() > 2048) {
            return "";
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "";
        }
        return trimmed;
    }

    public boolean send(String username, String avatarUrl, String content) {
        if (stopping) {
            outgoingDropped.incrementAndGet();
            return false;
        }
        String webhookUrl = getWebhookUrl();
        if (!validateDestination(webhookUrl)) {
            outgoingDropped.incrementAndGet();
            return false;
        }
        if (content == null || content.isBlank()) {
            return false;
        }

        String safeUsername = sanitizeWebhookUsername(username);
        String safeAvatar = sanitizeAvatarUrl(avatarUrl);

        List<String> segments = WebhookMessageSplitter.split(content);
        if (segments.isEmpty()) {
            return false;
        }

        boolean allEnqueued = true;
        for (String segment : segments) {
            String payload = buildPayload(
                    segment,
                    safeUsername,
                    safeAvatar,
                    List.of(),
                    getServerName(),
                    getEmbedFooterSettings());
            if (!enqueuePayload(payload)) {
                allEnqueued = false;
            }
        }
        return allEnqueued;
    }

    public boolean sendEmbed(MessageEmbed embed) {
        ConfigManager.WebhookSettings webhook = getWebhookSettings();
        String username = webhook != null ? webhook.username() : "{player}";
        String avatarUrl = webhook != null ? webhook.avatarUrl() : "";
        if (username != null && (username.contains("{player}") || username.contains("{prefix}") || username.contains("{suffix}"))) {
            username = getServerName();
        }
        if (avatarUrl != null && (avatarUrl.contains("{player}") || avatarUrl.contains("{uuid}"))) {
            avatarUrl = "";
        }
        return sendEmbed(username, avatarUrl, embed);
    }

    public boolean sendEmbed(String username, String avatarUrl, MessageEmbed embed) {
        if (stopping) {
            outgoingDropped.incrementAndGet();
            return false;
        }
        String webhookUrl = getWebhookUrl();
        if (!validateDestination(webhookUrl)) {
            outgoingDropped.incrementAndGet();
            return false;
        }
        if (embed == null) {
            return false;
        }

        String payload = buildPayload(
                null,
                username,
                avatarUrl,
                List.of(embed),
                getServerName(),
                getEmbedFooterSettings());
        return enqueuePayload(payload);
    }

    private boolean enqueuePayload(String payload) {
        boolean offered = outgoingQueue.offer(payload);
        if (!offered) {
            outgoingDropped.incrementAndGet();
            warnRateLimited("queue-full", "Discord webhook outgoing queue is full; message was dropped.");
            return false;
        }
        return true;
    }

    private boolean validateDestination(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            warnRateLimited("missing-webhook-url",
                    "Discord webhook URL is not configured; message dropped.");
            return false;
        }
        if (!ConfigManager.isValidWebhookUrl(webhookUrl)) {
            warnRateLimited("invalid-webhook-url",
                    "Discord webhook destination is invalid or unapproved; no URL or token was logged.");
            return false;
        }
        return true;
    }

    public void processOutgoing() {
        if (stopping) {
            return;
        }
        if (System.nanoTime() < rateLimitDeadlineNanos) {
            return;
        }

        RetryItem item = pendingRetry;
        if (item == null) {
            String payload = outgoingQueue.poll();
            if (payload == null) {
                return;
            }
            item = new RetryItem(payload, 1);
        }

        String webhookUrl = getWebhookUrl();
        if (!ConfigManager.isValidWebhookUrl(webhookUrl)) {
            outgoingDropped.incrementAndGet();
            pendingRetry = null;
            state = "INVALID_WEBHOOK";
            warnRateLimited("invalid-webhook-url",
                    "Discord webhook destination is invalid; message dropped without logging destination.");
            return;
        }

        RequestBody body = RequestBody.create(item.payloadJson(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build();

        try (Response response = callFactory.newCall(request).execute()) {
            int code = response.code();
            if (code >= 200 && code < 300) {
                pendingRetry = null;
                state = "READY";
                return;
            }

            String responseBody = "";
            if (response.body() != null) {
                try {
                    responseBody = response.body().string();
                } catch (IOException ignored) {
                }
            }

            if (code == 429) {
                long retryAfterMs = parseRetryAfterMs(response, responseBody);
                rateLimitDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(retryAfterMs);
                if (item.attemptCount() < MAX_RETRIES) {
                    pendingRetry = new RetryItem(item.payloadJson(), item.attemptCount() + 1);
                } else {
                    outgoingDropped.incrementAndGet();
                    pendingRetry = null;
                    warnRateLimited("webhook-rate-limited",
                            "Discord webhook rate limit exceeded max retries; message dropped (retryAfterMs="
                                    + retryAfterMs + ").");
                }
                return;
            }

            if (code == 400) {
                outgoingDropped.incrementAndGet();
                pendingRetry = null;
                warnRateLimited("webhook-bad-request",
                        "Discord rejected webhook payload (HTTP 400 Bad Request); message dropped.");
                return;
            }

            if (code == 401 || code == 404) {
                outgoingDropped.incrementAndGet();
                pendingRetry = null;
                state = "INVALID_WEBHOOK";
                warnRateLimited("webhook-unauthorized",
                        "Discord webhook destination returned HTTP " + code
                                + "; verify webhook URL exists and is active. No token was logged.");
                return;
            }

            if (code >= 500) {
                handleTemporaryFailure(item, "Discord webhook server error (HTTP " + code + ")");
                return;
            }

            outgoingDropped.incrementAndGet();
            pendingRetry = null;
            warnRateLimited("webhook-error", "Discord webhook request failed with unexpected HTTP " + code + ".");
        } catch (IOException exception) {
            handleTemporaryFailure(item, "Network error during Discord webhook request ("
                    + DiscordDiagnosticFormatter.simpleTypeName(exception) + ")");
        } catch (RuntimeException exception) {
            outgoingDropped.incrementAndGet();
            pendingRetry = null;
            warnRateLimited("webhook-exception", "Unexpected error dispatching Discord webhook ("
                    + DiscordDiagnosticFormatter.simpleTypeName(exception) + ").");
        }
    }

    private void handleTemporaryFailure(RetryItem item, String reason) {
        if (item.attemptCount() < MAX_RETRIES) {
            long backoffMs = (long) Math.pow(2, item.attemptCount() - 1) * 500L;
            rateLimitDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(backoffMs);
            pendingRetry = new RetryItem(item.payloadJson(), item.attemptCount() + 1);
            warnRateLimited("webhook-temp-failure",
                    DiscordDiagnosticFormatter.formatRetry(reason, backoffMs, item.attemptCount(), MAX_RETRIES));
        } else {
            outgoingDropped.incrementAndGet();
            pendingRetry = null;
            warnRateLimited("webhook-max-retries",
                    DiscordDiagnosticFormatter.formatMaxRetriesExceeded(reason, MAX_RETRIES));
        }
    }

    public static long parseRetryAfterMs(Response response, String responseBody) {
        long fallbackMs = 1000L;
        if (response != null) {
            String header = response.header("Retry-After");
            if (header != null && !header.isBlank()) {
                try {
                    double seconds = Double.parseDouble(header.trim());
                    return Math.max(50L, (long) Math.ceil(seconds * 1000.0));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                DataObject body = DataObject.fromJson(responseBody);
                if (body.hasKey("retry_after")) {
                    double seconds = body.getDouble("retry_after");
                    return Math.max(50L, (long) Math.ceil(seconds * 1000.0));
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackMs;
    }

    public static String buildPayload(
            String content,
            String username,
            String avatarUrl,
            List<MessageEmbed> embeds,
            String serverName,
            ConfigManager.EmbedFooterSettings footerSettings) {
        DataObject root = DataObject.empty();

        if (content != null && !content.isBlank()) {
            root.put("content", content);
        }
        if (username != null && !username.isBlank()) {
            String resolvedUsername = username;
            if (serverName != null && !serverName.isBlank()) {
                resolvedUsername = resolvedUsername.replace("{server}", serverName);
            }
            resolvedUsername = resolvedUsername
                    .replace("{prefix}", "")
                    .replace("{suffix}", "")
                    .trim();
            if (resolvedUsername.contains("{player}")) {
                resolvedUsername = resolvedUsername.replace("{player}", serverName != null && !serverName.isBlank() ? serverName : "Minecraft").trim();
            }
            root.put("username", sanitizeWebhookUsername(resolvedUsername));
        }
        String safeAvatar = sanitizeAvatarUrl(avatarUrl);
        if (!safeAvatar.isEmpty()) {
            if (safeAvatar.contains("{player}") || safeAvatar.contains("{uuid}")) {
                safeAvatar = "";
            } else {
                root.put("avatar_url", safeAvatar);
            }
        }

        if (embeds != null && !embeds.isEmpty()) {
            DataArray embedsArray = DataArray.empty();
            for (MessageEmbed embed : embeds) {
                if (embed == null) {
                    continue;
                }
                DataObject embedNode = DataObject.empty();
                if (embed.getTitle() != null && !embed.getTitle().isBlank()) {
                    embedNode.put("title", embed.getTitle());
                }
                if (embed.getDescription() != null && !embed.getDescription().isBlank()) {
                    embedNode.put("description", embed.getDescription());
                }
                if (embed.getColor() != null) {
                    embedNode.put("color", embed.getColorRaw() & 0xFFFFFF);
                }
                if (embed.getTimestamp() != null) {
                    embedNode.put("timestamp", embed.getTimestamp().toString());
                }

                if (embed.getFooter() != null && embed.getFooter().getText() != null) {
                    String footerText = embed.getFooter().getText();
                    if (serverName != null && !serverName.isBlank()) {
                        footerText = footerText.replace("{server}", serverName);
                    }
                    if (!footerText.isBlank() && !DiscordEmbedFactory.DEFAULT_FOOTER.equals(footerText) && !"MineBridge | Minecraft Events".equals(footerText)) {
                        DataObject footerNode = DataObject.empty();
                        footerNode.put("text", footerText);

                        String icon = embed.getFooter().getIconUrl();
                        if ((icon == null || icon.isBlank()) && footerSettings != null) {
                            icon = footerSettings.iconUrl();
                        }
                        if (icon != null && !icon.isBlank()) {
                            footerNode.put("icon_url", icon);
                        }
                        embedNode.put("footer", footerNode);
                    }
                } else if (footerSettings != null && !footerSettings.text().isBlank()) {
                    String footerText = footerSettings.text();
                    if (serverName != null && !serverName.isBlank()) {
                        footerText = footerText.replace("{server}", serverName);
                    }
                    if (!footerText.isBlank() && !DiscordEmbedFactory.DEFAULT_FOOTER.equals(footerText) && !"MineBridge | Minecraft Events".equals(footerText)) {
                        DataObject footerNode = DataObject.empty();
                        footerNode.put("text", footerText);
                        if (!footerSettings.iconUrl().isBlank()) {
                            footerNode.put("icon_url", footerSettings.iconUrl());
                        }
                        embedNode.put("footer", footerNode);
                    }
                }

                if (!embed.getFields().isEmpty()) {
                    DataArray fieldsArray = DataArray.empty();
                    for (MessageEmbed.Field field : embed.getFields()) {
                        DataObject fieldNode = DataObject.empty();
                        fieldNode.put("name", field.getName() != null ? field.getName() : "");
                        fieldNode.put("value", field.getValue() != null ? field.getValue() : "");
                        fieldNode.put("inline", field.isInline());
                        fieldsArray.add(fieldNode);
                    }
                    embedNode.put("fields", fieldsArray);
                }

                if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
                    DataObject authorNode = DataObject.empty();
                    authorNode.put("name", embed.getAuthor().getName());
                    if (embed.getAuthor().getUrl() != null) {
                        authorNode.put("url", embed.getAuthor().getUrl());
                    }
                    if (embed.getAuthor().getIconUrl() != null) {
                        authorNode.put("icon_url", embed.getAuthor().getIconUrl());
                    }
                    embedNode.put("author", authorNode);
                }
                embedsArray.add(embedNode);
            }
            root.put("embeds", embedsArray);
        }

        return root.toString();
    }

    private String getWebhookUrl() {
        if (config == null || config.currentSettings() == null
                || config.currentSettings().webhook() == null) {
            return "";
        }
        return config.currentSettings().webhook().url();
    }

    private ConfigManager.WebhookSettings getWebhookSettings() {
        if (config == null || config.currentSettings() == null) {
            return null;
        }
        return config.currentSettings().webhook();
    }

    private String getServerName() {
        if (config == null || config.currentSettings() == null) {
            return "Minecraft Server";
        }
        return config.currentSettings().serverName();
    }

    private ConfigManager.EmbedFooterSettings getEmbedFooterSettings() {
        if (config == null || config.currentSettings() == null) {
            return null;
        }
        return config.currentSettings().embedFooter();
    }

    public int getQueueSize() {
        return outgoingQueue.size();
    }

    public int getQueueCapacity() {
        return outgoingQueue.capacity();
    }

    public long getOutgoingDropped() {
        return outgoingDropped.get();
    }

    public void markConfigurationInvalid() {
        this.state = "INVALID_WEBHOOK";
    }

    public String getState() {
        return state;
    }

    public MineBridgeStatus getStatus(
            long uptimeMillis,
            boolean chatRelayEnabled,
            boolean joinLeaveNotificationsEnabled,
            boolean luckPermsAvailable) {
        String webhookUrl = getWebhookUrl();
        boolean validDestination = ConfigManager.isValidWebhookUrl(webhookUrl);
        String currentState = state;
        if (!validDestination && !"STOPPED".equals(currentState)) {
            currentState = "INVALID_WEBHOOK";
        }
        boolean available = validDestination && "READY".equals(currentState);
        return new MineBridgeStatus(
                IntegrationMode.WEBHOOK,
                currentState,
                available,
                outgoingQueue.size(),
                outgoingQueue.capacity(),
                uptimeMillis,
                chatRelayEnabled,
                joinLeaveNotificationsEnabled,
                outgoingDropped.get(),
                luckPermsAvailable);
    }

    public boolean isStopping() {
        return stopping;
    }

    private void warnRateLimited(String category, String message) {
        if (diagnostics.shouldEmit(category)) {
            warningLogger.accept(message);
        }
    }
}
