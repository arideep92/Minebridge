package com.minebridge.lifecycle;

import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import com.minebridge.update.UpdateChecker;
import okhttp3.*;
import okio.Timeout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Stage4ResourceAndReloadTest {

    private static final String VALID_BOT_TOKEN = "synthetic-test-bot-token-12345";
    private static final String VALID_CHANNEL_ID = "123456789012345678";
    private static final String VALID_WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/mock-token";

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

    private ConfigManager.Candidate parseYaml(String yaml) {
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
        return new ConfigManager(null).candidateFrom(config, null);
    }

    @Test
    void testMissingBotTokenProvidesActionableErrorWithoutCrashing() {
        String yaml = """
                discord:
                  mode: BOT
                  channel-id: "%s"
                """.formatted(VALID_CHANNEL_ID);
        ConfigManager.Candidate candidate = parseYaml(yaml);
        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(e ->
                e.contains("Discord bot token is missing or invalid")));
        // Should NOT have confusing "discord.bot-token must be a string."
        assertFalse(candidate.errors().stream().anyMatch(e ->
                e.contains("discord.bot-token must be a string.")));
        assertNotNull(candidate.settings());
        assertEquals("", candidate.settings().botToken());
    }

    @Test
    void testInvalidHexColorFallsBackSafelyWithWarning() {
        String yaml = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                death-messages:
                  discord:
                    color: "not-a-color"
                achievements:
                  discord:
                    color: "XYZ1234"
                """.formatted(VALID_BOT_TOKEN, VALID_CHANNEL_ID);
        ConfigManager.Candidate candidate = parseYaml(yaml);
        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(e ->
                e.contains("death-messages.discord.color must be a six-digit hex color string.")));
        assertTrue(candidate.errors().stream().anyMatch(e ->
                e.contains("achievements.discord.color must be a six-digit hex color string.")));
        // Values fell back safely
        assertEquals(0xD32F2F, candidate.settings().deathMessages().embedColor());
        assertEquals(0x2196F3, candidate.settings().achievements().embedColor());
    }

    @Test
    void testInvalidChannelIdsProduceClearErrors() {
        String yaml = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                death-messages:
                  discord:
                    channel-id: "invalid-id"
                achievements:
                  discord:
                    channel-id: "999"
                """.formatted(VALID_BOT_TOKEN, VALID_CHANNEL_ID);
        ConfigManager.Candidate candidate = parseYaml(yaml);
        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(e ->
                e.contains("death-messages.discord.channel-id must be a 17-20 digit Discord channel ID.")));
        assertTrue(candidate.errors().stream().anyMatch(e ->
                e.contains("achievements.discord.channel-id must be a 17-20 digit Discord channel ID.")));
    }

    @Test
    void testInvalidSubsectionProducesClearError() {
        String yaml = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                death-messages:
                  minecraft: "invalid-not-a-section"
                """.formatted(VALID_BOT_TOKEN, VALID_CHANNEL_ID);
        ConfigManager.Candidate candidate = parseYaml(yaml);
        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(e ->
                e.contains("death-messages.minecraft must be a configuration section.")));
    }

    @Test
    void testModeSwitchingCleanupAndStateTracking() {
        List<String> logs = new ArrayList<>();
        AtomicReference<ConfigManager.Candidate> currentCandidate = new AtomicReference<>();
        ConfigManager configManager = new ConfigManager(null);

        AtomicInteger botStarts = new AtomicInteger(0);
        AtomicInteger webhookStarts = new AtomicInteger(0);
        AtomicInteger schedulerStarts = new AtomicInteger(0);
        AtomicInteger schedulerCancels = new AtomicInteger(0);

        LifecycleManager manager = new LifecycleManager(
                configManager,
                currentCandidate::get,
                (LuckPermsHook) null,
                cfg -> {
                    botStarts.incrementAndGet();
                    return new DiscordService(null, cfg, null, logs::add, logs::add);
                },
                cfg -> {
                    webhookStarts.incrementAndGet();
                    return new WebhookService(cfg, noopCallFactory(), logs::add, logs::add);
                },
                Runnable::run,
                schedulerStarts::incrementAndGet,
                schedulerCancels::incrementAndGet,
                logs::add,
                logs::add,
                () -> 1000L
        );

        // 1. Initial startup in BOT mode
        ConfigManager.Candidate botCandidate = parseYaml("""
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_BOT_TOKEN, VALID_CHANNEL_ID));
        currentCandidate.set(botCandidate);
        manager.start();

        DiscordService initialDiscord = manager.getDiscordService();
        assertNotNull(initialDiscord);
        assertFalse(initialDiscord.isStopping());
        assertNull(manager.getWebhookService());
        assertEquals(1, schedulerStarts.get());

        // 2. Reload into WEBHOOK mode
        ConfigManager.Candidate webhookCandidate = parseYaml("""
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "%s"
                """.formatted(VALID_WEBHOOK_URL));
        currentCandidate.set(webhookCandidate);

        AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
        manager.reload(reloadResult::set);

        assertNotNull(reloadResult.get());
        assertTrue(reloadResult.get().successful());
        assertNull(manager.getDiscordService());
        assertTrue(initialDiscord.isStopping()); // Discord service was cleanly stopped
        WebhookService activeWebhook = manager.getWebhookService();
        assertNotNull(activeWebhook);
        assertFalse(activeWebhook.isStopping());
        assertEquals(1, schedulerCancels.get());

        // 3. Repeated reload in WEBHOOK mode
        reloadResult.set(null);
        manager.reload(reloadResult::set);
        assertNotNull(reloadResult.get());
        assertTrue(reloadResult.get().successful());
        assertNotNull(manager.getWebhookService());

        // 4. Switch back to BOT mode
        currentCandidate.set(botCandidate);
        reloadResult.set(null);
        manager.reload(reloadResult::set);

        assertNotNull(manager.getDiscordService());
        assertNull(manager.getWebhookService());
        assertTrue(activeWebhook.isStopping()); // Webhook service was cleanly stopped
        assertTrue(schedulerStarts.get() >= 2);

        // 5. Final shutdown cleans up everything
        manager.shutdown();
        assertTrue(manager.isStopping());
        assertNull(manager.getDiscordService());
        assertNull(manager.getWebhookService());
        assertTrue(schedulerCancels.get() >= 2);
    }

    @Test
    void testUpdateCheckerDynamicToggleAndResourceCleanup() {
        UpdateChecker checker = new UpdateChecker("1.3.2", true);
        assertTrue(checker.isEnabled());
        assertFalse(checker.isShutdown());

        checker.setEnabled(false);
        assertFalse(checker.isEnabled());

        checker.setEnabled(true);
        assertTrue(checker.isEnabled());

        // Shutdown cleanly shuts down executor and OkHttpClient
        checker.shutdown();
        assertTrue(checker.isShutdown());
    }

    @Test
    void testStartupWithInvalidConfigTracksWebhookModeGracefully() {
        List<String> logs = new ArrayList<>();
        ConfigManager configManager = new ConfigManager(null);

        // Webhook mode with missing webhook URL on first startup
        ConfigManager.Candidate invalidWebhookCandidate = parseYaml("""
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: ""
                """);

        // Start manager directly and then reload on it
        AtomicReference<ConfigManager.Candidate> dynamicCandidate = new AtomicReference<>(invalidWebhookCandidate);
        LifecycleManager dynamicManager = new LifecycleManager(
                configManager,
                dynamicCandidate::get,
                (LuckPermsHook) null,
                cfg -> new DiscordService(null, cfg, null, logs::add, logs::add),
                cfg -> new WebhookService(cfg, noopCallFactory(), logs::add, logs::add),
                Runnable::run,
                () -> {},
                () -> {},
                logs::add,
                logs::add,
                () -> 0L
        );
        dynamicManager.start();
        assertNull(dynamicManager.getDiscordService());
        assertNotNull(dynamicManager.getWebhookService());
        assertEquals("INVALID_WEBHOOK", dynamicManager.getWebhookService().getState());

        // Reload with valid candidate
        ConfigManager.Candidate validWebhookCandidate = parseYaml("""
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "%s"
                """.formatted(VALID_WEBHOOK_URL));

        dynamicCandidate.set(validWebhookCandidate);
        AtomicReference<ReloadResult> result = new AtomicReference<>();
        dynamicManager.reload(result::set);

        assertNotNull(result.get());
        assertTrue(result.get().successful());
        assertNotNull(dynamicManager.getWebhookService());
        assertEquals("READY", dynamicManager.getWebhookService().getState());

        dynamicManager.shutdown();
    }
}
