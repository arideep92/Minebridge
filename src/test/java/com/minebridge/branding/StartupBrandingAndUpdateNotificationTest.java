package com.minebridge.branding;

import com.minebridge.config.ConfigManager;
import com.minebridge.config.ConfigSnapshot;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.listener.PlayerEventListener;
import com.minebridge.update.UpdateChecker;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Startup Branding and Update Notification Tests")
class StartupBrandingAndUpdateNotificationTest {

    @BeforeEach
    @AfterEach
    void resetBanner() {
        StartupBanner.reset();
    }

    // --- Mock Helpers ---

    private static CommandSender createSender(boolean hasAdminPermission, List<String> receivedMessages) {
        String senderName = "Sender-" + UUID.randomUUID();
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        String perm = (String) args[0];
                        return UpdateChecker.ADMIN_PERMISSION.equals(perm) && hasAdminPermission;
                    }
                    if ("sendMessage".equals(method.getName())) {
                        receivedMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    if ("getName".equals(method.getName())) {
                        return senderName;
                    }
                    return null;
                });
    }

    private static Player createPlayerWithSpigot(
            boolean hasAdminPermission,
            List<BaseComponent> spigotComponents,
            List<String> plainMessages) {
        UUID uuid = UUID.randomUUID();
        String name = "AdminPlayer-" + uuid.toString().substring(0, 8);

        Player.Spigot spigotInstance = new Player.Spigot() {
            @Override
            public void sendMessage(BaseComponent component) {
                if (component != null) {
                    spigotComponents.add(component);
                }
            }

            @Override
            public void sendMessage(BaseComponent... components) {
                if (components != null) {
                    for (BaseComponent c : components) {
                        spigotComponents.add(c);
                    }
                }
            }
        };

        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        return UpdateChecker.ADMIN_PERMISSION.equals(args[0]) && hasAdminPermission;
                    }
                    if ("getUniqueId".equals(method.getName())) {
                        return uuid;
                    }
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    if ("hasPlayedBefore".equals(method.getName())) {
                        return true;
                    }
                    if ("spigot".equals(method.getName())) {
                        return spigotInstance;
                    }
                    if ("sendMessage".equals(method.getName())) {
                        plainMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    return null;
                });
    }

    // --- 1. Startup Banner Enabled vs Disabled & Deduplication ---

    @Test
    @DisplayName("Banner displays exactly once when enabled and avoids duplicate display")
    void bannerEnabledAndDeduplication() {
        assertFalse(StartupBanner.hasBeenDisplayed());

        List<String> loggedLines = new ArrayList<>();
        boolean firstDisplay = StartupBanner.display(null, loggedLines::add, "1.4.1", "BOT", true);

        assertTrue(firstDisplay, "First display call must return true");
        assertTrue(StartupBanner.hasBeenDisplayed(), "hasBeenDisplayed must be true after display");
        assertFalse(loggedLines.isEmpty(), "Lines must be emitted");

        // Second call must be blocked by the AtomicBoolean guard
        List<String> secondLoggedLines = new ArrayList<>();
        boolean secondDisplay = StartupBanner.display(null, secondLoggedLines::add, "1.4.1", "BOT", true);

        assertFalse(secondDisplay, "Second display call must return false");
        assertTrue(secondLoggedLines.isEmpty(), "Second display call must emit no lines");
    }

    @Test
    @DisplayName("Banner routes to Server consoleSender when available")
    void bannerRoutesToConsoleSender() {
        List<String> consoleMessages = new ArrayList<>();
        ConsoleCommandSender consoleMock = (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    if ("sendMessage".equals(method.getName())) {
                        consoleMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    return null;
                });

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getConsoleSender".equals(method.getName())) {
                        return consoleMock;
                    }
                    return null;
                });

        boolean displayed = StartupBanner.display(serverMock, null, "1.4.1", "WEBHOOK", true);
        assertTrue(displayed);
        assertFalse(consoleMessages.isEmpty());
        assertTrue(consoleMessages.stream().anyMatch(l -> l.contains("MineBridge v1.4.1")));
        assertTrue(consoleMessages.stream().anyMatch(l -> l.contains("WEBHOOK")));
    }

    @Test
    @DisplayName("Banner prefers plugin logger over console sender when both are provided")
    void bannerPrefersPluginLoggerOverConsoleSender() {
        List<String> consoleMessages = new ArrayList<>();
        ConsoleCommandSender consoleMock = (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    if ("sendMessage".equals(method.getName())) {
                        consoleMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    return null;
                });

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getConsoleSender".equals(method.getName())) {
                        return consoleMock;
                    }
                    return null;
                });

        List<String> loggerMessages = new ArrayList<>();
        boolean displayed = StartupBanner.display(serverMock, loggerMessages::add, "1.4.1", "BOT", true);

        assertTrue(displayed, "Banner display must succeed");
        assertTrue(StartupBanner.hasBeenDisplayed());
        assertFalse(loggerMessages.isEmpty(), "Logger must receive all banner lines");
        assertTrue(consoleMessages.isEmpty(), "Console sender must NOT be called when logger succeeds");
        assertTrue(loggerMessages.stream().anyMatch(l -> l.contains("MineBridge v1.4.1")));
    }

    @Test
    @DisplayName("Banner falls back to console sender when logger throws an exception")
    void bannerFallsBackToConsoleSenderWhenLoggerThrows() {
        List<String> consoleMessages = new ArrayList<>();
        ConsoleCommandSender consoleMock = (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    if ("sendMessage".equals(method.getName())) {
                        consoleMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    return null;
                });

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getConsoleSender".equals(method.getName())) {
                        return consoleMock;
                    }
                    return null;
                });

        // Failing logger
        java.util.function.Consumer<String> failingLogger = msg -> {
            throw new RuntimeException("Logger error");
        };

        boolean displayed = StartupBanner.display(serverMock, failingLogger, "1.4.1", "HYBRID", true);

        assertTrue(displayed, "Banner display must succeed via fallback");
        assertTrue(StartupBanner.hasBeenDisplayed());
        assertFalse(consoleMessages.isEmpty(), "Console sender must receive lines via fallback");
        assertTrue(consoleMessages.stream().anyMatch(l -> l.contains("HYBRID")));
        assertTrue(consoleMessages.stream().anyMatch(l -> l.contains("Mode:")));
    }

    @Test
    @DisplayName("Banner failure does not set AtomicBoolean guard and allows subsequent retry")
    void bannerFailureDoesNotLockDisplayGuard() {
        // Both logger and server are null
        boolean failedDisplay = StartupBanner.display((Server) null, (java.util.function.Consumer<String>) null, "1.4.1", "BOT", true);

        assertFalse(failedDisplay, "Display with no output sinks must return false");
        assertFalse(StartupBanner.hasBeenDisplayed(), "DISPLAYED guard must NOT be set to true on failure");

        // Subsequent attempt with valid logger succeeds
        List<String> loggerMessages = new ArrayList<>();
        boolean retryDisplay = StartupBanner.display(loggerMessages::add, "1.4.1", "BOT", true);

        assertTrue(retryDisplay, "Retry with valid logger must succeed");
        assertTrue(StartupBanner.hasBeenDisplayed());
        assertFalse(loggerMessages.isEmpty());
    }

    // --- 2. Startup Banner Content & Formatting ---

    @Test
    @DisplayName("Banner contains ASCII art, subtitle by Arixx, version, mode and status")
    void bannerContentLines() {
        List<String> uncolored = StartupBanner.getBannerLines("1.4.1", "HYBRID", true, false);

        // ASCII Art verification
        for (String artLine : StartupBanner.ASCII_ART) {
            assertTrue(uncolored.contains(artLine), "Uncolored banner must contain ASCII art line: " + artLine);
        }

        // Subtitle verification
        assertTrue(uncolored.contains(StartupBanner.SUBTITLE), "Banner must contain subtitle: by Arixx");

        // Version, mode and status
        assertTrue(uncolored.contains("MineBridge v1.4.1"), "Banner must contain MineBridge v1.4.1");
        assertTrue(uncolored.contains("Mode: HYBRID"), "Banner must contain Mode: HYBRID");
        assertTrue(uncolored.contains("Status: Enabled"), "Banner must contain Status: Enabled");

        // Leading 'v' sanitization (prevents 'MineBridge vv1.4.1')
        List<LeadingV> leadingVCheck = List.of(
                new LeadingV("v1.4.1", "MineBridge v1.4.1"),
                new LeadingV("V1.4.1", "MineBridge v1.4.1")
        );
        for (LeadingV v : leadingVCheck) {
            List<String> lines = StartupBanner.getBannerLines(v.input, "BOT", false, false);
            assertTrue(lines.contains(v.expected), "Banner version must be cleaned to: " + v.expected);
            assertTrue(lines.contains("Status: Disabled"));
        }
    }

    private record LeadingV(String input, String expected) {}

    @Test
    @DisplayName("Banner colored lines include exact ChatColor accents")
    void bannerColoredFormatting() {
        List<String> colored = StartupBanner.getBannerLines("1.4.1", "BOT", true, true);

        // ASCII art is AQUA
        assertTrue(colored.get(0).startsWith(ChatColor.AQUA.toString()));

        // Subtitle is DARK_GRAY
        assertTrue(colored.stream().anyMatch(l -> l.contains(ChatColor.DARK_GRAY.toString()) && l.contains("by Arixx")));

        // Title has WHITE and BOLD
        assertTrue(colored.stream().anyMatch(l -> l.contains(ChatColor.WHITE.toString()) && l.contains(ChatColor.BOLD.toString())));

        // Mode has AQUA
        assertTrue(colored.stream().anyMatch(l -> l.contains(ChatColor.AQUA.toString()) && l.contains("BOT")));

        // Status has GREEN when enabled
        assertTrue(colored.stream().anyMatch(l -> l.contains(ChatColor.GREEN.toString()) && l.contains("Enabled")));

        // Status has RED when disabled
        List<String> disabledColored = StartupBanner.getBannerLines("1.4.1", "BOT", false, true);
        assertTrue(disabledColored.stream().anyMatch(l -> l.contains(ChatColor.RED.toString()) && l.contains("Disabled")));
    }

    // --- 3. Version Status Console Display ---

    @Test
    @DisplayName("Console status display for up to date is neutral/green")
    void versionStatusConsoleDisplayUpToDate() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        List<String> logs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();

        checker.notifyConsole(UpdateChecker.UpdateResult.upToDate("1.4.1"), logs::add, warnLogs::add);

        assertEquals(2, logs.size());
        assertTrue(warnLogs.isEmpty(), "Up to date status must not emit warnings");
        assertEquals("§a[MineBridge] §7Version: §f1.4.1", logs.get(0));
        assertEquals("§a[MineBridge] §aUpdate status: Up to date", logs.get(1));
    }

    @Test
    @DisplayName("Console status display for update available is yellow/gold warning")
    void versionStatusConsoleDisplayUpdateAvailable() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        List<String> logs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();

        checker.notifyConsole(
                UpdateChecker.UpdateResult.available("1.4.1", "1.5.0", "https://modrinth.com/plugin/minebridge/version/1.5.0"),
                logs::add, warnLogs::add);

        assertEquals(3, logs.size());
        assertTrue(warnLogs.isEmpty());
        assertEquals("§6[MineBridge] §eInstalled version: §f1.4.1", logs.get(0));
        assertEquals("§6[MineBridge] §eLatest version: §a1.5.0", logs.get(1));
        assertEquals("§6[MineBridge] §eUpdate available! Download the latest release.", logs.get(2));
    }

    @Test
    @DisplayName("Console status display for failed check is informational and does not report outdated")
    void versionStatusConsoleDisplayFailedCheck() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        List<String> logs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();

        checker.notifyConsole(UpdateChecker.UpdateResult.failed("HTTP 503"), logs::add, warnLogs::add);

        assertEquals(2, logs.size());
        assertTrue(warnLogs.isEmpty(), "Failed check must be informational and not alarm admins with warning log level");
        assertEquals("§7[MineBridge] §7Version: §f1.4.1", logs.get(0));
        assertEquals("§e[MineBridge] §7Update check failed: §eHTTP 503", logs.get(1));
    }

    // --- 4. In-Game Administrator Notifications ---

    @Test
    @DisplayName("Admin with minebridge.admin receives clickable notification with Spigot components")
    void adminReceivesClickableNotification() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        UpdateChecker.UpdateResult available = UpdateChecker.UpdateResult.available(
                "1.4.1", "1.5.0", "https://modrinth.com/plugin/minebridge/version/1.5.0");

        List<BaseComponent> spigotComponents = new ArrayList<>();
        List<String> plainMessages = new ArrayList<>();
        Player admin = createPlayerWithSpigot(true, spigotComponents, plainMessages);

        checker.notifyIfAdmin(admin, available);

        // Verify Spigot components delivered
        assertFalse(spigotComponents.isEmpty(), "Admin player must receive Spigot TextComponent messages");
        assertEquals(3, spigotComponents.size());

        // Line 1: Header
        assertTrue(spigotComponents.get(0).toLegacyText().contains("An update is available!"));

        // Line 2: Version details
        assertTrue(spigotComponents.get(1).toLegacyText().contains("1.4.1"));
        assertTrue(spigotComponents.get(1).toLegacyText().contains("1.5.0"));

        // Line 3: Download link with ClickEvent and HoverEvent
        BaseComponent linkLine = spigotComponents.get(2);
        assertTrue(linkLine.toLegacyText().contains("Download:"));
        assertTrue(linkLine.toLegacyText().contains("https://modrinth.com/plugin/minebridge/version/1.5.0"));

        // Inspect extra components on linkLine to verify ClickEvent.Action.OPEN_URL
        boolean foundOpenUrl = false;
        boolean foundHoverText = false;
        if (linkLine.getExtra() != null) {
            for (BaseComponent extra : linkLine.getExtra()) {
                if (extra.getClickEvent() != null && extra.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                    assertEquals("https://modrinth.com/plugin/minebridge/version/1.5.0", extra.getClickEvent().getValue());
                    foundOpenUrl = true;
                }
                if (extra.getHoverEvent() != null && extra.getHoverEvent().getAction() == HoverEvent.Action.SHOW_TEXT) {
                    foundHoverText = true;
                }
            }
        }
        assertTrue(foundOpenUrl, "Link component must have OPEN_URL click event");
        assertTrue(foundHoverText, "Link component must have SHOW_TEXT hover event");
    }

    @Test
    @DisplayName("Non-admin does not receive any update notification")
    void nonAdminDoesNotReceiveNotification() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        UpdateChecker.UpdateResult available = UpdateChecker.UpdateResult.available(
                "1.4.1", "1.5.0", "https://modrinth.com/plugin/minebridge/version/1.5.0");

        List<BaseComponent> spigotComponents = new ArrayList<>();
        List<String> plainMessages = new ArrayList<>();
        Player normalPlayer = createPlayerWithSpigot(false, spigotComponents, plainMessages);

        checker.notifyIfAdmin(normalPlayer, available);

        assertTrue(spigotComponents.isEmpty(), "Non-admin must receive 0 Spigot components");
        assertTrue(plainMessages.isEmpty(), "Non-admin must receive 0 plain messages");
        assertFalse(checker.isNotified(normalPlayer));
    }

    @Test
    @DisplayName("Admin notification is deduplicated across multiple joins or calls")
    void adminNotificationDeduplication() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        UpdateChecker.UpdateResult available = UpdateChecker.UpdateResult.available(
                "1.4.1", "1.5.0", "https://modrinth.com/plugin/minebridge/version/1.5.0");

        List<BaseComponent> spigotComponents = new ArrayList<>();
        List<String> plainMessages = new ArrayList<>();
        Player admin = createPlayerWithSpigot(true, spigotComponents, plainMessages);

        // First call
        checker.notifyIfAdmin(admin, available);
        assertEquals(3, spigotComponents.size());
        assertTrue(checker.isNotified(admin));

        // Second call
        checker.notifyIfAdmin(admin, available);
        assertEquals(3, spigotComponents.size(), "No additional messages should be sent on repeated notification calls");
    }

    // --- 5. Configuration Toggles & Join Listener ---

    @Test
    @DisplayName("Join listener notifies admin only when enabled and update is cached")
    void joinListenerNotifiesAdminWhenCached() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        UpdateChecker.UpdateResult available = UpdateChecker.UpdateResult.available(
                "1.4.1", "1.5.0", "https://modrinth.com/plugin/minebridge/version/1.5.0");

        List<BaseComponent> adminComponents = new ArrayList<>();
        List<String> adminPlain = new ArrayList<>();
        Player admin = createPlayerWithSpigot(true, adminComponents, adminPlain);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(admin, "joined");

        // Case 1: Update NOT cached yet (player joins before update check completes)
        PlayerEventListener listenerNoCache = new PlayerEventListener(
                (DiscordEventNotifier) null, null, checker);
        listenerNoCache.onJoin(joinEvent);
        assertTrue(adminComponents.isEmpty(), "Admin joining before update check completes must receive nothing");

        // Simulate update check completion caching result
        checker.setCachedResult(available);
        assertTrue(checker.hasCachedUpdate());

        // Case 2: Player joins after update check completes -> receives notification
        listenerNoCache.onJoin(joinEvent);
        assertEquals(3, adminComponents.size(), "Admin joining after update check completes must receive notification");

        // Case 3: Re-joining does not deliver duplicate
        listenerNoCache.onJoin(joinEvent);
        assertEquals(3, adminComponents.size(), "Re-joining must not deliver duplicate notification");
    }

    @Test
    @DisplayName("Configuration toggles suppress notifications when false")
    void configurationTogglesSuppressNotifications() {
        UpdateChecker checker = new UpdateChecker("1.4.1", true);
        UpdateChecker.UpdateResult available = UpdateChecker.UpdateResult.available(
                "1.4.1", "1.5.0", "https://modrinth.com/plugin/minebridge/version/1.5.0");
        checker.setCachedResult(available);

        ConfigSnapshot baseSnapshot = ConfigSnapshot.defaultSnapshot();
        ConfigManager.Settings baseSettings = baseSnapshot.toSettings();

        // 1. admin-update-notification: false
        ConfigManager.Settings allDisabledSettings = new ConfigManager.Settings(
                baseSettings.mode(),
                baseSettings.botToken(),
                baseSettings.channelId(),
                baseSettings.chatEnabled(),
                baseSettings.eventsEnabled(),
                baseSettings.messages(),
                baseSettings.serverStatusEnabled(),
                baseSettings.playerListEnabled(),
                baseSettings.deathMessages(),
                baseSettings.achievements(),
                baseSettings.discordEvents(),
                baseSettings.webhook(),
                baseSettings.serverName(),
                baseSettings.embedFooter(),
                baseSettings.updateCheckerEnabled(),
                baseSettings.crossWorld(),
                baseSettings.hybrid(),
                baseSettings.chatFormatting(),
                new ConfigManager.NotificationSettings(false, true),
                baseSettings.startup()
        );
        ConfigManager configAllDisabled = new ConfigManager((org.bukkit.plugin.Plugin) null);
        configAllDisabled.commit(new ConfigManager.Candidate(
                allDisabledSettings, ConfigSnapshot.fromSettings(allDisabledSettings), List.of()));

        List<BaseComponent> adminComponents = new ArrayList<>();
        List<String> adminPlain = new ArrayList<>();
        Player admin = createPlayerWithSpigot(true, adminComponents, adminPlain);
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(admin, "joined");

        PlayerEventListener listenerDisabled = new PlayerEventListener(
                (DiscordEventNotifier) null, configAllDisabled, checker);
        listenerDisabled.onJoin(joinEvent);

        assertTrue(adminComponents.isEmpty(), "When adminUpdateNotification is disabled, no message on join");

        // 2. admin-update-notification-on-join: false
        ConfigManager.Settings joinDisabledSettings = new ConfigManager.Settings(
                baseSettings.mode(),
                baseSettings.botToken(),
                baseSettings.channelId(),
                baseSettings.chatEnabled(),
                baseSettings.eventsEnabled(),
                baseSettings.messages(),
                baseSettings.serverStatusEnabled(),
                baseSettings.playerListEnabled(),
                baseSettings.deathMessages(),
                baseSettings.achievements(),
                baseSettings.discordEvents(),
                baseSettings.webhook(),
                baseSettings.serverName(),
                baseSettings.embedFooter(),
                baseSettings.updateCheckerEnabled(),
                baseSettings.crossWorld(),
                baseSettings.hybrid(),
                baseSettings.chatFormatting(),
                new ConfigManager.NotificationSettings(true, false),
                baseSettings.startup()
        );
        ConfigManager configJoinDisabled = new ConfigManager((org.bukkit.plugin.Plugin) null);
        configJoinDisabled.commit(new ConfigManager.Candidate(
                joinDisabledSettings, ConfigSnapshot.fromSettings(joinDisabledSettings), List.of()));

        PlayerEventListener listenerJoinDisabled = new PlayerEventListener(
                (DiscordEventNotifier) null, configJoinDisabled, checker);
        listenerJoinDisabled.onJoin(joinEvent);

        assertTrue(adminComponents.isEmpty(), "When adminUpdateNotificationOnJoin is disabled, no message on join");
    }

    // --- 6. Configuration Defaults & Backward Compatibility ---

    @Test
    @DisplayName("ConfigSnapshot and ConfigManager default notifications and startup cleanly")
    void configDefaultsAndBackwardCompatibility() {
        ConfigSnapshot defaultSnapshot = ConfigSnapshot.defaultSnapshot();
        assertNotNull(defaultSnapshot.notifications());
        assertTrue(defaultSnapshot.notifications().adminUpdateNotification());
        assertTrue(defaultSnapshot.notifications().adminUpdateNotificationOnJoin());
        assertNotNull(defaultSnapshot.startup());
        assertTrue(defaultSnapshot.startup().banner());

        // 18-parameter constructor backward compatibility
        ConfigSnapshot legacySnapshot = new ConfigSnapshot(
                IntegrationMode.BOT,
                "token",
                "123456789",
                true,
                true,
                java.util.Map.of(),
                true,
                true,
                null,
                null,
                null,
                null,
                "Server",
                null,
                true,
                null,
                null,
                null
        );

        assertNotNull(legacySnapshot.notifications());
        assertTrue(legacySnapshot.notifications().adminUpdateNotification());
        assertTrue(legacySnapshot.notifications().adminUpdateNotificationOnJoin());
        assertNotNull(legacySnapshot.startup());
        assertTrue(legacySnapshot.startup().banner());

        // Snapshot toSettings roundtrip
        ConfigManager.Settings settings = legacySnapshot.toSettings();
        assertNotNull(settings.notifications());
        assertTrue(settings.notifications().adminUpdateNotification());
        assertTrue(settings.notifications().adminUpdateNotificationOnJoin());
        assertNotNull(settings.startup());
        assertTrue(settings.startup().banner());

        ConfigSnapshot roundtrip = ConfigSnapshot.fromSettings(settings);
        assertEquals(settings.notifications().adminUpdateNotification(), roundtrip.notifications().adminUpdateNotification());
        assertEquals(settings.notifications().adminUpdateNotificationOnJoin(), roundtrip.notifications().adminUpdateNotificationOnJoin());
        assertEquals(settings.startup().banner(), roundtrip.startup().banner());
    }

    @Test
    @DisplayName("Configuration startup.banner options: true, false, and missing default")
    void startupBannerConfigurationOptions() throws Exception {
        ConfigManager configManager = new ConfigManager((org.bukkit.plugin.Plugin) null);

        // 1. Missing startup.banner defaults to true
        org.bukkit.configuration.file.YamlConfiguration configMissing = new org.bukkit.configuration.file.YamlConfiguration();
        configMissing.load(new java.io.StringReader("discord:\n  mode: BOT\n"));
        ConfigManager.Candidate candidateMissing = configManager.candidateFrom(configMissing, "");
        assertNotNull(candidateMissing.settings().startup());
        assertTrue(candidateMissing.settings().startup().banner(), "Missing startup.banner must default to true");

        // 2. Explicit startup.banner: false
        org.bukkit.configuration.file.YamlConfiguration configFalse = new org.bukkit.configuration.file.YamlConfiguration();
        configFalse.load(new java.io.StringReader("startup:\n  banner: false\n"));
        ConfigManager.Candidate candidateFalse = configManager.candidateFrom(configFalse, "");
        assertNotNull(candidateFalse.settings().startup());
        assertFalse(candidateFalse.settings().startup().banner(), "Explicit startup.banner: false must evaluate to false");

        // 3. Explicit startup.banner: true
        org.bukkit.configuration.file.YamlConfiguration configTrue = new org.bukkit.configuration.file.YamlConfiguration();
        configTrue.load(new java.io.StringReader("startup:\n  banner: true\n"));
        ConfigManager.Candidate candidateTrue = configManager.candidateFrom(configTrue, "");
        assertNotNull(candidateTrue.settings().startup());
        assertTrue(candidateTrue.settings().startup().banner(), "Explicit startup.banner: true must evaluate to true");
    }
}
