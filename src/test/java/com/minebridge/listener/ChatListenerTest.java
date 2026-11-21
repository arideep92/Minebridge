package com.minebridge.listener;

import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatListenerTest {

    private static final String TEMPLATE = "**{player}**: {message}";
    private static final String TIMESTAMP = "2026-10-30T12:00:00Z";

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

    // =========================================================================
    // Legacy relayIfNonBlank tests
    // =========================================================================

    @Test
    void doesNotEnqueueEmptyChatContent() {
        List<String> sent = new ArrayList<>();

        assertFalse(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", "", TIMESTAMP, sent::add));

        assertEquals(List.of(), sent);
    }

    @Test
    void doesNotEnqueueWhitespaceOnlyChatContent() {
        List<String> sent = new ArrayList<>();

        assertFalse(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", " \t\r\n ", TIMESTAMP, sent::add));

        assertEquals(List.of(), sent);
    }

    @Test
    void relaysNormalTextWithExistingFormatting() {
        List<String> sent = new ArrayList<>();

        assertTrue(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", "hello world", TIMESTAMP, sent::add));

        assertEquals(List.of("**Alex**: hello world"), sent);
    }

    @Test
    void acceptsSurroundingWhitespaceAndPreservesExistingFormatterBehavior() {
        List<String> sent = new ArrayList<>();

        assertTrue(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", "  hello world  ", TIMESTAMP, sent::add));

        assertEquals(List.of("**Alex**: hello world"), sent);
    }

    @Test
    void doesNotEnqueueNullContent() {
        List<String> sent = new ArrayList<>();

        assertFalse(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", null, TIMESTAMP, sent::add));

        assertEquals(List.of(), sent);
    }

    @Test
    void relaysChatWithServerAndPrefixPlaceholders() {
        List<String> sent = new ArrayList<>();

        assertTrue(ChatListener.relayIfNonBlank(
                "[{server}] {prefix}{player}: {message}",
                "Alex",
                "hello world",
                TIMESTAMP,
                "Survival",
                "[VIP] ",
                sent::add));

        assertEquals(List.of("[Survival] \\[VIP\\] Alex: hello world"), sent);
    }

    @Test
    void preventsRecursiveExpansionInRelayIfNonBlank() {
        List<String> sent = new ArrayList<>();

        assertTrue(ChatListener.relayIfNonBlank(
                "[{server}] **{player}**: {message}",
                "Alex",
                "I love {server} with {prefix}",
                TIMESTAMP,
                "SMP",
                "[Mod] ",
                sent::add));

        assertEquals(List.of("[SMP] **Alex**: I love {server} with {prefix}"), sent);
    }

    // =========================================================================
    // Strict Global-Chat Synchronization End-to-End Tests
    // =========================================================================

    @Test
    void onPlayerChatRelaysLegitimateGlobalChat() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        ChatListener listener = new ChatListener(null, discord, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        Player bob = createMockPlayer("Bob", UUID.randomUUID(), online);
        online.addAll(List.of(alex, bob));

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, "Hello everyone!", recipients);

        listener.onPlayerChat(event);

        assertEquals(1, discord.getStatus(0L).outgoingQueueSize(),
                "Legitimate global chat must be queued for Discord delivery");
    }

    @Test
    void onPlayerChatRejectsCancelledEvent() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        ChatListener listener = new ChatListener(null, discord, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, "Cancelled message", recipients);
        event.setCancelled(true);

        listener.onPlayerChat(event);

        assertEquals(0, discord.getStatus(0L).outgoingQueueSize(),
                "Cancelled chat event must never be forwarded to Discord");
    }

    @Test
    void onPlayerChatRejectsPrivateChannelSubsetRecipients() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        ChatListener listener = new ChatListener(null, discord, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        Player bob = createMockPlayer("Bob", UUID.randomUUID(), online);
        Player charlie = createMockPlayer("Charlie", UUID.randomUUID(), online);
        online.addAll(List.of(alex, bob, charlie));

        // Party chat: only Alex and Bob receive it
        Set<Player> recipients = new HashSet<>(List.of(alex, bob));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, "Party chat", recipients);

        listener.onPlayerChat(event);

        assertEquals(0, discord.getStatus(0L).outgoingQueueSize(),
                "Private channel chat with restricted recipients must not be forwarded");
    }

    @Test
    void onPlayerChatDeduplicatesRapidIdenticalMessages() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        ChatListener listener = new ChatListener(null, discord, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event1 = new AsyncPlayerChatEvent(true, alex, "gg", recipients);
        AsyncPlayerChatEvent event2 = new AsyncPlayerChatEvent(true, alex, "gg", recipients);

        listener.onPlayerChat(event1);
        listener.onPlayerChat(event2);

        assertEquals(1, discord.getStatus(0L).outgoingQueueSize(),
                "Duplicate message within deduplication window must be dropped");
    }

    @Test
    void handlePaperChatDeduplicatesAgainstOnPlayerChat() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        ChatListener listener = new ChatListener(null, discord, config);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);

        // 1. Paper fires AsyncChatEvent
        listener.handlePaperChat(alex, "Hello from Paper");

        // 2. Paper subsequently fires legacy AsyncPlayerChatEvent
        AsyncPlayerChatEvent legacyEvent = new AsyncPlayerChatEvent(
                true, alex, "Hello from Paper", recipients);
        listener.onPlayerChat(legacyEvent);

        assertEquals(1, discord.getStatus(0L).outgoingQueueSize(),
                "Paper chat event followed by legacy event must produce only 1 message");
    }

    @Test
    void onPlayerChatRelaysWithLuckPermsPrefix() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        LuckPermsHook hook = new LuckPermsHook(() -> true, (player, uuid) -> "[VIP] ");
        ChatListener listener = new ChatListener(null, discord, null, config, hook);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, "Hello with prefix!", recipients);

        listener.onPlayerChat(event);

        assertEquals(1, discord.getStatus(0L).outgoingQueueSize(),
                "Message with prefix must be queued for Discord delivery");
    }

    @Test
    void onPlayerChatWithoutPrefixFormatsWithoutExtraSpaces() {
        ConfigManager config = createMockConfig(true, IntegrationMode.BOT);
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});
        LuckPermsHook hook = new LuckPermsHook(() -> true, (player, uuid) -> "");
        ChatListener listener = new ChatListener(null, discord, null, config, hook);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, "Hello no prefix!", recipients);

        listener.onPlayerChat(event);

        assertEquals(1, discord.getStatus(0L).outgoingQueueSize(),
                "Message without prefix must be queued for Discord delivery");
    }

    @Test
    void onPlayerChatRelaysWithFullMetadataService() {
        ConfigManager config = createMockConfigWithTemplate(
                "[{world_alias}] {prefix}{nickname}{suffix}: {message}");
        DiscordService discord = new DiscordService(null, config, null, msg -> {}, msg -> {});

        com.minebridge.hook.ChatMetadataService metadataService = new com.minebridge.hook.ChatMetadataService(
                new LuckPermsHook(() -> true, (p, u) -> "[Admin] ", (p, u) -> " [Staff]"),
                new com.minebridge.hook.VaultHook(() -> false, null, null, null),
                new com.minebridge.hook.EssentialsHook(() -> true, p -> "AlexTheGreat"),
                new com.minebridge.hook.MultiverseHook(() -> true, w -> "CreativePlots"),
                new com.minebridge.hook.PlaceholderApiHook(() -> false, null)
        );

        ChatListener listener = new ChatListener(null, discord, null, config, metadataService);

        List<Player> online = new ArrayList<>();
        Player alex = createMockPlayer("Alex", UUID.randomUUID(), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, "Hello with full metadata!", recipients);

        listener.onPlayerChat(event);

        assertEquals(1, discord.getStatus(0L).outgoingQueueSize(),
                "Message with full metadata service must be queued");
    }

    private static ConfigManager createMockConfigWithTemplate(String template) {
        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Settings settings = new ConfigManager.Settings(
                IntegrationMode.BOT,
                "fake-token",
                "123456789012345678",
                true,
                true,
                Map.of("messages.minecraft-to-discord", template),
                true,
                true,
                new ConfigManager.DeathSettings(false, false, false, false, false, "", "", "", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(false, false, false, false, "", "", "", "", "", "", "", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(true, true, "footer",
                        true, "", true, true, true, false, "", Map.of(), false, false, "", false, false, false),
                new ConfigManager.WebhookSettings("", "{prefix}{player}", ""),
                "Survival SMP",
                new ConfigManager.EmbedFooterSettings("", ""),
                false
        );
        manager.commit(new ConfigManager.Candidate(settings, List.of()));
        return manager;
    }

    // =========================================================================
    // Test Mocks
    // =========================================================================

    private static ConfigManager createMockConfig(boolean chatEnabled, IntegrationMode mode) {
        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Settings settings = new ConfigManager.Settings(
                mode,
                "fake-token",
                "123456789012345678",
                chatEnabled,
                true,
                Map.of("messages.minecraft-to-discord", "{prefix}**{player}**: {message}"),
                true,
                true,
                new ConfigManager.DeathSettings(false, false, false, false, false, "", "", "", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(false, false, false, false, "", "", "", "", "", "", "", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(true, true, "footer",
                        true, "", true, true, true, false, "", Map.of(), false, false, "", false, false, false),
                new ConfigManager.WebhookSettings("", "{prefix}{player}", ""),
                "Survival SMP",
                new ConfigManager.EmbedFooterSettings("", ""),
                false
        );
        manager.commit(new ConfigManager.Candidate(settings, List.of()));
        return manager;
    }
}
