package com.minebridge.listener;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatClassifierTest {

    private static Player createMockPlayer(
            String name,
            UUID uuid,
            boolean online,
            Map<String, Object> metadata,
            Collection<Player> onlinePlayersRef) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) {
                        return name;
                    }
                    if ("getUniqueId".equals(methodName)) {
                        return uuid;
                    }
                    if ("isOnline".equals(methodName)) {
                        return online;
                    }
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
                                        if ("asString".equals(mMethod.getName())) {
                                            return String.valueOf(val);
                                        }
                                        if ("value".equals(mMethod.getName())) {
                                            return val;
                                        }
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
                    if ("hashCode".equals(methodName)) {
                        return uuid.hashCode();
                    }
                    if ("toString".equals(methodName)) {
                        return "Player[" + name + ", " + uuid + "]";
                    }
                    return null;
                });
    }

    private static Player createPlayer(String name, Collection<Player> serverOnlinePlayers) {
        return createMockPlayer(name, UUID.randomUUID(), true, null, serverOnlinePlayers);
    }

    private static Player createPlayerWithMetadata(
            String name,
            Map<String, Object> metadata,
            Collection<Player> serverOnlinePlayers) {
        return createMockPlayer(name, UUID.randomUUID(), true, metadata, serverOnlinePlayers);
    }

    // =========================================================================
    // 1. Genuine Global Chat Acceptance
    // =========================================================================

    @Test
    void acceptsNormalGlobalChatWithMultipleOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        Player charlie = createPlayer("Charlie", online);
        online.addAll(List.of(alex, bob, charlie));

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Hello everyone!", recipients);

        assertTrue(ChatClassifier.isGlobalChat(event), "Global chat to all players must be accepted");
    }

    @Test
    void acceptsNormalGlobalChatWithSinglePlayerOnline() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Testing SMP build alone", recipients);

        assertTrue(ChatClassifier.isGlobalChat(event), "Single player global chat must be accepted");
    }

    @Test
    void acceptsGlobalChatWithLuckPermsRankPrefixInFormat() {
        List<Player> online = new ArrayList<>();
        Player admin = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        online.addAll(List.of(admin, bob));

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, admin, "Welcome to the server!", recipients);
        event.setFormat("<[Admin] %1$s> %2$s");

        assertTrue(ChatClassifier.isGlobalChat(event),
                "Rank prefix in format must not block legitimate global chat when delivered to all players");
    }

    @Test
    void acceptsGlobalChatMessageContainingChannelWordsInNaturalProse() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        online.addAll(List.of(alex, bob));

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Hey guys, should we throw a party or join a faction?", recipients);

        assertTrue(ChatClassifier.isGlobalChat(event),
                "Natural sentences mentioning 'party' or 'faction' must not be falsely rejected");
    }

    // =========================================================================
    // 2. Cancellation Enforcement
    // =========================================================================

    @Test
    void rejectsCancelledChatEvent() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        online.addAll(List.of(alex, bob));

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "This was cancelled by another plugin", recipients);
        event.setCancelled(true);

        assertFalse(ChatClassifier.isGlobalChat(event), "Cancelled events must never be forwarded");
    }

    // =========================================================================
    // 3. Private Message Rejection (/msg, /tell, /whisper, etc.)
    // =========================================================================

    @Test
    void rejectsPrivateMessageWithRestrictedRecipients() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        Player charlie = createPlayer("Charlie", online);
        online.addAll(List.of(alex, bob, charlie));

        // Private message between Alex and Bob (Charlie is excluded)
        Set<Player> recipients = new HashSet<>(List.of(alex, bob));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Secret message for Bob", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event),
                "Private messages delivered to a subset of online players must be rejected");
    }

    @Test
    void rejectsDirectMessageCommands() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);

        String[] commands = {
                "/msg Bob secret",
                "/tell Bob secret",
                "/whisper Bob secret",
                "/w Bob secret",
                "/r secret",
                "/pm Bob secret",
                "/dm Bob secret"
        };

        for (String cmd : commands) {
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, cmd, recipients);
            assertFalse(ChatClassifier.isGlobalChat(event), "Direct message command must be rejected: " + cmd);
        }
    }

    @Test
    void rejectsPrivateMessageFormattedText() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);

        String[] pmTexts = {
                "[PM] to Bob: hello",
                "[MSG] to Bob: hello",
                "[Tell] to Bob: hello",
                "[Whisper] from Alex: secret",
                "-> Bob: hello there",
                "[me -> Bob] hello"
        };

        for (String text : pmTexts) {
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, alex, text, recipients);
            assertFalse(ChatClassifier.isGlobalChat(event), "Private message text pattern must be rejected: " + text);
        }
    }

    // =========================================================================
    // 4. Faction and Team Chat Rejection
    // =========================================================================

    @Test
    void rejectsFactionChatByRestrictedRecipients() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player ally = createPlayer("Ally", online);
        Player enemy = createPlayer("Enemy", online);
        online.addAll(List.of(alex, ally, enemy));

        Set<Player> factionRecipients = new HashSet<>(List.of(alex, ally));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Enemy near our base!", factionRecipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Faction chat excluding enemies must be rejected");
    }

    @Test
    void rejectsFactionChatByFormat() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Defend the core!", recipients);
        event.setFormat("[Faction] <%1$s> %2$s");

        assertFalse(ChatClassifier.isGlobalChat(event), "Faction channel format must be rejected");
    }

    @Test
    void rejectsFactionChatByPlayerMetadata() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayerWithMetadata(
                "Alex", Map.of("channel", "faction"), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Base coordinates: 100, 200", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Player in faction channel metadata must be rejected");
    }

    @Test
    void rejectsFactionShortcutPrefix() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "@f attack now", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Faction shortcut @f must be rejected");
    }

    @Test
    void rejectsTeamChatByFormatAndMetadata() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayerWithMetadata(
                "Alex", Map.of("chat_mode", "team"), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Pass the flag", recipients);
        event.setFormat("[Team] %1$s: %2$s");

        assertFalse(ChatClassifier.isGlobalChat(event), "Team chat must be rejected");
    }

    // =========================================================================
    // 5. Party and Guild Chat Rejection
    // =========================================================================

    @Test
    void rejectsPartyChatByRestrictedRecipients() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player member = createPlayer("Member", online);
        Player bystander = createPlayer("Bystander", online);
        online.addAll(List.of(alex, member, bystander));

        Set<Player> partyRecipients = new HashSet<>(List.of(alex, member));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Dungeon ready", partyRecipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Party chat excluding non-members must be rejected");
    }

    @Test
    void rejectsPartyChatByFormat() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Ready up", recipients);
        event.setFormat("[Party] %1$s: %2$s");

        assertFalse(ChatClassifier.isGlobalChat(event), "Party channel format must be rejected");
    }

    @Test
    void rejectsPartyChatByPlayerMetadata() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayerWithMetadata(
                "Alex", Map.of("active_channel", "party"), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Who has potions?", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Player in party channel metadata must be rejected");
    }

    @Test
    void rejectsPartyShortcutPrefix() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "@p heal me", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Party shortcut @p must be rejected");
    }

    @Test
    void rejectsGuildChatByFormatAndMetadata() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayerWithMetadata(
                "Alex", Map.of("chat_channel", "guild"), online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Guild war tonight", recipients);
        event.setFormat("[Guild] <%1$s> %2$s");

        assertFalse(ChatClassifier.isGlobalChat(event), "Guild chat must be rejected");
    }

    // =========================================================================
    // 6. Staff / Admin Private Chat Rejection
    // =========================================================================

    @Test
    void rejectsStaffChatByRestrictedRecipients() {
        List<Player> online = new ArrayList<>();
        Player mod1 = createPlayer("Mod1", online);
        Player mod2 = createPlayer("Mod2", online);
        Player player = createPlayer("Player", online);
        online.addAll(List.of(mod1, mod2, player));

        Set<Player> staffRecipients = new HashSet<>(List.of(mod1, mod2));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, mod1, "Investigating hacker at spawn", staffRecipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Staff chat excluding normal players must be rejected");
    }

    @Test
    void rejectsStaffChatByFormat() {
        List<Player> online = new ArrayList<>();
        Player mod = createPlayer("Mod", online);
        online.add(mod);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, mod, "Muting player X", recipients);
        event.setFormat("[Staff] %1$s: %2$s");

        assertFalse(ChatClassifier.isGlobalChat(event), "Staff channel format must be rejected");
    }

    @Test
    void rejectsStaffChatByPlayerMetadata() {
        List<Player> online = new ArrayList<>();
        Player mod = createPlayerWithMetadata(
                "Mod", Map.of("channel", "staff"), online);
        online.add(mod);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, mod, "Check user reports", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Player in staff channel metadata must be rejected");
    }

    @Test
    void rejectsStaffShortcutPrefix() {
        List<Player> online = new ArrayList<>();
        Player mod = createPlayer("Mod", online);
        online.add(mod);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, mod, "@s checking logs", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Staff shortcut @s must be rejected");
    }

    // =========================================================================
    // 7. Plugin-Generated, System, and Server Messages Rejection
    // =========================================================================

    @Test
    void rejectsCitizensNPC() {
        List<Player> online = new ArrayList<>();
        Player npc = createPlayerWithMetadata(
                "Shopkeeper", Map.of("NPC", true), online);
        online.add(npc);

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, npc, "Welcome to my shop!", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Citizens NPC messages must be rejected");
    }

    @Test
    void rejectsConsoleAndServerSenders() {
        List<Player> online = new ArrayList<>();
        Player console = createPlayer("CONSOLE", online);
        Player server = createPlayer("Server", online);
        online.addAll(List.of(console, server));

        Set<Player> recipients = new HashSet<>(online);
        AsyncPlayerChatEvent event1 = new AsyncPlayerChatEvent(
                true, console, "Server restarting in 5 minutes", recipients);
        AsyncPlayerChatEvent event2 = new AsyncPlayerChatEvent(
                true, server, "Server broadcast announcement", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event1), "CONSOLE sender must be rejected");
        assertFalse(ChatClassifier.isGlobalChat(event2), "Server sender must be rejected");
    }

    @Test
    void rejectsOfflineSender() {
        List<Player> online = new ArrayList<>();
        Player offlinePlayer = createMockPlayer(
                "Ghost", UUID.randomUUID(), false, null, online);

        Set<Player> recipients = new HashSet<>(List.of(offlinePlayer));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, offlinePlayer, "I am offline", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Offline player sender must be rejected");
    }

    @Test
    void rejectsEmptyOrNullRecipients() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        AsyncPlayerChatEvent eventEmpty = new AsyncPlayerChatEvent(
                true, alex, "Echo in the void", new HashSet<>());

        assertFalse(ChatClassifier.isGlobalChat(eventEmpty), "Empty recipients must be rejected");
    }

    @Test
    void rejectsSenderMissingFromRecipients() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        online.addAll(List.of(alex, bob));

        Set<Player> recipients = new HashSet<>(List.of(bob));
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, alex, "Sending without self-receiving", recipients);

        assertFalse(ChatClassifier.isGlobalChat(event), "Sender missing from recipients must be rejected");
    }

    @Test
    void rejectsNullOrBlankMessages() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);

        AsyncPlayerChatEvent eventNull = new AsyncPlayerChatEvent(true, alex, null, recipients);
        AsyncPlayerChatEvent eventEmpty = new AsyncPlayerChatEvent(true, alex, "", recipients);
        AsyncPlayerChatEvent eventWhitespace = new AsyncPlayerChatEvent(true, alex, "   \t\n  ", recipients);

        assertFalse(ChatClassifier.isGlobalChat(eventNull));
        assertFalse(ChatClassifier.isGlobalChat(eventEmpty));
        assertFalse(ChatClassifier.isGlobalChat(eventWhitespace));
    }

    @Test
    void rejectsCommandsAttemptedThroughChat() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Player> recipients = new HashSet<>(online);

        AsyncPlayerChatEvent eventCmd = new AsyncPlayerChatEvent(
                true, alex, "/help", recipients);

        assertFalse(ChatClassifier.isGlobalChat(eventCmd), "Commands starting with / must be rejected");
    }

    // =========================================================================
    // 8. Paper Chat Classification
    // =========================================================================

    @Test
    void paperGlobalChatAcceptsAllViewers() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        online.addAll(List.of(alex, bob));

        Set<Object> viewers = new HashSet<>(online);

        assertTrue(ChatClassifier.isPaperGlobalChat(alex, "Paper global chat!", false, viewers, online),
                "Paper chat with all online players as viewers must be accepted");
    }

    @Test
    void paperGlobalChatRejectsMissingViewer() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        Player bob = createPlayer("Bob", online);
        Player charlie = createPlayer("Charlie", online);
        online.addAll(List.of(alex, bob, charlie));

        // Charlie is missing from viewers
        Set<Object> viewers = new HashSet<>(List.of(alex, bob));

        assertFalse(ChatClassifier.isPaperGlobalChat(alex, "Private paper chat", false, viewers, online),
                "Paper chat missing an online player viewer must be rejected");
    }

    @Test
    void paperGlobalChatRejectsCancelledOrPrivateCommand() {
        List<Player> online = new ArrayList<>();
        Player alex = createPlayer("Alex", online);
        online.add(alex);

        Set<Object> viewers = new HashSet<>(online);

        assertFalse(ChatClassifier.isPaperGlobalChat(alex, "Cancelled", true, viewers, online),
                "Cancelled Paper chat must be rejected");
        assertFalse(ChatClassifier.isPaperGlobalChat(alex, "/msg Bob hey", false, viewers, online),
                "Private message command on Paper must be rejected");
    }
}
