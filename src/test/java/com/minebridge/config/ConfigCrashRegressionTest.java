package com.minebridge.config;

import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import com.minebridge.lifecycle.LifecycleManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCrashRegressionTest {

    private static final String VALID_BOT_TOKEN = "synthetic-test-bot-token-12345";
    private static final String VALID_CHANNEL_ID = "123456789012345678";
    private static final String VALID_WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/synthetic-webhook-token";

    private static Call.Factory noopCallFactory() {
        return request -> new Call() {
            @Override public Request request() { return request; }
            @Override public Response execute() {
                return new Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(204)
                        .message("No Content")
                        .body(ResponseBody.create("", MediaType.get("application/json")))
                        .build();
            }
            @Override public void enqueue(Callback responseCallback) {}
            @Override public void cancel() {}
            @Override public boolean isExecuted() { return true; }
            @Override public boolean isCanceled() { return false; }
            @Override public Timeout timeout() { return Timeout.NONE; }
            @Override public Call clone() { return this; }
        };
    }

    private ConfigManager.Candidate parseCandidate(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            try (var stream = getClass().getResourceAsStream("/config.yml")) {
                if (stream != null) {
                    YamlConfiguration defaults = new YamlConfiguration();
                    defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    config.setDefaults(defaults);
                }
            }
            config.load(new StringReader(yaml));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ConfigManager manager = new ConfigManager(null);
        return manager.candidateFrom(config, null);
    }

    @Test
    @DisplayName("Regression: Missing bot token produces clear errors and non-null settings")
    void missingBotToken() {
        String yaml = """
                discord:
                  mode: BOT
                  bot-token: ""
                  channel-id: "%s"
                """.formatted(VALID_CHANNEL_ID);

        ConfigManager.Candidate candidate = parseCandidate(yaml);
        assertNotNull(candidate, "Candidate must not be null");
        assertFalse(candidate.valid(), "Candidate must be invalid when bot token is missing");
        assertNotNull(candidate.settings(), "Candidate settings must not be null even when invalid");
        assertEquals(IntegrationMode.BOT, candidate.settings().mode());

        List<String> errors = candidate.errors();
        assertFalse(errors.isEmpty(), "Errors must be populated");
        assertTrue(errors.stream().anyMatch(e -> e.contains("Discord bot token is missing or invalid")),
                "Error must clearly indicate missing bot token: " + errors);

        // Verify LifecycleManager handles this candidate gracefully without NPE
        List<String> warnings = new ArrayList<>();
        LifecycleManager manager = new LifecycleManager(
                new ConfigManager(null),
                () -> candidate,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                warnings::add,
                () -> 0L
        );

        assertDoesNotThrow(manager::start, "LifecycleManager.start() must not throw NPE on missing bot token");
        assertFalse(warnings.isEmpty(), "Warnings must be logged for configuration errors");
        assertEquals("INVALID_CONFIGURATION", manager.getStatus().discordState());
    }

    @Test
    @DisplayName("Regression: Missing channel ID produces clear errors and non-null settings")
    void missingChannelId() {
        String yaml = """
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: ""
                features:
                  chat-relay: true
                """.formatted(VALID_BOT_TOKEN);

        ConfigManager.Candidate candidate = parseCandidate(yaml);
        assertNotNull(candidate);
        assertFalse(candidate.valid(), "Candidate must be invalid when channel ID is missing and chat-relay is enabled");
        assertNotNull(candidate.settings(), "Candidate settings must not be null");

        List<String> errors = candidate.errors();
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Discord channel ID is missing or invalid")),
                "Error must clearly indicate missing channel ID: " + errors);

        // Verify LifecycleManager handles this candidate gracefully without NPE
        LifecycleManager manager = new LifecycleManager(
                new ConfigManager(null),
                () -> candidate,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 0L
        );

        assertDoesNotThrow(manager::start, "LifecycleManager.start() must not throw NPE on missing channel ID");
        assertEquals("INVALID_CONFIGURATION", manager.getStatus().discordState());
    }

    @Test
    @DisplayName("Regression: Invalid bot token produces clear errors and non-null settings")
    void invalidBotToken() {
        String yaml = """
                discord:
                  mode: BOT
                  bot-token: "invalid token with spaces"
                  channel-id: "%s"
                """.formatted(VALID_CHANNEL_ID);

        ConfigManager.Candidate candidate = parseCandidate(yaml);
        assertNotNull(candidate);
        assertFalse(candidate.valid(), "Candidate must be invalid with malformed bot token");
        assertNotNull(candidate.settings(), "Candidate settings must not be null");

        List<String> errors = candidate.errors();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Discord bot token is missing or invalid")),
                "Error must indicate invalid bot token: " + errors);
    }

    @Test
    @DisplayName("Regression: Invalid channel ID produces clear errors and non-null settings")
    void invalidChannelId() {
        String yaml = """
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: "not-a-valid-snowflake"
                """.formatted(VALID_BOT_TOKEN);

        ConfigManager.Candidate candidate = parseCandidate(yaml);
        assertNotNull(candidate);
        assertFalse(candidate.valid(), "Candidate must be invalid with malformed channel ID");
        assertNotNull(candidate.settings(), "Candidate settings must not be null");

        List<String> errors = candidate.errors();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Discord channel ID is missing or invalid")),
                "Error must indicate invalid channel ID: " + errors);
    }

    @Test
    @DisplayName("Regression: Null configuration candidate is handled defensively across lifecycle")
    void nullConfigurationCandidate() {
        List<String> warnings = new ArrayList<>();
        ConfigManager configManager = new ConfigManager(null);

        LifecycleManager manager = new LifecycleManager(
                configManager,
                () -> null, // Null candidate supplier
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                warnings::add,
                () -> 0L
        );

        assertDoesNotThrow(manager::start, "start() must not throw NPE when candidate is null");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("configuration is null")),
                "Warning must be logged for null candidate: " + warnings);

        AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
        assertDoesNotThrow(() -> manager.reload(reloadResult::set),
                "reload() must not throw NPE when candidate is null");
        assertNotNull(reloadResult.get());
        assertFalse(reloadResult.get().successful(), "Reload must fail when candidate is null");

        assertThrows(IllegalArgumentException.class, () -> configManager.commit(null),
                "commit(null) must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Regression: Null settings in candidate or ConfigManager are handled defensively")
    void nullSettingsHandling() {
        ConfigManager.Candidate candidateWithNullSettings = new ConfigManager.Candidate(
                null, List.of("Settings are absent due to syntax error"));

        assertFalse(candidateWithNullSettings.valid(), "Candidate with null settings must be invalid");
        assertNull(candidateWithNullSettings.settings());

        ConfigManager configManager = new ConfigManager(null);
        assertThrows(IllegalArgumentException.class, () -> configManager.commit(candidateWithNullSettings),
                "commit() with null settings must throw IllegalArgumentException");

        // Test LifecycleManager start with candidate that has null settings
        List<String> warnings = new ArrayList<>();
        LifecycleManager manager = new LifecycleManager(
                configManager,
                () -> candidateWithNullSettings,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                warnings::add,
                () -> 0L
        );

        assertDoesNotThrow(manager::start, "start() must not throw NPE when candidate.settings() is null");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Settings are absent due to syntax error")));

        // Test LifecycleManager reload with candidate that has null settings
        AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
        assertDoesNotThrow(() -> manager.reload(reloadResult::set),
                "reload() must not throw NPE when candidate.settings() is null");
        assertNotNull(reloadResult.get());
        assertFalse(reloadResult.get().successful());

        // Test ConfigManager getters when settings are null or default
        assertNotNull(configManager.mode());
        assertNotNull(configManager.getBotToken());
        assertNotNull(configManager.getChannelId());
        assertNotNull(configManager.webhook());
        assertNotNull(configManager.serverName());
        assertNotNull(configManager.embedFooter());
        assertNotNull(configManager.validationErrors());
        assertDoesNotThrow(() -> ConfigManager.validateConfiguration((ConfigManager.Settings) null));
    }

    @Test
    @DisplayName("Regression: Valid configuration for BOT mode starts successfully")
    void validBotConfiguration() {
        String yaml = """
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: "%s"
                server:
                  name: "MineBridge Server"
                """.formatted(VALID_BOT_TOKEN, VALID_CHANNEL_ID);

        ConfigManager.Candidate candidate = parseCandidate(yaml);
        assertTrue(candidate.valid(), "Candidate must be valid: " + candidate.errors());
        assertNotNull(candidate.settings());
        assertEquals(IntegrationMode.BOT, candidate.settings().mode());
        assertEquals(VALID_BOT_TOKEN, candidate.settings().botToken());
        assertEquals(VALID_CHANNEL_ID, candidate.settings().channelId());

        AtomicBoolean schedulerStarted = new AtomicBoolean(false);
        AtomicReference<DiscordService> discordCreated = new AtomicReference<>();
        AtomicReference<WebhookService> webhookCreated = new AtomicReference<>();

        ConfigManager configManager = new ConfigManager(null);
        LifecycleManager manager = new LifecycleManager(
                configManager,
                () -> candidate,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> {
                    DiscordService svc = new DiscordService(null, cfg, null, msg -> {}, msg -> {});
                    discordCreated.set(svc);
                    return svc;
                },
                cfg -> {
                    WebhookService svc = new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {});
                    webhookCreated.set(svc);
                    return svc;
                },
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                msg -> {},
                msg -> {},
                () -> 1000L
        );

        assertDoesNotThrow(manager::start);
        assertNotNull(discordCreated.get(), "DiscordService must be created in BOT mode");
        assertNull(webhookCreated.get(), "WebhookService must NOT be created in BOT mode");
        assertTrue(schedulerStarted.get(), "Scheduler must start in BOT mode");
        assertEquals(IntegrationMode.BOT, manager.getStatus().mode());
    }

    @Test
    @DisplayName("Regression: Valid configuration for WEBHOOK mode starts successfully")
    void validWebhookConfiguration() {
        String yaml = """
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "%s"
                    username: "{player}"
                server:
                  name: "MineBridge Server"
                """.formatted(VALID_WEBHOOK_URL);

        ConfigManager.Candidate candidate = parseCandidate(yaml);
        assertTrue(candidate.valid(), "Candidate must be valid: " + candidate.errors());
        assertNotNull(candidate.settings());
        assertEquals(IntegrationMode.WEBHOOK, candidate.settings().mode());
        assertEquals(VALID_WEBHOOK_URL, candidate.settings().webhook().url());

        AtomicBoolean schedulerStarted = new AtomicBoolean(false);
        AtomicReference<DiscordService> discordCreated = new AtomicReference<>();
        AtomicReference<WebhookService> webhookCreated = new AtomicReference<>();

        ConfigManager configManager = new ConfigManager(null);
        LifecycleManager manager = new LifecycleManager(
                configManager,
                () -> candidate,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> {
                    DiscordService svc = new DiscordService(null, cfg, null, msg -> {}, msg -> {});
                    discordCreated.set(svc);
                    return svc;
                },
                cfg -> {
                    WebhookService svc = new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {});
                    webhookCreated.set(svc);
                    return svc;
                },
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                msg -> {},
                msg -> {},
                () -> 1000L
        );

        assertDoesNotThrow(manager::start);
        assertNull(discordCreated.get(), "DiscordService must NOT be created in WEBHOOK mode");
        assertNotNull(webhookCreated.get(), "WebhookService must be created in WEBHOOK mode");
        assertFalse(schedulerStarted.get(), "Scheduler must NOT start in WEBHOOK mode");
        assertEquals(IntegrationMode.WEBHOOK, manager.getStatus().mode());
        assertEquals("READY", manager.getStatus().discordState());
    }
}
