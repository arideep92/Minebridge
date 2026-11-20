package com.minebridge.lifecycle;

import com.minebridge.command.MineBridgeStatus;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import okhttp3.*;
import okio.Timeout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleManagerTest {

    private static final String BOT_TOKEN = "synthetic-test-bot-token-12345";
    private static final String CHANNEL_ID = "123456789012345678";
    private static final String WEBHOOK_SECRET = "invalid-test-token-placeholder";
    private static final String WEBHOOK_URL =
            "https://invalid.discord.test/api/webhooks/invalid-placeholder/" + WEBHOOK_SECRET;

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

    private ConfigManager.Settings createWebhookSettings(String webhookUrl) {
        ConfigManager.Candidate botCandidate = createCandidate(botConfigYaml());
        ConfigManager.Settings base = botCandidate.settings();
        return new ConfigManager.Settings(
                IntegrationMode.WEBHOOK,
                base.botToken(),
                base.channelId(),
                base.chatEnabled(),
                base.eventsEnabled(),
                base.messages(),
                base.serverStatusEnabled(),
                base.playerListEnabled(),
                base.deathMessages(),
                base.achievements(),
                base.discordEvents(),
                new ConfigManager.WebhookSettings(webhookUrl, "{player}", ""),
                base.serverName(),
                base.embedFooter(),
                base.updateCheckerEnabled());
    }

    private ConfigManager.Candidate validWebhookCandidate() {
        return new ConfigManager.Candidate(
                createWebhookSettings("https://discord.com/api/webhooks/123456789012345678/mock-token"),
                List.of());
    }

    private ConfigManager.Candidate createCandidate(String yaml) {
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

    private ConfigManager createConfig(String yaml) {
        if (yaml.contains("WEBHOOK")) {
            ConfigManager manager = new ConfigManager(null);
            manager.commit(new ConfigManager.Candidate(createWebhookSettings(WEBHOOK_URL), List.of()));
            return manager;
        }
        ConfigManager.Candidate candidate = createCandidate(yaml);
        ConfigManager manager = new ConfigManager(null);
        manager.commit(candidate);
        return manager;
    }

    private String botConfigYaml() {
        return """
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: "%s"
                server:
                  name: "TestServer"
                """.formatted(BOT_TOKEN, CHANNEL_ID);
    }

    private String webhookConfigYaml() {
        return """
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "%s"
                    username: "{player}"
                server:
                  name: "TestServer"
                """.formatted(WEBHOOK_URL);
    }

    @Test
    void botOnlyInitialization() {
        ConfigManager config = createConfig(botConfigYaml());
        AtomicInteger discordCreated = new AtomicInteger();
        AtomicInteger webhookCreated = new AtomicInteger();
        AtomicBoolean schedulerStarted = new AtomicBoolean();

        LifecycleManager manager = new LifecycleManager(
                config,
                new LuckPermsHook(() -> true, uuid -> "[Admin] "),
                cfg -> {
                    discordCreated.incrementAndGet();
                    return new DiscordService(null, cfg, null, msg -> {}, msg -> {});
                },
                cfg -> {
                    webhookCreated.incrementAndGet();
                    return new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {});
                },
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                msg -> {},
                msg -> {},
                () -> 5000L);

        manager.start();

        assertEquals(1, discordCreated.get());
        assertEquals(0, webhookCreated.get());
        assertTrue(schedulerStarted.get());
        assertNotNull(manager.getDiscordService());
        assertNull(manager.getWebhookService());
        assertEquals(IntegrationMode.BOT, manager.getStatus().mode());
    }

    @Test
    void webhookOnlyInitializationAndNoJdaGateway() {
        ConfigManager config = createConfig(webhookConfigYaml());
        AtomicInteger discordCreated = new AtomicInteger();
        AtomicInteger webhookCreated = new AtomicInteger();
        AtomicBoolean schedulerStarted = new AtomicBoolean();

        LifecycleManager manager = new LifecycleManager(
                config,
                () -> validWebhookCandidate(),
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> {
                    discordCreated.incrementAndGet();
                    return new DiscordService(null, cfg, null, msg -> {}, msg -> {});
                },
                cfg -> {
                    webhookCreated.incrementAndGet();
                    return new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {});
                },
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                msg -> {},
                msg -> {},
                () -> 5000L);

        manager.start();

        assertEquals(0, discordCreated.get());
        assertEquals(1, webhookCreated.get());
        assertFalse(schedulerStarted.get());
        assertNull(manager.getDiscordService());
        assertNotNull(manager.getWebhookService());
        assertEquals(IntegrationMode.WEBHOOK, manager.getStatus().mode());
        assertEquals("READY", manager.getStatus().discordState());
        assertTrue(manager.getStatus().channelAvailable());
    }

    @Test
    void botToWebhookReloadTransitionsCleanly() {
        ConfigManager config = createConfig(botConfigYaml());
        AtomicInteger discordCreated = new AtomicInteger();
        AtomicInteger webhookCreated = new AtomicInteger();
        AtomicBoolean schedulerStarted = new AtomicBoolean();

        LifecycleManager manager = new LifecycleManager(
                config,
                new LuckPermsHook(() -> true, uuid -> "[Mod] "),
                cfg -> {
                    discordCreated.incrementAndGet();
                    return new DiscordService(null, cfg, null, msg -> {}, msg -> {});
                },
                cfg -> {
                    webhookCreated.incrementAndGet();
                    return new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {});
                },
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                msg -> {},
                msg -> {},
                () -> 5000L);

        manager.start();
        assertTrue(schedulerStarted.get());
        assertNotNull(manager.getDiscordService());
        assertNull(manager.getWebhookService());

        // Switch config candidate to WEBHOOK mode
        ConfigManager.Candidate webhookCandidate =
                validWebhookCandidate();
        ConfigManager.Candidate initialBotCandidate = createCandidate(botConfigYaml());

        AtomicReference<ConfigManager.Candidate> candidateRef =
                new AtomicReference<>(initialBotCandidate);

        LifecycleManager managerWithCandidate = new LifecycleManager(
                config,
                candidateRef::get,
                new LuckPermsHook(() -> true, uuid -> "[Mod] "),
                cfg -> {
                    discordCreated.incrementAndGet();
                    return new DiscordService(null, cfg, null, msg -> {}, msg -> {});
                },
                cfg -> {
                    webhookCreated.incrementAndGet();
                    return new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {});
                },
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                msg -> {},
                msg -> {},
                () -> 5000L);

        managerWithCandidate.start();
        assertTrue(schedulerStarted.get());

        // Update candidate for reload
        candidateRef.set(webhookCandidate);
        AtomicReference<ReloadResult> resultRef = new AtomicReference<>();
        managerWithCandidate.reload(resultRef::set);

        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().successful());
        assertFalse(schedulerStarted.get(), "Scheduler must be canceled upon switching to WEBHOOK");
        assertNull(managerWithCandidate.getDiscordService(), "DiscordService must be null in WEBHOOK mode");
        assertNotNull(managerWithCandidate.getWebhookService(), "WebhookService must be active");
        assertEquals(IntegrationMode.WEBHOOK, managerWithCandidate.getStatus().mode());
    }

    @Test
    void webhookToBotReloadTransitionsCleanly() {
        ConfigManager configWithWebhook = createConfig(webhookConfigYaml());
        ConfigManager.Candidate initialWebhookCandidate =
                validWebhookCandidate();
        ConfigManager.Candidate botCandidate = createCandidate(botConfigYaml());

        AtomicReference<ConfigManager.Candidate> candidateRef =
                new AtomicReference<>(initialWebhookCandidate);
        AtomicBoolean schedulerActive = new AtomicBoolean(false);

        net.dv8tion.jda.api.JDA mockJda = (net.dv8tion.jda.api.JDA) java.lang.reflect.Proxy.newProxyInstance(
                net.dv8tion.jda.api.JDA.class.getClassLoader(),
                new Class<?>[]{net.dv8tion.jda.api.JDA.class},
                (proxy, method, args) -> {
                    if ("getStatus".equals(method.getName())) {
                        return net.dv8tion.jda.api.JDA.Status.CONNECTED;
                    }
                    return null;
                });

        LifecycleManager manager = new LifecycleManager(
                configWithWebhook,
                candidateRef::get,
                new LuckPermsHook(() -> true, uuid -> ""),
                cfg -> new DiscordService(null, cfg, settings -> mockJda, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> schedulerActive.set(true),
                () -> schedulerActive.set(false),
                msg -> {},
                msg -> {},
                () -> 1000L);

        manager.start();
        assertEquals(IntegrationMode.WEBHOOK, manager.getStatus().mode());
        assertFalse(schedulerActive.get());

        // Perform reload to switch to BOT
        candidateRef.set(botCandidate);
        AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
        manager.reload(reloadResult::set);

        assertNotNull(reloadResult.get());
        assertTrue(reloadResult.get().successful());
        assertTrue(schedulerActive.get(), "Scheduler must be started for BOT mode");
        assertNotNull(manager.getDiscordService(), "DiscordService must be active");
        assertNull(manager.getWebhookService(), "WebhookService must be null in BOT mode");
        assertEquals(IntegrationMode.BOT, manager.getStatus().mode());
    }

    @Test
    void invalidConfigurationDuringReloadPreservesActiveState() {
        ConfigManager config = createConfig(webhookConfigYaml());
        ConfigManager.Candidate initialWebhookCandidate =
                validWebhookCandidate();
        ConfigManager.Candidate invalidCandidate = new ConfigManager.Candidate(
                config.currentSettings(), List.of("Invalid webhook URL host"));

        AtomicReference<ConfigManager.Candidate> candidateRef =
                new AtomicReference<>(initialWebhookCandidate);

        LifecycleManager manager = new LifecycleManager(
                config,
                candidateRef::get,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 1000L);

        manager.start();
        assertNotNull(manager.getWebhookService());

        // Set invalid candidate for reload
        candidateRef.set(invalidCandidate);
        AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
        manager.reload(reloadResult::set);

        assertNotNull(reloadResult.get());
        assertFalse(reloadResult.get().successful());
        assertTrue(reloadResult.get().message().contains("Invalid webhook URL host"));
        // Service remains active and not broken
        assertNotNull(manager.getWebhookService());
    }

    @Test
    void initializationFailureAndCleanup() {
        // 1. Initial BOT failure
        ConfigManager.Candidate invalidBot = new ConfigManager.Candidate(
                createConfig(botConfigYaml()).currentSettings(), List.of("Invalid bot token"));
        ConfigManager botConfig = createConfig(botConfigYaml());
        AtomicBoolean botScheduler = new AtomicBoolean(false);

        LifecycleManager botManager = new LifecycleManager(
                botConfig,
                () -> invalidBot,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> botScheduler.set(true),
                () -> botScheduler.set(false),
                msg -> {},
                msg -> {},
                () -> 0L);

        botManager.start();
        assertFalse(botScheduler.get(), "Scheduler must not start on invalid BOT config");
        assertEquals("INVALID_CONFIGURATION", botManager.getStatus().discordState());
        botManager.shutdown();
        assertTrue(botManager.isStopping());

        // 2. Initial WEBHOOK failure
        ConfigManager webhookConfig = createConfig(webhookConfigYaml());
        ConfigManager.Candidate invalidWebhook = new ConfigManager.Candidate(
                webhookConfig.currentSettings(), List.of("Invalid webhook URL host"));

        LifecycleManager webhookManager = new LifecycleManager(
                webhookConfig,
                () -> invalidWebhook,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 0L);

        webhookManager.start();
        assertEquals("INVALID_WEBHOOK", webhookManager.getStatus().discordState());
        assertFalse(webhookManager.getStatus().channelAvailable());
        webhookManager.shutdown();
        assertTrue(webhookManager.isStopping());
    }

    @Test
    void repeatedReloadReusesListenersWithoutDuplication() {
        ConfigManager config = createConfig(webhookConfigYaml());
        ConfigManager.Candidate validWebhookCandidate =
                validWebhookCandidate();
        LifecycleManager manager = new LifecycleManager(
                config,
                () -> validWebhookCandidate,
                new LuckPermsHook(() -> true, uuid -> "[Tester] "),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 2000L);

        manager.start();
        var initialNotifier = manager.getEventNotifier();
        var initialChatListener = manager.getChatListener();

        // Reload 3 times
        for (int i = 0; i < 3; i++) {
            AtomicReference<ReloadResult> res = new AtomicReference<>();
            manager.reload(res::set);
            assertTrue(res.get().successful());
            // Listener and Notifier instances must remain identical (no duplicate listener registrations)
            assertSame(initialNotifier, manager.getEventNotifier());
            assertSame(initialChatListener, manager.getChatListener());
        }
    }

    @Test
    void idempotentShutdown() {
        ConfigManager config = createConfig(webhookConfigYaml());
        AtomicInteger cancels = new AtomicInteger();

        LifecycleManager manager = new LifecycleManager(
                config,
                () -> validWebhookCandidate(),
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg, noopCallFactory(), msg -> {}, msg -> {}),
                Runnable::run,
                () -> {},
                cancels::incrementAndGet,
                msg -> {},
                msg -> {},
                () -> 1000L);

        manager.start();
        assertFalse(manager.isStopping());

        // Call shutdown twice
        manager.shutdown();
        assertTrue(manager.isStopping());
        assertEquals(1, cancels.get());

        manager.shutdown();
        assertTrue(manager.isStopping());
        assertEquals(1, cancels.get(), "Shutdown must be idempotent");

        // Reload after shutdown should fail gracefully
        AtomicReference<ReloadResult> res = new AtomicReference<>();
        manager.reload(res::set);
        assertNotNull(res.get());
        assertFalse(res.get().successful());
        assertTrue(res.get().message().contains("shutting down"));
    }

    @Test
    void statusOutputForBothModesAndNoSecretsExposed() {
        List<String> logMessages = new ArrayList<>();

        // 1. Webhook mode status
        ConfigManager webhookConfig = createConfig(webhookConfigYaml());
        LifecycleManager webhookManager = new LifecycleManager(
                webhookConfig,
                () -> validWebhookCandidate(),
                new LuckPermsHook(() -> true, uuid -> "[Admin] "),
                cfg -> new DiscordService(null, cfg, null, logMessages::add, logMessages::add),
                cfg -> new WebhookService(cfg, noopCallFactory(), logMessages::add, logMessages::add),
                Runnable::run,
                () -> {},
                () -> {},
                logMessages::add,
                logMessages::add,
                () -> 3600_000L);

        webhookManager.start();
        MineBridgeStatus webhookStatus = webhookManager.getStatus();
        List<String> webhookLines = webhookStatus.formatLines();

        assertTrue(webhookLines.stream().anyMatch(l -> l.contains("Mode: §fWEBHOOK")));
        assertTrue(webhookLines.stream().anyMatch(l -> l.contains("Webhook status: §aREADY")));
        assertTrue(webhookLines.stream().anyMatch(l -> l.contains("Webhook destination: §aValid")));
        assertTrue(webhookLines.stream().anyMatch(l -> l.contains("LuckPerms: §aAvailable")));
        assertTrue(webhookLines.stream().anyMatch(l -> l.contains("Plugin uptime: §f1h 0m")));

        // 2. Bot mode status
        ConfigManager botConfig = createConfig(botConfigYaml());
        LifecycleManager botManager = new LifecycleManager(
                botConfig,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, logMessages::add, logMessages::add),
                cfg -> new WebhookService(cfg, noopCallFactory(), logMessages::add, logMessages::add),
                Runnable::run,
                () -> {},
                () -> {},
                logMessages::add,
                logMessages::add,
                () -> 120_000L);

        botManager.start();
        MineBridgeStatus botStatus = botManager.getStatus();
        List<String> botLines = botStatus.formatLines();

        assertTrue(botLines.stream().anyMatch(l -> l.contains("Mode: §fBOT")));
        assertTrue(botLines.stream().anyMatch(l -> l.contains("Discord: ")));
        assertTrue(botLines.stream().anyMatch(l -> l.contains("LuckPerms: §cUnavailable")));

        // 3. Strict verification: NO secrets appear in any status lines or log messages
        for (String line : webhookLines) {
            assertFalse(line.contains(WEBHOOK_SECRET), "Webhook secret found in status line: " + line);
            assertFalse(line.contains(BOT_TOKEN), "Bot token found in status line: " + line);
        }
        for (String line : botLines) {
            assertFalse(line.contains(WEBHOOK_SECRET), "Webhook secret found in status line: " + line);
            assertFalse(line.contains(BOT_TOKEN), "Bot token found in status line: " + line);
        }
        for (String log : logMessages) {
            assertFalse(log.contains(WEBHOOK_SECRET), "Webhook secret found in log message: " + log);
            assertFalse(log.contains(BOT_TOKEN), "Bot token found in log message: " + log);
        }
    }
}
