package com.minebridge.listener;

import com.minebridge.config.ConfigManager;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossWorldChatTest {

    private static Player createMockPlayer(
            String name,
            UUID uuid,
            String worldName,
            List<Player> worldPlayersRef,
            Collection<Player> serverOnlinePlayersRef,
            Map<String, Object> metadata) {

        World mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return worldName;
                    if ("getPlayers".equals(methodName)) return worldPlayersRef != null ? worldPlayersRef : List.of();
                    return null;
                });

        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
                    if ("getUniqueId".equals(methodName)) return uuid;
                    if ("isOnline".equals(methodName)) return true;
                    if ("getWorld".equals(methodName)) return mockWorld;
                    if ("hasMetadata".equals(methodName)) {
                        String key = (String) args[0];
                        return metadata != null && metadata.containsKey(key);
                    }
                    if ("getMetadata".equals(methodName)) {
                        String key = (String) args[0];
                        if (metadata != null && metadata.containsKey(key)) {
                            Object val = metadata.get(key);
                            MetadataValue metaValue = (MetadataValue) Proxy.newProxyInstance(
                                    MetadataValue.class.getClassLoader(),
                                    new Class<?>[]{MetadataValue.class},
                                    (mProxy, mMethod, mArgs) -> {
                                        if ("asString".equals(mMethod.getName())) return String.valueOf(val);
                                        if ("value".equals(mMethod.getName())) return val;
                                        return null;
                                    });
                            return List.of(metaValue);
                        }
                        return List.of();
                    }
                    if ("getServer".equals(methodName)) {
                        return (Server) Proxy.newProxyInstance(
                                Server.class.getClassLoader(),
                                new Class<?>[]{Server.class},
                                (sProxy, sMethod, sArgs) -> {
                                    if ("getOnlinePlayers".equals(sMethod.getName())) {
                                        return serverOnlinePlayersRef;
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
                    if ("toString".equals(methodName)) return "Player[" + name + " in " + worldName + "]";
                    return null;
                });
    }

    // =========================================================================
    // 1. GLOBAL Mode Tests
    // =========================================================================

    @Test
    void globalModeAcceptsChatWhenDeliveredToAllOnlinePlayersAcrossAllWorlds() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> world1Players = new ArrayList<>();
        List<Player> world2Players = new ArrayList<>();

        Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p3 = createMockPlayer("Charlie", UUID.randomUUID(), "world_nether", world2Players, allOnline, null);

        world1Players.addAll(List.of(p1, p2));
        world2Players.add(p3);
        allOnline.addAll(List.of(p1, p2, p3));

        ConfigManager.CrossWorldSettings globalSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.GLOBAL, Set.of());

        // Message received by all players across both worlds
        Set<Player> recipients = new HashSet<>(allOnline);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, p1, "Hello server!", recipients);

        assertTrue(ChatClassifier.isGlobalChat(event, globalSettings),
                "GLOBAL mode must accept chat delivered to all online players server-wide");
    }

    @Test
    void globalModeRejectsChatWhenDeliveredOnlyToSameWorldPlayers() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> world1Players = new ArrayList<>();
        List<Player> world2Players = new ArrayList<>();

        Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p3 = createMockPlayer("Charlie", UUID.randomUUID(), "world_nether", world2Players, allOnline, null);

        world1Players.addAll(List.of(p1, p2));
        world2Players.add(p3);
        allOnline.addAll(List.of(p1, p2, p3));

        ConfigManager.CrossWorldSettings globalSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.GLOBAL, Set.of());

        // Message received only by world1 players (p1, p2), not by p3 in nether
        Set<Player> recipients = new HashSet<>(world1Players);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, p1, "Hello world!", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event, globalSettings),
                "GLOBAL mode must reject chat that is restricted to a single world when others are online");
    }

    // =========================================================================
    // 2. PER_WORLD Mode Tests
    // =========================================================================

    @Test
    void perWorldModeAcceptsChatDeliveredToAllPlayersInSenderWorld() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> world1Players = new ArrayList<>();
        List<Player> world2Players = new ArrayList<>();

        Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p3 = createMockPlayer("Charlie", UUID.randomUUID(), "world_nether", world2Players, allOnline, null);

        world1Players.addAll(List.of(p1, p2));
        world2Players.add(p3);
        allOnline.addAll(List.of(p1, p2, p3));

        ConfigManager.CrossWorldSettings perWorldSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.PER_WORLD, Set.of());

        // Under PER_WORLD, recipients matching world1 players (p1, p2) is valid public chat for world1
        Set<Player> recipients = new HashSet<>(world1Players);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, p1, "Hello local world!", recipients);

        assertTrue(ChatClassifier.isGlobalChat(event, perWorldSettings),
                "PER_WORLD mode must accept chat delivered to all players in sender's world");
    }

    @Test
    void perWorldModeRejectsSubsetWithinSenderWorld() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> world1Players = new ArrayList<>();

        Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p3 = createMockPlayer("Charlie", UUID.randomUUID(), "world", world1Players, allOnline, null);

        world1Players.addAll(List.of(p1, p2, p3));
        allOnline.addAll(world1Players);

        ConfigManager.CrossWorldSettings perWorldSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.PER_WORLD, Set.of());

        // In world1 with 3 players, a party message is delivered to only p1 and p2 (missing p3)
        Set<Player> recipients = new HashSet<>(List.of(p1, p2));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, p1, "Party message in world", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event, perWorldSettings),
                "PER_WORLD mode must reject chat that does not reach all players in sender's world");
    }

    @Test
    void perWorldModeAcceptsSinglePlayerInWorld() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> world1Players = new ArrayList<>();
        List<Player> world2Players = new ArrayList<>();

        Player p1 = createMockPlayer("Solo", UUID.randomUUID(), "world_the_end", world1Players, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "world", world2Players, allOnline, null);

        world1Players.add(p1);
        world2Players.add(p2);
        allOnline.addAll(List.of(p1, p2));

        ConfigManager.CrossWorldSettings perWorldSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.PER_WORLD, Set.of());

        Set<Player> recipients = new HashSet<>(List.of(p1));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, p1, "Echo in the End...", recipients);

        assertTrue(ChatClassifier.isGlobalChat(event, perWorldSettings),
                "PER_WORLD mode must accept chat when sender is the only player in that world");
    }

    // =========================================================================
    // 3. FILTERED Mode Tests
    // =========================================================================

    @Test
    void filteredModeAcceptsAllowedWorldsAndRejectsUnlistedWorlds() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> overworldPlayers = new ArrayList<>();
        List<Player> minigamePlayers = new ArrayList<>();

        Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "survival", overworldPlayers, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "minigames", minigamePlayers, allOnline, null);

        overworldPlayers.add(p1);
        minigamePlayers.add(p2);
        allOnline.addAll(List.of(p1, p2));

        ConfigManager.CrossWorldSettings filteredSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.FILTERED, Set.of("survival", "survival_nether"));

        // p1 in "survival" (allowed world) sends server-wide chat
        Set<Player> recipients = new HashSet<>(allOnline);
        AsyncPlayerChatEvent p1Event = new AsyncPlayerChatEvent(true, p1, "Hello from survival!", recipients);
        assertTrue(ChatClassifier.isGlobalChat(p1Event, filteredSettings),
                "FILTERED mode must accept chat from whitelisted world");

        // p2 in "minigames" (unlisted world) sends chat
        AsyncPlayerChatEvent p2Event = new AsyncPlayerChatEvent(true, p2, "Hello from minigames!", recipients);
        assertFalse(ChatClassifier.isGlobalChat(p2Event, filteredSettings),
                "FILTERED mode must reject chat from non-whitelisted world");
    }

    // =========================================================================
    // 4. Strict Privacy Filtering Across All Cross-World Modes
    // =========================================================================

    @Test
    void strictPrivacyFilteringPreservedAcrossAllCrossWorldModes() {
        for (ConfigManager.CrossWorldMode mode : ConfigManager.CrossWorldMode.values()) {
            ConfigManager.CrossWorldSettings settings = new ConfigManager.CrossWorldSettings(
                    mode, Set.of("world"));

            List<Player> worldPlayers = new ArrayList<>();
            List<Player> allOnline = new ArrayList<>();
            Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "world", worldPlayers, allOnline, null);
            worldPlayers.add(p1);
            allOnline.add(p1);

            Set<Player> recipients = new HashSet<>(allOnline);

            // 1. Cancelled event
            AsyncPlayerChatEvent cancelledEvent = new AsyncPlayerChatEvent(true, p1, "Public message", recipients);
            cancelledEvent.setCancelled(true);
            assertFalse(ChatClassifier.isGlobalChat(cancelledEvent, settings),
                    "Cancelled events must be rejected in mode " + mode);

            // 2. Private message command attempts (/msg, /tell, /w)
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "/msg Bob secret", recipients), settings));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "/tell Bob secret", recipients), settings));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "/w Bob secret", recipients), settings));

            // 3. Channel shortcut prefixes
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "@party hello party", recipients), settings));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "@f faction talk", recipients), settings));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "!staff staff chatter", recipients), settings));

            // 4. Bracketed non-global channel prefixes
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "[Party] Hello team", recipients), settings));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "[Staff] Internal discussion", recipients), settings));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, p1, "[Guild] Hey guildies", recipients), settings));

            // 5. Active non-global channel metadata on sender
            Player staffPlayer = createMockPlayer(
                    "StaffUser", UUID.randomUUID(), "world", worldPlayers, allOnline,
                    Map.of("staffchat", true));
            assertFalse(ChatClassifier.isGlobalChat(
                    new AsyncPlayerChatEvent(true, staffPlayer, "Regular message while in staffchat", recipients),
                    settings), "Sender with active staffchat metadata must be rejected in mode " + mode);
        }
    }

    // =========================================================================
    // 5. Paper AsyncChatEvent Viewers Verification
    // =========================================================================

    @Test
    void paperAsyncChatEventViewersRespectedInCrossWorldModes() {
        List<Player> allOnline = new ArrayList<>();
        List<Player> world1Players = new ArrayList<>();
        List<Player> world2Players = new ArrayList<>();

        Player p1 = createMockPlayer("Alex", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p2 = createMockPlayer("Bob", UUID.randomUUID(), "world", world1Players, allOnline, null);
        Player p3 = createMockPlayer("Charlie", UUID.randomUUID(), "world_nether", world2Players, allOnline, null);

        world1Players.addAll(List.of(p1, p2));
        world2Players.add(p3);
        allOnline.addAll(List.of(p1, p2, p3));

        ConfigManager.CrossWorldSettings globalSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.GLOBAL, Set.of());
        ConfigManager.CrossWorldSettings perWorldSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.PER_WORLD, Set.of());
        ConfigManager.CrossWorldSettings filteredSettings = new ConfigManager.CrossWorldSettings(
                ConfigManager.CrossWorldMode.FILTERED, Set.of("world_nether"));

        // Global viewers (p1, p2, p3)
        Set<Player> allViewers = new HashSet<>(allOnline);
        assertTrue(ChatClassifier.isPaperGlobalChat(p1, "Paper global chat", false, allViewers, allOnline, globalSettings));

        // World1 viewers only (p1, p2)
        Set<Player> world1Viewers = new HashSet<>(world1Players);
        // Rejected in GLOBAL mode
        assertFalse(ChatClassifier.isPaperGlobalChat(p1, "Paper local chat", false, world1Viewers, allOnline, globalSettings));
        // Accepted in PER_WORLD mode
        assertTrue(ChatClassifier.isPaperGlobalChat(p1, "Paper local chat", false, world1Viewers, allOnline, perWorldSettings));

        // Filtered mode: p1 in "world" (unlisted) -> false
        assertFalse(ChatClassifier.isPaperGlobalChat(p1, "Paper filtered chat", false, allViewers, allOnline, filteredSettings));
        // Filtered mode: p3 in "world_nether" (listed) -> true
        assertTrue(ChatClassifier.isPaperGlobalChat(p3, "Paper nether chat", false, allViewers, allOnline, filteredSettings));
    }
}
