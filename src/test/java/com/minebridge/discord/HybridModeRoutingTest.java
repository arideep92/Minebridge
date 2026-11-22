package com.minebridge.discord;

import com.minebridge.command.MineBridgeStatus;
import com.minebridge.config.ConfigManager;
import com.minebridge.hook.LuckPermsHook;
import com.minebridge.lifecycle.LifecycleManager;
import com.minebridge.listener.ChatListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import okhttp3.*;
import okio.Buffer;
import okio.Timeout;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class HybridModeRoutingTest {

    private static final String VALID_TOKEN = "synthetic-test-bot-token-12345";
    private static final String VALID_CHANNEL = "123456789012345678";
    private static final String VALID_WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/test_webhook_token";

    private static class RecordingCallFactory implements Call.Factory {
        final List<Request> recordedRequests = new ArrayList<>();
        final List<String> recordedBodies = new ArrayList<>();

        @Override
        public Call newCall(Request request) {
            recordedRequests.add(request);
            try {
                if (request.body() != null) {
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    recordedBodies.add(buffer.readUtf8());
                } else {
                    recordedBodies.add("");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return new Call() {
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
    }

    private static JDA createMockJda(List<Object> sentObjects) {
        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class<?>[]{JDA.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getStatus".equals(name)) return JDA.Status.CONNECTED;
                    if ("getTextChannelById".equals(name)) {
                        return Proxy.newProxyInstance(
                                net.dv8tion.jda.api.entities.channel.concrete.TextChannel.class.getClassLoader(),
                                new Class<?>[]{net.dv8tion.jda.api.entities.channel.concrete.TextChannel.class},
                                (cProxy, cMethod, cArgs) -> {
                                    if ("sendMessage".equals(cMethod.getName())) {
                                        if (sentObjects != null && cArgs != null && cArgs.length > 0) {
                                            sentObjects.add(cArgs[0]);
                                        }
                                        return Proxy.newProxyInstance(
                                                net.dv8tion.jda.api.requests.restaction.MessageCreateAction.class.getClassLoader(),
                                                new Class<?>[]{net.dv8tion.jda.api.requests.restaction.MessageCreateAction.class},
                                                (mProxy, mMethod, mArgs) -> {
                                                    if ("queue".equals(mMethod.getName())) {
                                                        if (mArgs != null && mArgs.length > 0 && mArgs[0] instanceof java.util.function.Consumer consumer) {
                                                            consumer.accept(null);
                                                        }
                                                        return null;
                                                    }
                                                    return mProxy;
                                                });
                                    }
                                    return null;
                                });
                    }
                    if ("shutdownNow".equals(name) || "shutdown".equals(name)) return null;
                    if ("addEventListener".equals(name) || "removeEventListener".equals(name)) return null;
                    return null;
                });
    }

    private static Player createMockPlayer(String name, UUID uuid, Collection<Player> onlinePlayersRef) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
                    if ("getUniqueId".equals(methodName)) return uuid;
                    if ("isOnline".equals(methodName)) return true;
                    if ("hasMetadata".equals(methodName)) return false;
                    if ("getMetadata".equals(methodName)) return List.of();
                    if ("getServer".equals(methodName)) {
                        return (Server) Proxy.newProxyInstance(
                                Server.class.getClassLoader(),
                                new Class<?>[]{Server.class},
                                (sProxy, sMethod, sArgs) -> {
                                    if ("getOnlinePlayers".equals(sMethod.getName())) {
                                        return onlinePlayersRef;
                                    }
                                    return null;
                                });
                    }
                    if ("equals".equals(methodName)) {
                        if (args[0] instanceof Player other) {
                            return uuid.equals(other.getUniqueId());
                        }
                        return false;
                    }
                    if ("hashCode".equals(methodName)) return uuid.hashCode();
                    return null;
                });
    }

    private static ConfigManager createConfig(String yaml) {
        ConfigManager manager = new ConfigManager(null);
        YamlConfiguration config = parseYaml(yaml);
        ConfigManager.Candidate candidate = manager.candidateFrom(config, null);
        assertTrue(candidate.valid(), "Candidate errors: " + candidate.errors());
        manager.commit(candidate);
        return manager;
    }

    private static String hybridConfigYaml(String eventDelivery) {
        return """
                discord:
                  mode: HYBRID
                  bot-token: "%s"
                  channel-id: "%s"
                  webhook:
                    url: "%s"
                    username: "{prefix}{player}"
                    avatar-url: "https://mc-heads.net/avatar/{player}/100"
                  hybrid:
                    event-delivery: %s
                features:
                  chat-relay: true
                  join-leave-notifications: true
                  server-status: true
                  player-list: true
                """.formatted(VALID_TOKEN, VALID_CHANNEL, VALID_WEBHOOK_URL, eventDelivery);
    }

    // =========================================================================
    // 1. Directional Transport Tests
    // =========================================================================

    @Test
    void hybridModeMinecraftChatRoutesExclusivelyToWebhookNotBot() {
        ConfigManager config = createConfig(hybridConfigYaml("WEBHOOK"));
        RecordingCallFactory callFactory = new RecordingCallFactory();
        WebhookService webhookService = new WebhookService(config, callFactory, null, null);
        webhookService.start();

        DiscordService discordService = new DiscordService(null, config, null, null, null);
        ChatListener chatListener = new ChatListener(null, discordService, webhookService, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent chatEvent = new AsyncPlayerChatEvent(true, alex, "Hello from Minecraft!", recipients);

        chatListener.onPlayerChat(chatEvent);

        // Webhook must receive the outgoing message
        assertEquals(1, webhookService.getStatus(0L, true, true, false).outgoingQueueSize(),
                "In HYBRID mode, Minecraft chat must be enqueued to Webhook");

        // Bot MUST NOT receive the outgoing message (zero duplicate chat)
        assertEquals(0, discordService.getStatus(0L).outgoingQueueSize(),
                "In HYBRID mode, Bot must NOT send outgoing Minecraft chat");
    }

    @Test
    void webhookModeMinecraftChatRoutesOnlyToWebhook() {
        String webhookYaml = """
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "%s"
                features:
                  chat-relay: true
                  join-leave-notifications: true
                  server-status: true
                  player-list: true
                """.formatted(VALID_WEBHOOK_URL);
        ConfigManager config = createConfig(webhookYaml);
        RecordingCallFactory callFactory = new RecordingCallFactory();
        WebhookService webhookService = new WebhookService(config, callFactory, null, null);
        webhookService.start();

        ChatListener chatListener = new ChatListener(null, null, webhookService, config);

        List<Player> online = new ArrayList<>();
        Player steve = createMockPlayer("Steve", UUID.randomUUID(), online);
        online.add(steve);

        AsyncPlayerChatEvent chatEvent = new AsyncPlayerChatEvent(true, steve, "Webhook message", new HashSet<>(online));
        chatListener.onPlayerChat(chatEvent);

        assertEquals(1, webhookService.getStatus(0L, true, true, false).outgoingQueueSize());
    }

    @Test
    void botModeMinecraftChatRoutesOnlyToBot() {
        String botYaml = """
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: "%s"
                features:
                  chat-relay: true
                  join-leave-notifications: true
                  server-status: true
                  player-list: true
                """.formatted(VALID_TOKEN, VALID_CHANNEL);
        ConfigManager config = createConfig(botYaml);
        DiscordService discordService = new DiscordService(null, config, null, null, null);
        ChatListener chatListener = new ChatListener(null, discordService, null, config);

        List<Player> online = new ArrayList<>();
        Player steve = createMockPlayer("Steve", UUID.randomUUID(), online);
        online.add(steve);

        AsyncPlayerChatEvent chatEvent = new AsyncPlayerChatEvent(true, steve, "Bot message", new HashSet<>(online));
        chatListener.onPlayerChat(chatEvent);

        assertEquals(1, discordService.getStatus(0L).outgoingQueueSize());
    }

    // =========================================================================
    // 2. Loop Prevention & Inbound Filter Tests
    // =========================================================================

    @Test
    void inboundFilterRejectsWebhookAndBotMessagesPreventingFeedbackLoops() {
        // Genuine user message in configured channel
        assertTrue(DiscordMessageFilter.shouldRelay(
                true, false, false, true, VALID_CHANNEL, VALID_CHANNEL));

        // MineBridge Webhook message in configured channel: MUST be ignored
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, false, true, true, VALID_CHANNEL, VALID_CHANNEL),
                "Webhook messages must be filtered out to prevent echo loops");

        // Bot message in configured channel: MUST be ignored
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, true, false, true, VALID_CHANNEL, VALID_CHANNEL),
                "Bot messages must be filtered out to prevent echo loops");

        // Message from a different Discord channel: MUST be ignored
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, false, false, true, "999999999999999999", VALID_CHANNEL),
                "Messages from non-configured channels must be ignored");
    }

    // =========================================================================
    // 3. Single-Path Event Notification Delivery Tests
    // =========================================================================

    @Test
    void hybridModeEventNotificationRoutesToWebhookByDefault() {
        ConfigManager config = createConfig(hybridConfigYaml("WEBHOOK"));
        RecordingCallFactory callFactory = new RecordingCallFactory();
        WebhookService webhookService = new WebhookService(config, callFactory, null, null);
        webhookService.start();

        DiscordService discordService = new DiscordService(null, config, null, null, null);
        DiscordEventNotifier notifier = new DiscordEventNotifier(discordService, webhookService, config);

        MessageEmbed embed = DiscordEmbedFactory.playerJoined("Alex", "has joined the server", false, "Footer");
        notifier.send(VALID_CHANNEL, embed);

        // Webhook receives event embed
        assertEquals(1, webhookService.getStatus(0L, true, true, false).outgoingQueueSize(),
                "Event embed must be delivered via Webhook when event-delivery is WEBHOOK");

        // Bot does NOT receive event embed (no duplicate notifications)
        assertEquals(0, discordService.getStatus(0L).outgoingQueueSize(),
                "Bot must NOT deliver event embed when event-delivery is WEBHOOK");
    }

    @Test
    void hybridModeEventNotificationRoutesToBotWhenConfigured() {
        ConfigManager config = createConfig(hybridConfigYaml("BOT"));
        RecordingCallFactory callFactory = new RecordingCallFactory();
        WebhookService webhookService = new WebhookService(config, callFactory, null, null);
        webhookService.start();

        List<Object> botSentMessages = new ArrayList<>();
        JDA mockJda = createMockJda(botSentMessages);
        DiscordService discordService = new DiscordService(null, config, s -> mockJda, null, null);
        discordService.start();
        DiscordEventNotifier notifier = new DiscordEventNotifier(discordService, webhookService, config);

        MessageEmbed embed = DiscordEmbedFactory.playerJoined("Alex", "has joined the server", false, "Footer");
        notifier.send(VALID_CHANNEL, embed);

        // Bot receives event embed
        assertEquals(1, botSentMessages.size(),
                "Event embed must be delivered via Bot when event-delivery is BOT");

        // Webhook does NOT receive event embed
        assertEquals(0, webhookService.getStatus(0L, true, true, false).outgoingQueueSize(),
                "Webhook must NOT deliver event embed when event-delivery is BOT");
    }

    // =========================================================================
    // 4. Failure Isolation Tests
    // =========================================================================

    @Test
    void webhookOutboundContinuesEvenIfDiscordBotGatewayIsDown() {
        ConfigManager config = createConfig(hybridConfigYaml("WEBHOOK"));
        RecordingCallFactory callFactory = new RecordingCallFactory();
        WebhookService webhookService = new WebhookService(config, callFactory, null, null);
        webhookService.start();

        // Discord service in stopped/disconnected state
        DiscordService discordService = new DiscordService(null, config, null, null, null);
        discordService.stop();

        ChatListener chatListener = new ChatListener(null, discordService, webhookService, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        chatListener.onPlayerChat(new AsyncPlayerChatEvent(true, alex, "Chat while bot is disconnected", new HashSet<>(online)));

        // Webhook delivery continues uninterrupted
        assertEquals(1, webhookService.getStatus(0L, true, true, false).outgoingQueueSize(),
                "Minecraft -> Discord chat via Webhook must work even if Bot gateway is disconnected");
    }

    // =========================================================================
    // 5. Configuration Validation & Defaults
    // =========================================================================

    @Test
    void hybridModeRequiresAllThreeCredentials() {
        ConfigManager manager = new ConfigManager(null);

        // 1. Missing bot token
        String noTokenYaml = """
                discord:
                  mode: HYBRID
                  channel-id: "%s"
                  webhook:
                    url: "%s"
                """.formatted(VALID_CHANNEL, VALID_WEBHOOK_URL);
        ConfigManager.Candidate noToken = manager.candidateFrom(parseYaml(noTokenYaml), null);
        assertFalse(noToken.valid());
        assertTrue(noToken.errors().stream().anyMatch(e -> e.contains("Discord bot token is missing or invalid")));

        // 2. Missing channel ID
        String noChannelYaml = """
                discord:
                  mode: HYBRID
                  bot-token: "%s"
                  webhook:
                    url: "%s"
                """.formatted(VALID_TOKEN, VALID_WEBHOOK_URL);
        ConfigManager.Candidate noChannel = manager.candidateFrom(parseYaml(noChannelYaml), null);
        assertFalse(noChannel.valid());
        assertTrue(noChannel.errors().stream().anyMatch(e -> e.contains("Discord channel ID is missing or invalid")));

        // 3. Missing webhook URL
        String noWebhookYaml = """
                discord:
                  mode: HYBRID
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);
        ConfigManager.Candidate noWebhook = manager.candidateFrom(parseYaml(noWebhookYaml), null);
        assertFalse(noWebhook.valid());
        assertTrue(noWebhook.errors().stream().anyMatch(e -> e.contains("discord.webhook.url is required when discord.mode is HYBRID")));

        // 4. Invalid webhook URL
        String badWebhookYaml = """
                discord:
                  mode: HYBRID
                  bot-token: "%s"
                  channel-id: "%s"
                  webhook:
                    url: "https://evil.com/fake-webhook"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);
        ConfigManager.Candidate badWebhook = manager.candidateFrom(parseYaml(badWebhookYaml), null);
        assertFalse(badWebhook.valid());
        assertTrue(badWebhook.errors().stream().anyMatch(e -> e.contains("discord.webhook.url must be a valid HTTPS Discord webhook URL")));

        // 5. All valid
        ConfigManager.Candidate valid = manager.candidateFrom(parseYaml(hybridConfigYaml("WEBHOOK")), null);
        assertTrue(valid.valid(), "Errors: " + valid.errors());
        assertEquals(IntegrationMode.HYBRID, valid.settings().mode());
        assertEquals(IntegrationMode.WEBHOOK, valid.settings().hybrid().eventDelivery());
    }

    @Test
    void hybridEventDeliveryDefaultsToWebhook() {
        String noDeliveryYaml = """
                discord:
                  mode: HYBRID
                  bot-token: "%s"
                  channel-id: "%s"
                  webhook:
                    url: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL, VALID_WEBHOOK_URL);
        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Candidate candidate = manager.candidateFrom(parseYaml(noDeliveryYaml), null);
        assertTrue(candidate.valid(), "Errors: " + candidate.errors());
        assertEquals(IntegrationMode.WEBHOOK, candidate.settings().hybrid().eventDelivery());
    }

    // =========================================================================
    // 6. Lifecycle & Reload Transitions
    // =========================================================================

    @Test
    void lifecycleManagerStartupInHybridMode() {
        ConfigManager config = createConfig(hybridConfigYaml("WEBHOOK"));
        RecordingCallFactory callFactory = new RecordingCallFactory();
        List<String> logs = new ArrayList<>();
        AtomicBoolean botSchedulerStarted = new AtomicBoolean(false);

        LifecycleManager manager = new LifecycleManager(
                config,
                () -> config.candidateFrom(parseYaml(hybridConfigYaml("WEBHOOK")), null),
                new LuckPermsHook(() -> true, uuid -> ""),
                cfg -> new DiscordService(null, cfg, null, logs::add, logs::add),
                cfg -> new WebhookService(cfg, callFactory, logs::add, logs::add),
                Runnable::run,
                () -> botSchedulerStarted.set(true),
                () -> botSchedulerStarted.set(false),
                logs::add,
                logs::add,
                () -> 60_000L);

        manager.start();

        assertNotNull(manager.getDiscordService(), "Discord service must be active in HYBRID mode");
        assertNotNull(manager.getWebhookService(), "Webhook service must be active in HYBRID mode");
        assertTrue(botSchedulerStarted.get(), "Bot scheduler must be started in HYBRID mode");
        assertTrue(logs.stream().anyMatch(l -> l.contains("MineBridge started in HYBRID mode.")));

        MineBridgeStatus status = manager.getStatus();
        assertEquals(IntegrationMode.HYBRID, status.mode());
        List<String> statusLines = status.formatLines();
        assertTrue(statusLines.stream().anyMatch(l -> l.contains("Mode: §fHYBRID")));
        assertTrue(statusLines.stream().anyMatch(l -> l.contains("Discord bot: ")));
        assertTrue(statusLines.stream().anyMatch(l -> l.contains("Webhook status: ")));
        assertTrue(statusLines.stream().anyMatch(l -> l.contains("Webhook destination: §aValid")));

        manager.shutdown();
        assertNull(manager.getDiscordService(), "Discord service must be null after shutdown");
        assertNull(manager.getWebhookService(), "Webhook service must be null after shutdown");
        assertFalse(botSchedulerStarted.get(), "Bot scheduler must be canceled after shutdown");
    }

    @Test
    void lifecycleManagerTransitionsBetweenAllModes() {
        RecordingCallFactory callFactory = new RecordingCallFactory();
        List<String> logs = new ArrayList<>();
        AtomicBoolean botSchedulerActive = new AtomicBoolean(false);

        // Start in BOT mode
        String botYaml = """
                discord:
                  mode: BOT
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);
        ConfigManager config = createConfig(botYaml);

        AtomicReference<ConfigManager.Candidate> currentCandidate = new AtomicReference<>(
                config.candidateFrom(parseYaml(botYaml), null));

        JDA mockJda = createMockJda(new ArrayList<>());
        LifecycleManager manager = new LifecycleManager(
                config,
                currentCandidate::get,
                new LuckPermsHook(() -> false, uuid -> ""),
                cfg -> new DiscordService(null, cfg, s -> mockJda, logs::add, logs::add),
                cfg -> new WebhookService(cfg, callFactory, logs::add, logs::add),
                Runnable::run,
                () -> botSchedulerActive.set(true),
                () -> botSchedulerActive.set(false),
                logs::add,
                logs::add,
                () -> 10_000L);

        manager.start();
        assertEquals(IntegrationMode.BOT, manager.getStatus().mode());
        assertNotNull(manager.getDiscordService());
        assertNull(manager.getWebhookService());

        // 1. Transition BOT -> HYBRID
        currentCandidate.set(config.candidateFrom(parseYaml(hybridConfigYaml("WEBHOOK")), null));
        AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
        manager.reload(reloadResult::set);
        assertNotNull(reloadResult.get());
        assertTrue(reloadResult.get().successful());
        assertEquals(IntegrationMode.HYBRID, manager.getStatus().mode());
        assertNotNull(manager.getDiscordService(), "Bot must remain active");
        assertNotNull(manager.getWebhookService(), "Webhook must be started");
        assertTrue(botSchedulerActive.get());

        // 2. Transition HYBRID -> WEBHOOK
        String webhookYaml = """
                discord:
                  mode: WEBHOOK
                  webhook:
                    url: "%s"
                """.formatted(VALID_WEBHOOK_URL);
        currentCandidate.set(config.candidateFrom(parseYaml(webhookYaml), null));
        manager.reload(reloadResult::set);
        assertTrue(reloadResult.get().successful());
        assertEquals(IntegrationMode.WEBHOOK, manager.getStatus().mode());
        assertNull(manager.getDiscordService(), "Bot must be stopped");
        assertNotNull(manager.getWebhookService(), "Webhook must remain active");
        assertFalse(botSchedulerActive.get(), "Bot scheduler must be canceled");

        // 3. Transition WEBHOOK -> HYBRID
        currentCandidate.set(config.candidateFrom(parseYaml(hybridConfigYaml("BOT")), null));
        manager.reload(reloadResult::set);
        assertTrue(reloadResult.get().successful());
        assertEquals(IntegrationMode.HYBRID, manager.getStatus().mode());
        assertNotNull(manager.getDiscordService(), "Bot must be started");
        assertNotNull(manager.getWebhookService(), "Webhook must remain active");
        assertTrue(botSchedulerActive.get(), "Bot scheduler must be started");

        // 4. Transition HYBRID -> BOT
        currentCandidate.set(config.candidateFrom(parseYaml(botYaml), null));
        manager.reload(reloadResult::set);
        assertTrue(reloadResult.get().successful());
        assertEquals(IntegrationMode.BOT, manager.getStatus().mode());
        assertNotNull(manager.getDiscordService(), "Bot must remain active");
        assertNull(manager.getWebhookService(), "Webhook must be stopped");
        assertTrue(botSchedulerActive.get());

        manager.shutdown();
    }

    private static YamlConfiguration parseYaml(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            try (var stream = HybridModeRoutingTest.class.getResourceAsStream("/config.yml")) {
                if (stream != null) {
                    YamlConfiguration defaults = new YamlConfiguration();
                    defaults.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                    config.setDefaults(defaults);
                }
            }
            config.load(new StringReader(yaml));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return config;
    }
}
