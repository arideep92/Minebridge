package com.minebridge.lifecycle;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.ChatMetadataService;
import com.minebridge.hook.EssentialsHook;
import com.minebridge.hook.LuckPermsHook;
import com.minebridge.hook.MultiverseHook;
import com.minebridge.hook.PlaceholderApiHook;
import com.minebridge.hook.VaultHook;
import net.dv8tion.jda.api.JDA;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StartupConfigurationHandlingRegressionTest {

    private static JDA createMockJda(AtomicBoolean shutdownCalled) {
        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class<?>[]{JDA.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getStatus".equals(name)) return JDA.Status.CONNECTED;
                    if ("shutdownNow".equals(name) || "shutdown".equals(name)) {
                        if (shutdownCalled != null) shutdownCalled.set(true);
                        return null;
                    }
                    if ("addEventListener".equals(name) || "removeEventListener".equals(name)) return null;
                    if ("getTextChannelById".equals(name)) return null;
                    return null;
                });
    }

    private static ConfigManager.Candidate candidateFromYaml(ConfigManager configManager, String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            YamlConfiguration defaults = configManager.loadBundledDefaults();
            if (defaults != null) {
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {}
        if (yaml != null && !yaml.isBlank()) {
            try {
                config.load(new StringReader(yaml));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return configManager.candidateFrom(config, "");
    }

    // =========================================================================
    // 1. First startup with no config.yml
    // =========================================================================

    @Test
    @DisplayName("First startup with no config.yml triggers default config generation and loads without crashing")
    void firstStartupWithNoConfigFileTriggersDefaultConfigGeneration(@TempDir Path tempDir) {
        AtomicBoolean savedDefault = new AtomicBoolean(false);
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");
        assertFalse(configFile.exists(), "config.yml must not exist initially");

        // Mock plugin that implements saveDefaultConfig
        org.bukkit.plugin.Plugin mockPlugin = (org.bukkit.plugin.Plugin) Proxy.newProxyInstance(
                org.bukkit.plugin.Plugin.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.Plugin.class},
                (proxy, method, args) -> {
                    if ("getDataFolder".equals(method.getName())) return dataFolder;
                    if ("saveDefaultConfig".equals(method.getName())) {
                        savedDefault.set(true);
                        // Write standard default config with empty token
                        YamlConfiguration defaultYaml = new YamlConfiguration();
                        defaultYaml.set("discord.mode", "BOT");
                        defaultYaml.set("discord.bot-token", "");
                        defaultYaml.set("discord.channel-id", "");
                        try {
                            defaultYaml.save(configFile);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    }
                    return null;
                });

        ConfigManager configManager = new ConfigManager(mockPlugin);
        List<String> warnings = new ArrayList<>();
        List<String> infoLogs = new ArrayList<>();

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                null, // Use default candidateSupplier which calls loadCandidate()
                new ChatMetadataService(warnings::add),
                cfg -> new DiscordService(null, cfg, null, infoLogs::add, warnings::add),
                cfg -> new WebhookService(cfg),
                Runnable::run,
                () -> {},
                () -> {},
                infoLogs::add,
                warnings::add,
                () -> 1000L
        );

        // Must not throw on first startup with no config.yml
        assertDoesNotThrow(lifecycleManager::start);

        assertTrue(savedDefault.get(), "saveDefaultConfig() must be called when config.yml is missing");
        assertTrue(configFile.exists(), "config.yml must exist after automatic generation");

        // MineBridge must remain enabled even though token is empty
        assertFalse(lifecycleManager.isStopping());
        assertNotNull(lifecycleManager.getChatListener());
        assertNotNull(lifecycleManager.getEventNotifier());

        // Status must reflect INVALID_CONFIGURATION without crashing
        var status = lifecycleManager.getStatus();
        assertNotNull(status);
        assertEquals("INVALID_CONFIGURATION", status.discordState());
        assertFalse(status.channelAvailable());

        // Clear actionable warnings must be logged
        assertTrue(warnings.stream().anyMatch(w -> w.contains("bot-token") || w.contains("Discord token")),
                "Must log actionable warnings about missing token");
    }

    // =========================================================================
    // 2. First startup with an empty token
    // =========================================================================

    @Test
    @DisplayName("First startup with an empty token produces actionable warnings and remains enabled")
    void firstStartupWithEmptyTokenDoesNotDisablePlugin() {
        ConfigManager configManager = new ConfigManager(null);
        String yamlContent = """
                discord:
                  mode: BOT
                  bot-token: ""
                  channel-id: ""
                features:
                  chat-relay: true
                  join-leave-notifications: true
                """;

        ConfigManager.Candidate candidate = candidateFromYaml(configManager, yamlContent);

        assertFalse(candidate.valid(), "Candidate with empty token must be invalid");
        assertFalse(candidate.errors().isEmpty());

        List<String> warnings = new ArrayList<>();
        List<String> infoLogs = new ArrayList<>();

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                () -> candidate,
                new ChatMetadataService(warnings::add),
                cfg -> new DiscordService(null, cfg, null, infoLogs::add, warnings::add),
                cfg -> new WebhookService(cfg),
                Runnable::run,
                () -> {},
                () -> {},
                infoLogs::add,
                warnings::add,
                () -> 2000L
        );

        // start must not throw and must not disable the plugin
        assertDoesNotThrow(lifecycleManager::start);
        assertFalse(lifecycleManager.isStopping(), "LifecycleManager must not shut down on empty token");

        // Minecraft listeners must remain initialized
        assertNotNull(lifecycleManager.getChatListener());
        assertNotNull(lifecycleManager.getEventNotifier());

        // Status must report INVALID_CONFIGURATION
        assertEquals("INVALID_CONFIGURATION", lifecycleManager.getStatus().discordState());

        // Actionable warnings must be logged
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Discord bot token is missing or invalid") || w.contains("missing Discord token")),
                "Warning must explain that Discord bot token is missing or invalid");
    }

    // =========================================================================
    // 3. Invalid token (Discord rejects token)
    // =========================================================================

    @Test
    @DisplayName("Invalid token produces actionable error without crashing or disabling MineBridge")
    void invalidTokenProducesClearWarningAndDoesNotCrash() {
        ConfigManager configManager = new ConfigManager(null);
        String yamlContent = """
                discord:
                  mode: BOT
                  bot-token: "invalid-malformed-token"
                  channel-id: "123456789012345678"
                features:
                  chat-relay: true
                """;

        ConfigManager.Candidate candidate = candidateFromYaml(configManager, yamlContent);
        assertTrue(candidate.valid(), "Configuration must be valid syntactically: " + candidate.errors());

        List<String> warnings = new ArrayList<>();

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                () -> candidate,
                new ChatMetadataService(warnings::add),
                cfg -> new DiscordService(
                        null, cfg,
                        settings -> {
                            throw new IllegalArgumentException("The provided token is invalid!");
                        },
                        msg -> {},
                        warnings::add
                ),
                cfg -> new WebhookService(cfg),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                warnings::add,
                () -> 3000L
        );

        assertDoesNotThrow(lifecycleManager::start);
        assertFalse(lifecycleManager.isStopping());

        // Status must report FAILED or DISCONNECTED, never throwing
        String state = lifecycleManager.getStatus().discordState();
        assertEquals("FAILED", state);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Discord rejected the configured token")),
                "Must log clear warning when Discord rejects the token");
    }

    // =========================================================================
    // 4. Valid configuration after previous failed initialization
    // =========================================================================

    @Test
    @DisplayName("Valid configuration after a previous failed initialization transitions to CONNECTED")
    void validConfigurationAfterFailedInitializationConnectsSuccessfully() {
        ConfigManager configManager = new ConfigManager(null);

        // Step 1: Start with invalid configuration
        ConfigManager.Candidate invalidCandidate = new ConfigManager.Candidate(
                configManager.currentSettings(), List.of("discord.bot-token: Discord bot token is missing or invalid.")
        );

        AtomicReference<ConfigManager.Candidate> currentCandidate = new AtomicReference<>(invalidCandidate);
        AtomicBoolean mockShutdownCalled = new AtomicBoolean(false);
        JDA mockJda = createMockJda(mockShutdownCalled);

        List<String> warnings = new ArrayList<>();
        List<String> infoLogs = new ArrayList<>();
        AtomicBoolean schedulerStarted = new AtomicBoolean(false);

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                currentCandidate::get,
                new ChatMetadataService(warnings::add),
                cfg -> new DiscordService(
                        null, cfg,
                        settings -> mockJda,
                        infoLogs::add,
                        warnings::add
                ),
                cfg -> new WebhookService(cfg),
                Runnable::run,
                () -> schedulerStarted.set(true),
                () -> schedulerStarted.set(false),
                infoLogs::add,
                warnings::add,
                () -> 4000L
        );

        lifecycleManager.start();
        assertEquals("INVALID_CONFIGURATION", lifecycleManager.getStatus().discordState());
        assertFalse(schedulerStarted.get(), "Bot scheduler must not start while configuration is invalid");

        // Step 2: User provides valid token in config and triggers /mb reload
        String validYamlContent = """
                discord:
                  mode: BOT
                  bot-token: "valid-working-token-abc"
                  channel-id: "123456789012345678"
                features:
                  chat-relay: true
                  join-leave-notifications: true
                """;
        ConfigManager.Candidate validCandidate = candidateFromYaml(configManager, validYamlContent);
        assertTrue(validCandidate.valid(), "Candidate must be valid: " + validCandidate.errors());

        currentCandidate.set(validCandidate);

        AtomicReference<ReloadResult> reloadResultRef = new AtomicReference<>();
        lifecycleManager.reload(reloadResultRef::set);

        assertNotNull(reloadResultRef.get());
        assertTrue(reloadResultRef.get().successful(), "Reload with valid candidate must succeed: " + reloadResultRef.get().message());
        assertEquals("CONNECTED", lifecycleManager.getStatus().discordState());
        assertTrue(schedulerStarted.get(), "Bot scheduler must be started after valid reload");
    }

    // =========================================================================
    // 5. Reloading without creating duplicate Discord connections
    // =========================================================================

    @Test
    @DisplayName("Successive reloads shut down previous connections without leaking duplicates")
    void reloadingWithoutCreatingDuplicateDiscordConnections() {
        ConfigManager configManager = new ConfigManager(null);

        AtomicInteger activeClientsCount = new AtomicInteger(0);
        AtomicInteger totalCreatedCount = new AtomicInteger(0);
        AtomicInteger totalShutdownCount = new AtomicInteger(0);

        String yaml1 = """
                discord:
                  mode: BOT
                  bot-token: "token-1"
                  channel-id: "111111111111111111"
                features:
                  chat-relay: true
                """;
        ConfigManager.Candidate cand1 = candidateFromYaml(configManager, yaml1);
        assertTrue(cand1.valid(), "cand1 must be valid: " + cand1.errors());

        AtomicReference<ConfigManager.Candidate> candidateRef = new AtomicReference<>(cand1);

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                candidateRef::get,
                new ChatMetadataService(msg -> {}),
                cfg -> new DiscordService(
                        null, cfg,
                        settings -> {
                            totalCreatedCount.incrementAndGet();
                            activeClientsCount.incrementAndGet();
                            return (JDA) Proxy.newProxyInstance(
                                    JDA.class.getClassLoader(),
                                    new Class<?>[]{JDA.class},
                                    (proxy, method, args) -> {
                                        String name = method.getName();
                                        if ("getStatus".equals(name)) return JDA.Status.CONNECTED;
                                        if ("shutdownNow".equals(name) || "shutdown".equals(name)) {
                                            totalShutdownCount.incrementAndGet();
                                            activeClientsCount.decrementAndGet();
                                            return null;
                                        }
                                        return null;
                                    });
                        },
                        msg -> {},
                        msg -> {}
                ),
                cfg -> new WebhookService(cfg),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 5000L
        );

        // Initial startup
        lifecycleManager.start();
        assertEquals(1, totalCreatedCount.get());
        assertEquals(1, activeClientsCount.get());

        // Reload #1 with token-2
        String yaml2 = """
                discord:
                  mode: BOT
                  bot-token: "token-2"
                  channel-id: "222222222222222222"
                features:
                  chat-relay: true
                """;
        ConfigManager.Candidate cand2 = candidateFromYaml(configManager, yaml2);
        assertTrue(cand2.valid(), "cand2 must be valid: " + cand2.errors());
        candidateRef.set(cand2);

        lifecycleManager.reload(res -> assertTrue(res.successful()));

        assertEquals(2, totalCreatedCount.get());
        assertEquals(1, totalShutdownCount.get(), "Previous JDA must have been shut down");
        assertEquals(1, activeClientsCount.get(), "Must have exactly 1 active client");

        // Reload #2 with token-3
        String yaml3 = """
                discord:
                  mode: BOT
                  bot-token: "token-3"
                  channel-id: "333333333333333333"
                features:
                  chat-relay: true
                """;
        ConfigManager.Candidate cand3 = candidateFromYaml(configManager, yaml3);
        assertTrue(cand3.valid(), "cand3 must be valid: " + cand3.errors());
        candidateRef.set(cand3);

        lifecycleManager.reload(res -> assertTrue(res.successful()));

        assertEquals(3, totalCreatedCount.get());
        assertEquals(2, totalShutdownCount.get(), "Two older JDAs must have been shut down");
        assertEquals(1, activeClientsCount.get(), "Must have exactly 1 active client");

        // Final shutdown
        lifecycleManager.shutdown();
        assertEquals(3, totalShutdownCount.get(), "All JDAs must be shut down upon plugin shutdown");
        assertEquals(0, activeClientsCount.get(), "Zero clients remaining active after shutdown");
    }

    // =========================================================================
    // 6. Missing optional dependencies
    // =========================================================================

    @Test
    @DisplayName("Startup and reload function normally when all optional dependencies are absent")
    void missingOptionalDependenciesFunctionNormally() {
        ConfigManager configManager = new ConfigManager(null);

        // Create ChatMetadataService with all hooks explicitly unavailable (simulates missing plugins)
        ChatMetadataService metadata = new ChatMetadataService(
                new LuckPermsHook(() -> false, (p, u) -> ""),
                new VaultHook(() -> false, (p, w) -> "", (p, w) -> "", p -> ""),
                new EssentialsHook(() -> false, p -> ""),
                new MultiverseHook(() -> false, w -> ""),
                new PlaceholderApiHook(() -> false, (p, t) -> t),
                msg -> {}
        );

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                null,
                metadata,
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> new WebhookService(cfg),
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 6000L
        );

        assertDoesNotThrow(lifecycleManager::start);
        assertFalse(lifecycleManager.isStopping());

        // Player resolution falls back gracefully
        Player mockPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getName".equals(name)) return "Notch";
                    if ("getDisplayName".equals(name)) return "NotchDisplay";
                    if ("getUniqueId".equals(name)) return UUID.randomUUID();
                    return null;
                });

        assertEquals("", metadata.getPrefix(mockPlayer));
        assertEquals("", metadata.getSuffix(mockPlayer));
        assertEquals("NotchDisplay", metadata.getNickname(mockPlayer));
        assertEquals("Template {player}", metadata.applyPlaceholders(mockPlayer, "Template {player}"));
    }

    // =========================================================================
    // 7. WEBHOOK mode on first startup and shutdown
    // =========================================================================

    @Test
    @DisplayName("First startup in WEBHOOK mode with valid webhook URL starts WebhookService")
    void firstStartupInWebhookMode() {
        ConfigManager configManager = new ConfigManager(null);
        String yamlWebhook = """
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "https://discord.com/api/webhooks/123456789012345678/synthetic-webhook-token"
                features:
                  chat-relay: true
                """;
        ConfigManager.Candidate cand = candidateFromYaml(configManager, yamlWebhook);
        assertTrue(cand.valid(), "cand must be valid: " + cand.errors());

        AtomicReference<WebhookService> webhookRef = new AtomicReference<>();

        LifecycleManager lifecycleManager = new LifecycleManager(
                configManager,
                () -> cand,
                new ChatMetadataService(msg -> {}),
                cfg -> new DiscordService(null, cfg, null, msg -> {}, msg -> {}),
                cfg -> {
                    WebhookService ws = new WebhookService(cfg);
                    webhookRef.set(ws);
                    return ws;
                },
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                msg -> {},
                () -> 7000L
        );

        lifecycleManager.start();
        assertFalse(lifecycleManager.isStopping());
        assertEquals(IntegrationMode.WEBHOOK, lifecycleManager.getStatus().mode());
        assertEquals("READY", lifecycleManager.getStatus().discordState());

        lifecycleManager.shutdown();
        assertTrue(lifecycleManager.isStopping());
        assertNotNull(webhookRef.get());
        assertEquals("STOPPED", webhookRef.get().getStatus(0, true, true, false).discordState());
    }
}
