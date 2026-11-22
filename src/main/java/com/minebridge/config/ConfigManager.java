package com.minebridge.config;

import com.minebridge.MineBridgePlugin;
import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.death.DeathCategory;
import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.hook.HookUtil;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final List<String> MESSAGE_KEYS = List.of(
            "messages.minecraft-to-discord",
            "messages.discord-to-minecraft",
            "messages.player-join",
            "messages.player-leave",
            "messages.server-status",
            "messages.player-list");
    private static final Map<String, Set<String>> MESSAGE_PLACEHOLDERS = Map.of(
            "messages.minecraft-to-discord",
            Set.of("player", "nickname", "display_name", "message", "timestamp", "server", "prefix", "suffix", "world", "world_alias", "uuid"),
            "messages.discord-to-minecraft",
            Set.of("player", "display_name", "username", "nickname", "channel", "message", "timestamp", "server", "prefix", "suffix"),
            "messages.player-join", Set.of("player", "nickname", "display_name", "timestamp", "server", "prefix", "suffix", "world", "world_alias"),
            "messages.player-leave", Set.of("player", "nickname", "display_name", "timestamp", "server", "prefix", "suffix", "world", "world_alias"),
            "messages.server-status", Set.of("online", "max", "server"),
            "messages.player-list", Set.of("online", "max", "players", "server"));
    private static final int MAX_TEMPLATE_LENGTH = 2000;
    private static final Pattern SNOWFLAKE_PATTERN = Pattern.compile("^[0-9]{17,20}$");
    private static final Pattern DISCORD_WEBHOOK_URL_PATTERN = Pattern.compile(
            "^https://(discord\\.com|discordapp\\.com|canary\\.discord\\.com|ptb\\.discord\\.com)/api/webhooks/([0-9]{17,20})/([A-Za-z0-9_-]+)(?:\\?.*)?$");
    private static final BigInteger MAX_UNSIGNED_64_BIT = new BigInteger("18446744073709551615");
    private static final Map<String, String> SAFE_MESSAGES = Map.of(
            "messages.minecraft-to-discord", "{prefix}{player}{suffix} » {message}",
            "messages.minecraft-to-discord-webhook", "{message}",
            "messages.discord-to-minecraft", "&8[&bDiscord&8] &7{player}&8: &f{message}",
            "messages.player-join", "🟢 **{player}** joined the server.",
            "messages.player-leave", "🔴 **{player}** left the server.",
            "messages.server-status", "&7Server &8| &a{online}&7/&f{max} players online",
            "messages.player-list", "&7Online players (&f{online}&7/&f{max}&7): {players}");
    private static final Set<String> DEATH_PLACEHOLDERS =
            Set.of("victim", "killer", "cause", "category", "server", "prefix");
    private static final Set<String> DEATH_EMBED_PLACEHOLDERS =
            Set.of("victim", "killer", "cause", "category", "message", "server", "prefix");
    private static final Set<String> ACHIEVEMENT_PLACEHOLDERS =
            Set.of("player", "title", "description", "category", "challenge", "server", "prefix");
    private static final Map<String, String> DEFAULT_ACHIEVEMENT_MESSAGES = Map.of(
            "normal", "&a✦ {player} has made the advancement [{title}]!",
            "challenge", "&6🏆 {player} has completed the challenge [{title}]!");

    private final Plugin plugin;
    private volatile ConfigSnapshot snapshot = ConfigSnapshot.defaultSnapshot();
    private volatile Settings settings = snapshot.toSettings();

    public enum CrossWorldMode {
        GLOBAL,
        PER_WORLD,
        FILTERED;

        public static CrossWorldMode fromString(String raw) {
            if (raw == null) {
                return GLOBAL;
            }
            try {
                return CrossWorldMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return GLOBAL;
            }
        }
    }

    public record CrossWorldSettings(CrossWorldMode mode, Set<String> enabledWorlds) {
        public CrossWorldSettings {
            mode = mode == null ? CrossWorldMode.GLOBAL : mode;
            enabledWorlds = enabledWorlds == null ? Set.of() : Set.copyOf(enabledWorlds);
        }
    }

    public static CrossWorldSettings defaultCrossWorldSettings() {
        return new CrossWorldSettings(CrossWorldMode.GLOBAL, Set.of());
    }

    public record WebhookSettings(String url, String username, String avatarUrl) {
        public WebhookSettings {
            url = url == null ? "" : url.trim();
            username = username == null || username.isBlank() ? "{prefix}{player}" : username.trim();
            avatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        }
    }

    public record EmbedFooterSettings(String text, String iconUrl) {
        public EmbedFooterSettings {
            text = (text == null || text.isBlank() || "MineBridge | Minecraft Events".equals(text))
                    ? DiscordEmbedFactory.DEFAULT_FOOTER : text.trim();
            iconUrl = iconUrl == null ? "" : iconUrl.trim();
        }
    }

    public static WebhookSettings defaultWebhookSettings() {
        return new WebhookSettings("", "{prefix}{player}", "");
    }

    public static EmbedFooterSettings defaultEmbedFooterSettings() {
        return new EmbedFooterSettings(DiscordEmbedFactory.DEFAULT_FOOTER, "");
    }

    public record HybridSettings(IntegrationMode eventDelivery) {
        public HybridSettings {
            eventDelivery = (eventDelivery == IntegrationMode.BOT) ? IntegrationMode.BOT : IntegrationMode.WEBHOOK;
        }
    }

    public static HybridSettings defaultHybridSettings() {
        return new HybridSettings(IntegrationMode.WEBHOOK);
    }

    public record MinecraftChatFormattingSettings(
            boolean formatConversion,
            boolean allowBold,
            boolean allowItalic,
            boolean allowUnderline,
            boolean allowStrikethrough,
            boolean allowObfuscated) {
        public com.minebridge.discord.MinecraftToDiscordFormatter.Options toOptions() {
            return new com.minebridge.discord.MinecraftToDiscordFormatter.Options(
                    formatConversion,
                    allowBold,
                    allowItalic,
                    allowUnderline,
                    allowStrikethrough,
                    allowObfuscated);
        }
    }

    public static MinecraftChatFormattingSettings defaultMinecraftChatFormattingSettings() {
        return new MinecraftChatFormattingSettings(true, true, true, true, true, true);
    }

    public record DiscordChatFormattingSettings(
            boolean formatConversion,
            boolean allowBold,
            boolean allowItalic,
            boolean allowUnderline,
            boolean allowStrikethrough,
            boolean allowCode,
            boolean showAttachments) {
        public com.minebridge.discord.DiscordToMinecraftFormatter.Options toOptions() {
            return new com.minebridge.discord.DiscordToMinecraftFormatter.Options(
                    formatConversion,
                    allowBold,
                    allowItalic,
                    allowUnderline,
                    allowStrikethrough,
                    allowCode,
                    true);
        }
    }

    public static DiscordChatFormattingSettings defaultDiscordChatFormattingSettings() {
        return new DiscordChatFormattingSettings(true, true, true, true, true, true, true);
    }

    public record ChatFormattingSettings(
            MinecraftChatFormattingSettings minecraftToDiscord,
            DiscordChatFormattingSettings discordToMinecraft) {
        public ChatFormattingSettings {
            minecraftToDiscord = minecraftToDiscord == null ? defaultMinecraftChatFormattingSettings() : minecraftToDiscord;
            discordToMinecraft = discordToMinecraft == null ? defaultDiscordChatFormattingSettings() : discordToMinecraft;
        }
    }

    public static ChatFormattingSettings defaultChatFormattingSettings() {
        return new ChatFormattingSettings(
                defaultMinecraftChatFormattingSettings(),
                defaultDiscordChatFormattingSettings());
    }

    public record NotificationSettings(
            boolean adminUpdateNotification,
            boolean adminUpdateNotificationOnJoin) {
        public static NotificationSettings defaultSettings() {
            return new NotificationSettings(true, true);
        }
    }

    public record StartupSettings(boolean banner) {
        public static StartupSettings defaultSettings() {
            return new StartupSettings(true);
        }
    }

    public record Settings(
            IntegrationMode mode,
            String botToken,
            String channelId,
            boolean chatEnabled,
            boolean eventsEnabled,
            Map<String, String> messages,
            boolean serverStatusEnabled,
            boolean playerListEnabled,
            DeathSettings deathMessages,
            AchievementSettings achievements,
            DiscordEventsSettings discordEvents,
            WebhookSettings webhook,
            String serverName,
            EmbedFooterSettings embedFooter,
            boolean updateCheckerEnabled,
            CrossWorldSettings crossWorld,
            HybridSettings hybrid,
            ChatFormattingSettings chatFormatting,
            NotificationSettings notifications,
            StartupSettings startup) {
        public Settings {
            mode = mode == null ? IntegrationMode.BOT : mode;
            messages = Map.copyOf(messages);
            webhook = webhook == null ? defaultWebhookSettings() : webhook;
            serverName = serverName == null ? "Minecraft Server" : serverName;
            embedFooter = embedFooter == null ? defaultEmbedFooterSettings() : embedFooter;
            crossWorld = crossWorld == null ? defaultCrossWorldSettings() : crossWorld;
            hybrid = hybrid == null ? defaultHybridSettings() : hybrid;
            chatFormatting = chatFormatting == null ? defaultChatFormattingSettings() : chatFormatting;
            notifications = notifications == null ? NotificationSettings.defaultSettings() : notifications;
            startup = startup == null ? StartupSettings.defaultSettings() : startup;
        }

        public Settings(
                IntegrationMode mode,
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents,
                WebhookSettings webhook,
                String serverName,
                EmbedFooterSettings embedFooter,
                boolean updateCheckerEnabled,
                CrossWorldSettings crossWorld,
                HybridSettings hybrid,
                ChatFormattingSettings chatFormatting) {
            this(mode, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    webhook, serverName, embedFooter, updateCheckerEnabled, crossWorld, hybrid,
                    chatFormatting, NotificationSettings.defaultSettings(), StartupSettings.defaultSettings());
        }

        public Settings(
                IntegrationMode mode,
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents,
                WebhookSettings webhook,
                String serverName,
                EmbedFooterSettings embedFooter,
                boolean updateCheckerEnabled,
                CrossWorldSettings crossWorld,
                HybridSettings hybrid) {
            this(mode, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    webhook, serverName, embedFooter, updateCheckerEnabled, crossWorld, hybrid,
                    defaultChatFormattingSettings());
        }

        public Settings(
                IntegrationMode mode,
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents,
                WebhookSettings webhook,
                String serverName,
                EmbedFooterSettings embedFooter,
                boolean updateCheckerEnabled,
                CrossWorldSettings crossWorld) {
            this(mode, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    webhook, serverName, embedFooter, updateCheckerEnabled, crossWorld, defaultHybridSettings());
        }

        public Settings(
                IntegrationMode mode,
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents,
                WebhookSettings webhook,
                String serverName,
                EmbedFooterSettings embedFooter,
                boolean updateCheckerEnabled) {
            this(mode, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    webhook, serverName, embedFooter, updateCheckerEnabled, defaultCrossWorldSettings());
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents) {
            this(IntegrationMode.BOT, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    defaultWebhookSettings(), "Minecraft Server", defaultEmbedFooterSettings(), true);
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled) {
            this(IntegrationMode.BOT, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    defaultDeathSettings(), defaultAchievementSettings(),
                    defaultDiscordEventsSettings(),
                    defaultWebhookSettings(), "Minecraft Server", defaultEmbedFooterSettings(), true);
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages) {
            this(IntegrationMode.BOT, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, defaultAchievementSettings(), defaultDiscordEventsSettings(),
                    defaultWebhookSettings(), "Minecraft Server", defaultEmbedFooterSettings(), true);
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements) {
            this(IntegrationMode.BOT, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled, deathMessages,
                    achievements, defaultDiscordEventsSettings(),
                    defaultWebhookSettings(), "Minecraft Server", defaultEmbedFooterSettings(), true);
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages) {
            this(IntegrationMode.BOT, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, true, true, defaultDeathSettings(), defaultAchievementSettings(),
                    defaultDiscordEventsSettings(),
                    defaultWebhookSettings(), "Minecraft Server", defaultEmbedFooterSettings(), true);
        }

        public Settings(
                IntegrationMode mode,
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents,
                WebhookSettings webhook,
                String serverName) {
            this(mode, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    webhook, serverName, defaultEmbedFooterSettings(), true);
        }

        public Settings(
                IntegrationMode mode,
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements,
                DiscordEventsSettings discordEvents,
                WebhookSettings webhook,
                String serverName,
                EmbedFooterSettings embedFooter) {
            this(mode, botToken, channelId, chatEnabled, eventsEnabled,
                    messages, serverStatusEnabled, playerListEnabled,
                    deathMessages, achievements, discordEvents,
                    webhook, serverName, embedFooter, true);
        }
    }

    public record DiscordEventsSettings(
            boolean enabled,
            boolean embedsEnabled,
            String footer,
            boolean playerEventsEnabled,
            String playerEventsChannelId,
            boolean joinEnabled,
            boolean leaveEnabled,
            boolean firstJoinEnabled,
            boolean achievementsEnabled,
            String achievementsChannelId,
            Map<String, Boolean> achievementCategories,
            boolean challengesEnabled,
            boolean deathsEnabled,
            String deathsChannelId,
            boolean playerKillsEnabled,
            boolean mobKillsEnabled,
            boolean environmentalDeathsEnabled,
            boolean serverEventsEnabled,
            String serverEventsChannelId,
            boolean serverStartEnabled,
            boolean serverStopEnabled) {

        public DiscordEventsSettings(
                boolean enabled,
                boolean embedsEnabled,
                String footer,
                boolean playerEventsEnabled,
                String playerEventsChannelId,
                boolean joinEnabled,
                boolean leaveEnabled,
                boolean firstJoinEnabled,
                boolean achievementsEnabled,
                String achievementsChannelId,
                Map<String, Boolean> achievementCategories,
                boolean challengesEnabled,
                boolean deathsEnabled,
                String deathsChannelId,
                boolean playerKillsEnabled,
                boolean mobKillsEnabled,
                boolean environmentalDeathsEnabled) {
            this(
                    enabled,
                    embedsEnabled,
                    footer,
                    playerEventsEnabled,
                    playerEventsChannelId,
                    joinEnabled,
                    leaveEnabled,
                    firstJoinEnabled,
                    achievementsEnabled,
                    achievementsChannelId,
                    achievementCategories,
                    challengesEnabled,
                    deathsEnabled,
                    deathsChannelId,
                    playerKillsEnabled,
                    mobKillsEnabled,
                    environmentalDeathsEnabled,
                    false,
                    "",
                    true,
                    true);
        }

        public DiscordEventsSettings {
            footer = (footer == null || "MineBridge | Minecraft Events".equals(footer))
                    ? DiscordEmbedFactory.DEFAULT_FOOTER : footer;
            playerEventsChannelId = playerEventsChannelId == null ? "" : playerEventsChannelId;
            achievementsChannelId = achievementsChannelId == null ? "" : achievementsChannelId;
            deathsChannelId = deathsChannelId == null ? "" : deathsChannelId;
            serverEventsChannelId = serverEventsChannelId == null ? "" : serverEventsChannelId;
            achievementCategories = Map.copyOf(achievementCategories);
        }
    }

    public record DeathSettings(
            boolean enabled,
            boolean minecraftEnabled,
            boolean minecraftBroadcast,
            boolean discordEnabled,
            boolean embedEnabled,
            String discordChannelId,
            String embedTitle,
            String embedDescription,
            int embedColor,
            Map<String, Boolean> categories,
            Map<String, String> messages) {
        public DeathSettings {
            discordChannelId = discordChannelId == null ? "" : discordChannelId;
            categories = Map.copyOf(categories);
            messages = Map.copyOf(messages);
        }
    }

    public record AchievementSettings(
            boolean enabled,
            boolean minecraftEnabled,
            boolean discordEnabled,
            boolean challengesEnabled,
            String discordChannelId,
            String messageTemplate,
            String challengeMessageTemplate,
            String embedTitle,
            String embedDescription,
            String challengeEmbedTitle,
            String challengeEmbedDescription,
            int embedColor,
            Map<String, Boolean> categories) {
        public AchievementSettings {
            discordChannelId = discordChannelId == null ? "" : discordChannelId;
            categories = Map.copyOf(categories);
        }
    }

    public record Candidate(Settings settings, ConfigSnapshot snapshot, List<String> errors) {
        public Candidate(Settings settings, List<String> errors) {
            this(settings, ConfigSnapshot.fromSettings(settings), errors);
        }

        public Candidate {
            errors = errors == null ? List.of() : List.copyOf(errors);
            if (snapshot == null && settings != null) {
                snapshot = ConfigSnapshot.fromSettings(settings);
            }
        }

        public boolean valid() {
            return settings != null && errors.isEmpty();
        }
    }

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public ConfigManager(MineBridgePlugin plugin) {
        this((Plugin) plugin);
    }

    public Candidate loadCandidate() {
        if (plugin == null || plugin.getDataFolder() == null) {
            return new Candidate(settings, snapshot, validationErrors());
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
        if (!configFile.exists()) {
            try {
                plugin.saveDefaultConfig();
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
            }
        }

        // 1. Check and perform migration for legacy single-file setup
        if (ConfigMigrationManager.isLegacy(dataFolder)) {
            ConfigMigrationManager.migrate(
                    dataFolder,
                    msg -> {
                        if (plugin.getLogger() != null) plugin.getLogger().info(msg);
                    },
                    msg -> {
                        if (plugin.getLogger() != null) plugin.getLogger().warning(msg);
                    }
            );
        }

        // 2. Load or create the four configuration files
        List<String> loadErrors = new ArrayList<>();
        File messagesFile = new File(dataFolder, ConfigDefaults.FILE_MESSAGES);
        File eventsFile = new File(dataFolder, ConfigDefaults.FILE_EVENTS);
        File advancedFile = new File(dataFolder, ConfigDefaults.FILE_ADVANCED);

        YamlConfiguration configYaml = ConfigLoader.loadOrCreate(
                configFile, ConfigDefaults.RESOURCE_CONFIG, loadErrors);
        YamlConfiguration messagesYaml = ConfigLoader.loadOrCreate(
                messagesFile, ConfigDefaults.RESOURCE_MESSAGES, loadErrors);
        YamlConfiguration eventsYaml = ConfigLoader.loadOrCreate(
                eventsFile, ConfigDefaults.RESOURCE_EVENTS, loadErrors);
        YamlConfiguration advancedYaml = ConfigLoader.loadOrCreate(
                advancedFile, ConfigDefaults.RESOURCE_ADVANCED, loadErrors);

        if (!loadErrors.isEmpty()) {
            return new Candidate(null, null, loadErrors);
        }

        String envToken = System.getenv("MINEBRIDGE_BOT_TOKEN");
        return candidateFromModular(configYaml, messagesYaml, eventsYaml, advancedYaml, envToken);
    }

    public Candidate candidateFrom(ConfigurationSection configuration, String environmentToken) {
        if (configuration == null) {
            return new Candidate(null, null, List.of("Configuration section is null."));
        }
        return candidateFromModular(configuration, configuration, configuration, configuration, environmentToken);
    }

    public Candidate candidateFromModular(
            ConfigurationSection config,
            ConfigurationSection messages,
            ConfigurationSection events,
            ConfigurationSection advanced,
            String environmentToken) {
        if (config == null) {
            config = new YamlConfiguration();
        }
        if (messages == null) {
            messages = new YamlConfiguration();
        }
        if (events == null) {
            events = new YamlConfiguration();
        }
        if (advanced == null) {
            advanced = new YamlConfiguration();
        }

        List<String> errors = new ArrayList<>();

        // 1. Mode
        Object modeValue = config.get("discord.mode");
        IntegrationMode mode = IntegrationMode.BOT;
        if (modeValue != null) {
            if (modeValue instanceof String stringMode) {
                mode = IntegrationMode.fromString(stringMode, null);
                if (mode == null) {
                    errors.add("discord.mode must be either BOT, WEBHOOK, or HYBRID.");
                    mode = IntegrationMode.BOT;
                }
            } else {
                errors.add("discord.mode must be a string.");
            }
        }

        // 2. Bot token & channel
        String configuredToken = environmentToken == null || environmentToken.isBlank()
                ? readStringOrDefault(config, "discord.bot-token", "", errors) : "";
        String channelId = readStringOrDefault(config, "discord.channel-id", "", errors);

        // 3. Features
        Object chatValue = config.get("features.chat-relay");
        Object eventsValue = config.get("features.join-leave");
        if (eventsValue == null) {
            eventsValue = config.get("features.join-leave-notifications");
        }
        Object serverStatusValue = config.get("features.server-status");
        Object playerListValue = config.get("features.player-list");

        if (config.contains("features.join-leave-notifications") || !config.contains("features.join-leave")) {
            errors.addAll(validateFeatureToggles(chatValue, eventsValue, serverStatusValue, playerListValue));
        } else {
            if (!(chatValue instanceof Boolean)) {
                errors.add("features.chat-relay must be true or false.");
            }
            if (!(eventsValue instanceof Boolean)) {
                errors.add("features.join-leave must be true or false.");
            }
            if (!(serverStatusValue instanceof Boolean)) {
                errors.add("features.server-status must be true or false.");
            }
            if (!(playerListValue instanceof Boolean)) {
                errors.add("features.player-list must be true or false.");
            }
        }

        Boolean chatEnabled = chatValue instanceof Boolean value ? value : null;
        Boolean eventsEnabled = eventsValue instanceof Boolean value ? value : null;
        Boolean serverStatusEnabled = serverStatusValue instanceof Boolean value ? value : null;
        Boolean playerListEnabled = playerListValue instanceof Boolean value ? value : null;

        boolean chatRelay = chatEnabled == null || chatEnabled;
        boolean joinLeave = eventsEnabled == null || eventsEnabled;
        boolean serverStatus = serverStatusEnabled == null || serverStatusEnabled;
        boolean playerList = playerListEnabled == null || playerListEnabled;

        // 4. Server name & update checker
        String serverName = readStringOrDefault(config, "server.name", "Minecraft Server", errors);

        Object updateCheckerSection = config.get("update-checker");
        if (updateCheckerSection != null && !(updateCheckerSection instanceof ConfigurationSection)) {
            errors.add("update-checker must be a configuration section.");
        }
        boolean updateCheckerEnabled = readBoolean(config, "update-checker.enabled", true, errors);

        // 5. Messages
        Map<String, String> messageMap = new HashMap<>();
        Map<String, Object> messageValues = new HashMap<>();
        for (String legacyKey : MESSAGE_KEYS) {
            String modularKey = legacyToModularMessageKey(legacyKey);
            Object value = null;
            Object modVal = messages.get(modularKey);
            if (modVal instanceof String) {
                value = modVal;
            }
            if (value == null) {
                Object legVal = messages.get(legacyKey);
                if (legVal instanceof String) {
                    value = legVal;
                }
            }
            if (value == null && config != messages) {
                Object configVal = config.get(legacyKey);
                if (configVal instanceof String) {
                    value = configVal;
                }
            }
            if (value == null) {
                value = SAFE_MESSAGES.get(legacyKey);
            }
            messageValues.put(legacyKey, value);
            if (value instanceof String stringValue) {
                messageMap.put(legacyKey, stringValue);
                messageMap.put(modularKey, stringValue);
            }
        }
        errors.addAll(validateMessageTemplates(messageValues));

        // 6. Deaths, Achievements, DiscordEvents
        DeathSettings deathMessages = readDeathSettingsModular(events, messages, config, errors);
        AchievementSettings achievements = readAchievementSettingsModular(events, messages, config, errors);
        DiscordEventsSettings discordEvents = readDiscordEventsSettingsModular(events, advanced, config, errors);

        // 7. Webhook, Hybrid, Footer
        WebhookSettings webhook = readWebhookSettingsModular(advanced, config, errors);
        HybridSettings hybrid = readHybridSettingsModular(advanced, config, errors);
        EmbedFooterSettings embedFooter = readEmbedFooterSettingsModular(advanced, config, discordEvents.footer(), errors);

        // 8. Cross world & Chat formatting
        CrossWorldSettings crossWorld = readCrossWorldSettingsModular(advanced, config);
        ChatFormattingSettings chatFormatting = readChatFormattingSettingsModular(advanced, config, errors);

        // 9. Validation
        String token = resolveBotToken(environmentToken, configuredToken);
        boolean requireChannelId = requiresDiscordChannel(
                chatRelay,
                joinLeave,
                deathMessages,
                achievements,
                discordEvents);
        errors.addAll(validateConfiguration(mode, token, channelId, requireChannelId, webhook));

        boolean adminUpdateNotification = readBooleanFromSections(config, advanced, "notifications.admin-update-notification", true, errors);
        boolean adminUpdateNotificationOnJoin = readBooleanFromSections(config, advanced, "notifications.admin-update-notification-on-join", true, errors);
        boolean startupBanner = readBooleanFromSections(config, advanced, "startup.banner", true, errors);
        NotificationSettings notifications = new NotificationSettings(adminUpdateNotification, adminUpdateNotificationOnJoin);
        StartupSettings startup = new StartupSettings(startupBanner);

        ConfigSnapshot parsedSnapshot = new ConfigSnapshot(
                mode,
                token,
                channelId,
                chatRelay,
                joinLeave,
                Map.copyOf(messageMap),
                serverStatus,
                playerList,
                deathMessages,
                achievements,
                discordEvents,
                webhook,
                serverName,
                embedFooter,
                updateCheckerEnabled,
                crossWorld,
                hybrid,
                chatFormatting,
                notifications,
                startup
        );

        Settings parsedSettings = parsedSnapshot.toSettings();
        return new Candidate(parsedSettings, parsedSnapshot, errors.isEmpty() ? List.of() : List.copyOf(errors));
    }

    private static String legacyToModularMessageKey(String legacyKey) {
        return switch (legacyKey) {
            case "messages.minecraft-to-discord" -> "chat.minecraft-to-discord";
            case "messages.minecraft-to-discord-webhook" -> "chat.minecraft-to-discord-webhook";
            case "messages.discord-to-minecraft" -> "chat.discord-to-minecraft";
            case "messages.player-join" -> "player.join";
            case "messages.player-leave" -> "player.leave";
            case "messages.server-status" -> "player.server-status";
            case "messages.player-list" -> "player.player-list";
            default -> legacyKey;
        };
    }

    private static DeathSettings readDeathSettingsModular(
            ConfigurationSection events,
            ConfigurationSection messages,
            ConfigurationSection config,
            List<String> errors) {
        boolean hasEventsDeaths = events.contains("deaths");
        boolean hasConfigDeaths = config != null && config.contains("death-messages");

        if (events.get("deaths") != null && !(events.get("deaths") instanceof ConfigurationSection)) {
            errors.add("deaths must be a configuration section.");
        } else if (!hasEventsDeaths && config != null && config.get("death-messages") != null
                && !(config.get("death-messages") instanceof ConfigurationSection)) {
            errors.add("death-messages must be a configuration section.");
        }

        if (hasConfigDeaths && !hasEventsDeaths) {
            validateSubsections(config, "death-messages",
                    List.of("minecraft", "discord", "categories", "messages"), errors);
        }

        Map<String, Boolean> categories = new HashMap<>();
        for (DeathCategory category : DeathCategory.values()) {
            String catKey = category.key();
            boolean catDefault = true;
            if (events.contains("deaths.categories." + catKey)) {
                catDefault = readBoolean(events, "deaths.categories." + catKey, true, errors);
            } else if (config != null) {
                catDefault = readBoolean(config, "death-messages.categories." + catKey, true, errors);
            }
            categories.put(catKey, catDefault);
        }

        Map<String, String> deathMsgMap = new HashMap<>();
        Map<String, Object> templates = new HashMap<>();
        for (DeathCategory category : DeathCategory.values()) {
            String key = category.key();
            String defaultMsg = defaultDeathMessage(key);
            String val = defaultMsg;
            if (messages.contains("deaths." + key)) {
                val = readStringOrDefault(messages, "deaths." + key, defaultMsg, errors);
            } else if (config != null && config.contains("death-messages.messages." + key)) {
                val = readStringOrDefault(config, "death-messages.messages." + key, defaultMsg, errors);
            }
            deathMsgMap.put(key, val);
            templates.put(key, val);
        }

        String title = readStringFrom(events, "deaths.discord.title",
                config != null ? "death-messages.discord.title" : null, "☠ Player death", errors);
        String description = readStringFrom(events, "deaths.discord.description",
                config != null ? "death-messages.discord.description" : null,
                "{message}\nCategory: {category}\nCause: {cause}", errors);

        templates.put("discord.title", title);
        templates.put("discord.description", description);
        errors.addAll(validateDeathMessageTemplates(templates));

        String deathChannelId = readOptionalChannelIdFrom(events, "deaths.discord.channel-id",
                config != null ? "death-messages.discord.channel-id" : null, errors);

        int embedColor = readHexColorFrom(events, "deaths.discord.color",
                config != null ? "death-messages.discord.color" : null, 0xD32F2F, errors);

        boolean enabled = readBooleanFrom(events, "deaths.enabled",
                config != null ? "death-messages.enabled" : null, true, errors);
        boolean mcEnabled = readBooleanFrom(events, "deaths.minecraft.enabled",
                config != null ? "death-messages.minecraft.enabled" : null, true, errors);
        boolean mcBroadcast = readBooleanFrom(events, "deaths.minecraft.broadcast",
                config != null ? "death-messages.minecraft.broadcast" : null, true, errors);
        boolean discordEnabled = readBooleanFrom(events, "deaths.discord.enabled",
                config != null ? "death-messages.discord.enabled" : null, true, errors);
        boolean embedEnabled = readBooleanFrom(events, "deaths.discord.embed",
                config != null ? "death-messages.discord.embed" : null, true, errors);

        return new DeathSettings(
                enabled,
                mcEnabled,
                mcBroadcast,
                discordEnabled,
                embedEnabled,
                deathChannelId,
                title,
                description,
                embedColor,
                categories,
                deathMsgMap);
    }

    private static AchievementSettings readAchievementSettingsModular(
            ConfigurationSection events,
            ConfigurationSection messages,
            ConfigurationSection config,
            List<String> errors) {
        boolean hasEventsAch = events.contains("advancements");
        boolean hasConfigAch = config != null && config.contains("achievements");

        if (events.get("advancements") != null && !(events.get("advancements") instanceof ConfigurationSection)) {
            errors.add("advancements must be a configuration section.");
        } else if (!hasEventsAch && config != null && config.get("achievements") != null
                && !(config.get("achievements") instanceof ConfigurationSection)) {
            errors.add("achievements must be a configuration section.");
        }

        if (hasConfigAch && !hasEventsAch) {
            validateSubsections(config, "achievements",
                    List.of("minecraft", "discord", "categories", "messages", "challenges"), errors);
        }

        Map<String, Boolean> categories = new HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            String catKey = category.key();
            boolean catDefault = true;
            if (events.contains("advancements.categories." + catKey)) {
                catDefault = readBoolean(events, "advancements.categories." + catKey, true, errors);
            } else if (config != null) {
                catDefault = readBoolean(config, "achievements.categories." + catKey, true, errors);
            }
            categories.put(catKey, catDefault);
        }

        String normalMessage = readStringFrom(messages, "advancements.normal",
                config != null ? "achievements.messages.normal" : null,
                DEFAULT_ACHIEVEMENT_MESSAGES.get("normal"), errors);
        String challengeMessage = readStringFrom(messages, "advancements.challenge",
                config != null ? "achievements.messages.challenge" : null,
                DEFAULT_ACHIEVEMENT_MESSAGES.get("challenge"), errors);

        String title = readStringFrom(events, "advancements.discord.title",
                config != null ? "achievements.discord.title" : null, "ADVANCEMENT UNLOCKED", errors);
        String description = readStringFrom(events, "advancements.discord.description",
                config != null ? "achievements.discord.description" : null,
                "{player} has unlocked {title}.\n{description}", errors);
        String challengeTitle = readStringFrom(events, "advancements.discord.challenge-title",
                config != null ? "achievements.discord.challenge-title" : null, "CHALLENGE COMPLETED", errors);
        String challengeDescription = readStringFrom(events, "advancements.discord.challenge-description",
                config != null ? "achievements.discord.challenge-description" : null,
                "{player} has completed {title}!\n{description}", errors);

        errors.addAll(validateAchievementTemplates(Map.of(
                "normal", normalMessage,
                "challenge", challengeMessage,
                "discord.title", title,
                "discord.description", description,
                "discord.challenge-title", challengeTitle,
                "discord.challenge-description", challengeDescription)));

        String channelId = readOptionalChannelIdFrom(events, "advancements.discord.channel-id",
                config != null ? "achievements.discord.channel-id" : null, errors);

        int embedColor = readHexColorFrom(events, "advancements.discord.color",
                config != null ? "achievements.discord.color" : null, 0x2196F3, errors);

        boolean enabled = readBooleanFrom(events, "advancements.enabled",
                config != null ? "achievements.enabled" : null, true, errors);
        boolean mcEnabled = readBooleanFrom(events, "advancements.minecraft.enabled",
                config != null ? "achievements.minecraft.enabled" : null, true, errors);
        boolean discordEnabled = readBooleanFrom(events, "advancements.discord.enabled",
                config != null ? "achievements.discord.enabled" : null, true, errors);
        boolean challengesEnabled = readBooleanFrom(events, "advancements.challenges.enabled",
                config != null ? "achievements.challenges.enabled" : null, true, errors);

        return new AchievementSettings(
                enabled,
                mcEnabled,
                discordEnabled,
                challengesEnabled,
                channelId,
                normalMessage,
                challengeMessage,
                title,
                description,
                challengeTitle,
                challengeDescription,
                embedColor,
                categories);
    }

    private static DiscordEventsSettings readDiscordEventsSettingsModular(
            ConfigurationSection events,
            ConfigurationSection advanced,
            ConfigurationSection config,
            List<String> errors) {
        boolean hasLegacyDiscordEvents = config != null && config.contains("discord-events");
        boolean hasModularEvents = events.contains("player-events") || events.contains("server-events");

        if (hasLegacyDiscordEvents && !hasModularEvents) {
            Object sectionValue = config.get("discord-events");
            if (sectionValue != null && !(sectionValue instanceof ConfigurationSection)) {
                errors.add("discord-events must be a configuration section.");
                return defaultDiscordEventsSettings();
            }
            validateSubsections(config, "discord-events",
                    List.of("embeds", "player-events", "achievements", "deaths", "server-events"), errors);
        }

        String footer = readStringFrom(advanced, "embeds.footer.default-text",
                config != null ? "discord-events.embeds.footer" : null,
                DiscordEmbedFactory.DEFAULT_FOOTER, errors);
        if ("MineBridge | Minecraft Events".equals(footer)) {
            footer = DiscordEmbedFactory.DEFAULT_FOOTER;
        }

        String playerChannel = readOptionalChannelIdFrom(events, "player-events.channel-id",
                config != null ? "discord-events.player-events.channel-id" : null, errors);
        String achievementChannel = readOptionalChannelIdFrom(events, "advancements.discord.channel-id",
                config != null ? "discord-events.achievements.channel-id" : null, errors);
        String deathsChannel = readOptionalChannelIdFrom(events, "deaths.discord.channel-id",
                config != null ? "discord-events.deaths.channel-id" : null, errors);
        String serverEventsChannel = readOptionalChannelIdFrom(events, "server-events.channel-id",
                config != null ? "discord-events.server-events.channel-id" : null, errors);

        Map<String, Boolean> achievementCategories = new HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            if (category != AdvancementCategory.OTHER) {
                boolean catVal = readBooleanFrom(events, "advancements.categories." + category.key(),
                        config != null ? "discord-events.achievements." + category.key() : null, true, errors);
                achievementCategories.put(category.key(), catVal);
            }
        }

        boolean enabled = readBooleanFrom(events, "player-events.enabled",
                config != null ? "discord-events.enabled" : null, true, errors);
        boolean embedsEnabled = readBooleanFrom(events, "player-events.embeds.enabled",
                config != null ? "discord-events.embeds.enabled" : null, true, errors);
        boolean playerEventsEnabled = readBooleanFrom(events, "player-events.enabled",
                config != null ? "discord-events.player-events.enabled" : null, true, errors);
        boolean joinEnabled = readBooleanFrom(events, "player-events.join",
                config != null ? "discord-events.player-events.join" : null, true, errors);
        boolean leaveEnabled = readBooleanFrom(events, "player-events.leave",
                config != null ? "discord-events.player-events.leave" : null, true, errors);
        boolean firstJoinEnabled = readBooleanFrom(events, "player-events.first-join",
                config != null ? "discord-events.player-events.first-join" : null, true, errors);

        boolean achievementsEnabled = readBooleanFrom(events, "advancements.enabled",
                config != null ? "discord-events.achievements.enabled" : null, true, errors);
        boolean challengesEnabled = readBooleanFrom(events, "advancements.challenges.enabled",
                config != null ? "discord-events.achievements.challenges" : null, true, errors);

        boolean deathsEnabled = readBooleanFrom(events, "deaths.enabled",
                config != null ? "discord-events.deaths.enabled" : null, true, errors);
        boolean playerKillsEnabled = readBooleanFrom(events, "deaths.categories.player-kill",
                config != null ? "discord-events.deaths.player-kill" : null, true, errors);
        boolean mobKillsEnabled = readBooleanFrom(events, "deaths.categories.mob-kill",
                config != null ? "discord-events.deaths.mob-kill" : null, true, errors);
        boolean envDeathsEnabled = readBooleanFrom(events, "deaths.categories.environmental",
                config != null ? "discord-events.deaths.environmental" : null, true, errors);

        boolean serverEventsEnabled = readBooleanFrom(events, "server-events.enabled",
                config != null ? "discord-events.server-events.enabled" : null, false, errors);
        boolean serverStartEnabled = readBooleanFrom(events, "server-events.start",
                config != null ? "discord-events.server-events.start" : null, true, errors);
        boolean serverStopEnabled = readBooleanFrom(events, "server-events.stop",
                config != null ? "discord-events.server-events.stop" : null, true, errors);

        return new DiscordEventsSettings(
                enabled,
                embedsEnabled,
                footer,
                playerEventsEnabled,
                playerChannel,
                joinEnabled,
                leaveEnabled,
                firstJoinEnabled,
                achievementsEnabled,
                achievementChannel,
                achievementCategories,
                challengesEnabled,
                deathsEnabled,
                deathsChannel,
                playerKillsEnabled,
                mobKillsEnabled,
                envDeathsEnabled,
                serverEventsEnabled,
                serverEventsChannel,
                serverStartEnabled,
                serverStopEnabled);
    }

    private static CrossWorldSettings readCrossWorldSettingsModular(
            ConfigurationSection advanced, ConfigurationSection config) {
        ConfigurationSection sec = advanced != null && advanced.contains("chat-routing.cross-world")
                ? advanced.getConfigurationSection("chat-routing.cross-world")
                : (config != null ? config.getConfigurationSection("chat.cross-world") : null);
        if (sec == null) {
            return defaultCrossWorldSettings();
        }
        String modeStr = sec.getString("mode", "GLOBAL");
        CrossWorldMode mode = CrossWorldMode.fromString(modeStr);
        List<String> worldsList = sec.getStringList("enabled-worlds");
        Set<String> enabledWorlds = worldsList != null ? Set.copyOf(worldsList) : Set.of();
        return new CrossWorldSettings(mode, enabledWorlds);
    }

    private static ChatFormattingSettings readChatFormattingSettingsModular(
            ConfigurationSection advanced, ConfigurationSection config, List<String> errors) {
        ConfigurationSection formattingSec = advanced != null && advanced.contains("formatting")
                ? advanced.getConfigurationSection("formatting")
                : (config != null ? config.getConfigurationSection("chat") : null);
        if (formattingSec == null) {
            return defaultChatFormattingSettings();
        }

        ConfigurationSection mcSection = formattingSec.getConfigurationSection("minecraft-to-discord");
        boolean mcConversion = readBoolean(mcSection, "format-conversion", true, errors);
        boolean mcBold = readBoolean(mcSection, "allow-bold", true, errors);
        boolean mcItalic = readBoolean(mcSection, "allow-italic", true, errors);
        boolean mcUnderline = readBoolean(mcSection, "allow-underline", true, errors);
        boolean mcStrikethrough = readBoolean(mcSection, "allow-strikethrough", true, errors);
        boolean mcObfuscated = readBoolean(mcSection, "allow-obfuscated", true, errors);
        MinecraftChatFormattingSettings mcFormatting = new MinecraftChatFormattingSettings(
                mcConversion, mcBold, mcItalic, mcUnderline, mcStrikethrough, mcObfuscated);

        ConfigurationSection discordSection = formattingSec.getConfigurationSection("discord-to-minecraft");
        boolean discordConversion = readBoolean(discordSection, "format-conversion", true, errors);
        boolean discordBold = readBoolean(discordSection, "allow-bold", true, errors);
        boolean discordItalic = readBoolean(discordSection, "allow-italic", true, errors);
        boolean discordUnderline = readBoolean(discordSection, "allow-underline", true, errors);
        boolean discordStrikethrough = readBoolean(discordSection, "allow-strikethrough", true, errors);
        boolean discordCode = readBoolean(discordSection, "allow-code", true, errors);
        boolean discordAttachments = readBoolean(discordSection, "show-attachments", true, errors);
        DiscordChatFormattingSettings discordFormatting = new DiscordChatFormattingSettings(
                discordConversion, discordBold, discordItalic, discordUnderline, discordStrikethrough, discordCode, discordAttachments);

        return new ChatFormattingSettings(mcFormatting, discordFormatting);
    }

    private static WebhookSettings readWebhookSettingsModular(
            ConfigurationSection advanced, ConfigurationSection config, List<String> errors) {
        if (config != null && config.get("discord.webhook") != null
                && !(config.get("discord.webhook") instanceof ConfigurationSection)) {
            errors.add("discord.webhook must be a configuration section.");
            return defaultWebhookSettings();
        }

        String url = readStringFrom(config, "discord.webhook.url",
                advanced != null ? "webhook.url" : null, "", errors);
        String username = readStringFrom(advanced, "webhook.username",
                config != null ? "discord.webhook.username" : null, "{prefix}{player}", errors);
        String avatarUrl = readStringFrom(advanced, "webhook.avatar-url",
                config != null ? "discord.webhook.avatar-url" : null, "", errors);

        return new WebhookSettings(url, username, avatarUrl);
    }

    private static HybridSettings readHybridSettingsModular(
            ConfigurationSection advanced, ConfigurationSection config, List<String> errors) {
        if (config != null && config.get("discord.hybrid") != null
                && !(config.get("discord.hybrid") instanceof ConfigurationSection)) {
            errors.add("discord.hybrid must be a configuration section.");
            return defaultHybridSettings();
        }

        Object deliveryValue = advanced != null && advanced.contains("hybrid.event-delivery")
                ? advanced.get("hybrid.event-delivery")
                : (config != null ? config.get("discord.hybrid.event-delivery") : null);

        IntegrationMode eventDelivery = IntegrationMode.WEBHOOK;
        if (deliveryValue != null) {
            if (deliveryValue instanceof String stringDelivery) {
                String normalized = stringDelivery.trim().toUpperCase(Locale.ROOT);
                if ("BOT".equals(normalized)) {
                    eventDelivery = IntegrationMode.BOT;
                } else if ("WEBHOOK".equals(normalized)) {
                    eventDelivery = IntegrationMode.WEBHOOK;
                } else {
                    String path = advanced != null && advanced.contains("hybrid.event-delivery")
                            ? "hybrid.event-delivery" : "discord.hybrid.event-delivery";
                    errors.add(path + " must be either WEBHOOK or BOT.");
                }
            } else {
                String path = advanced != null && advanced.contains("hybrid.event-delivery")
                        ? "hybrid.event-delivery" : "discord.hybrid.event-delivery";
                errors.add(path + " must be a string.");
            }
        }
        return new HybridSettings(eventDelivery);
    }

    private static EmbedFooterSettings readEmbedFooterSettingsModular(
            ConfigurationSection advanced, ConfigurationSection config, String legacyFooter, List<String> errors) {
        if (config != null && config.get("discord.embed") != null
                && !(config.get("discord.embed") instanceof ConfigurationSection)) {
            errors.add("discord.embed must be a configuration section.");
        }
        if (config != null && config.get("discord.embed.footer") != null
                && !(config.get("discord.embed.footer") instanceof ConfigurationSection)) {
            errors.add("discord.embed.footer must be a configuration section.");
        }

        String text = readStringFrom(advanced, "embeds.footer.text",
                config != null ? "discord.embed.footer.text" : null, "", errors);
        String iconUrl = readStringFrom(advanced, "embeds.footer.icon-url",
                config != null ? "discord.embed.footer.icon-url" : null, "", errors);

        if (text == null || text.isBlank() || "MineBridge | Minecraft Events".equals(text)) {
            text = (legacyFooter != null && !legacyFooter.isBlank() && !"MineBridge | Minecraft Events".equals(legacyFooter))
                    ? legacyFooter : DiscordEmbedFactory.DEFAULT_FOOTER;
        }
        return new EmbedFooterSettings(text, iconUrl);
    }

    public void commit(Candidate candidate) {
        if (candidate == null || !candidate.valid() || candidate.settings() == null) {
            throw new IllegalArgumentException("Cannot commit invalid MineBridge configuration.");
        }
        this.settings = candidate.settings();
        this.snapshot = candidate.snapshot() != null
                ? candidate.snapshot()
                : ConfigSnapshot.fromSettings(candidate.settings());
    }

    public Settings currentSettings() {
        return settings;
    }

    public ConfigSnapshot snapshot() {
        return snapshot;
    }

    public String getBotToken() {
        return settings != null ? settings.botToken() : "";
    }

    public String getChannelId() {
        return settings != null ? settings.channelId() : "";
    }

    public static String resolveBotToken(String environmentToken, String configuredToken) {
        if (environmentToken != null && !environmentToken.isBlank()) {
            return environmentToken.trim();
        }
        return configuredToken == null ? "" : configuredToken.trim();
    }

    public static List<String> validateFeatureToggles(
            Object chatRelay, Object joinLeaveNotifications) {
        return validateFeatureToggles(
                chatRelay, joinLeaveNotifications, Boolean.TRUE, Boolean.TRUE);
    }

    public static List<String> validateFeatureToggles(
            Object chatRelay,
            Object joinLeaveNotifications,
            Object serverStatus,
            Object playerList) {
        List<String> errors = new ArrayList<>();
        if (!(chatRelay instanceof Boolean)) {
            errors.add("features.chat-relay must be true or false.");
        }
        if (!(joinLeaveNotifications instanceof Boolean)) {
            errors.add("features.join-leave-notifications must be true or false.");
        }
        if (!(serverStatus instanceof Boolean)) {
            errors.add("features.server-status must be true or false.");
        }
        if (!(playerList instanceof Boolean)) {
            errors.add("features.player-list must be true or false.");
        }
        return List.copyOf(errors);
    }

    public static List<String> validateMessageTemplates(Map<String, ?> messages) {
        List<String> errors = new ArrayList<>();
        for (String key : MESSAGE_KEYS) {
            Object value = messages.get(key);
            if (!(value instanceof String stringValue)) {
                errors.add(key + " must be a string.");
            } else if (stringValue.isBlank()) {
                errors.add(key + " must not be empty.");
            } else if (stringValue.length() > MAX_TEMPLATE_LENGTH) {
                errors.add(key + " exceeds the 2000-character message limit.");
            } else {
                validateTemplatePlaceholders(key, stringValue, errors);
            }
        }
        return List.copyOf(errors);
    }

    public static List<String> validateDeathMessageTemplates(Map<String, ?> templates) {
        List<String> errors = new ArrayList<>();
        for (DeathCategory category : DeathCategory.values()) {
            validateDeathTemplate(
                    "death-messages.messages." + category.key(),
                    templates.get(category.key()),
                    DEATH_PLACEHOLDERS,
                    2000,
                    errors);
        }
        validateDeathTemplate(
                "death-messages.discord.title",
                templates.get("discord.title"),
                DEATH_EMBED_PLACEHOLDERS,
                256,
                errors);
        validateDeathTemplate(
                "death-messages.discord.description",
                templates.get("discord.description"),
                DEATH_EMBED_PLACEHOLDERS,
                4096,
                errors);
        return List.copyOf(errors);
    }

    public static List<String> validateAchievementTemplates(Map<String, ?> templates) {
        List<String> errors = new ArrayList<>();
        validateDeathTemplate(
                "achievements.messages.normal",
                templates.get("normal"),
                ACHIEVEMENT_PLACEHOLDERS,
                2000,
                errors);
        validateDeathTemplate(
                "achievements.messages.challenge",
                templates.get("challenge"),
                ACHIEVEMENT_PLACEHOLDERS,
                2000,
                errors);
        validateDeathTemplate(
                "achievements.discord.title",
                templates.get("discord.title"),
                ACHIEVEMENT_PLACEHOLDERS,
                256,
                errors);
        validateDeathTemplate(
                "achievements.discord.challenge-title",
                templates.get("discord.challenge-title"),
                ACHIEVEMENT_PLACEHOLDERS,
                256,
                errors);
        validateDeathTemplate(
                "achievements.discord.description",
                templates.get("discord.description"),
                ACHIEVEMENT_PLACEHOLDERS,
                4096,
                errors);
        validateDeathTemplate(
                "achievements.discord.challenge-description",
                templates.get("discord.challenge-description"),
                ACHIEVEMENT_PLACEHOLDERS,
                4096,
                errors);
        return List.copyOf(errors);
    }

    private static void validateDeathTemplate(
            String key,
            Object value,
            Set<String> allowed,
            int maxLength,
            List<String> errors) {
        if (!(value instanceof String template)) {
            errors.add(key + " must be a string.");
        } else if (template.isBlank()) {
            errors.add(key + " must not be empty.");
        } else if (template.length() > maxLength) {
            errors.add(key + " exceeds the " + maxLength + "-character limit.");
        } else {
            validateTemplatePlaceholders(key, template, allowed, errors);
        }
    }

    private static void validateTemplatePlaceholders(
            String key, String template, List<String> errors) {
        validateTemplatePlaceholders(key, template, MESSAGE_PLACEHOLDERS.get(key), errors);
    }

    private static void validateTemplatePlaceholders(
            String key, String template, Set<String> allowed, List<String> errors) {
        ConfigValidator.validateTemplatePlaceholders("", key, template, allowed, errors);
    }

    public static void validateSubsections(
            ConfigurationSection configuration,
            String parentPath,
            List<String> subSections,
            List<String> errors) {
        for (String section : subSections) {
            String path = parentPath + "." + section;
            Object value = configuration.get(path);
            if (value != null && !(value instanceof ConfigurationSection)) {
                errors.add(path + " must be a configuration section.");
            }
        }
    }

    public static String readOptionalChannelId(
            ConfigurationSection configuration,
            String path,
            List<String> errors) {
        String channelId = readStringOrDefault(configuration, path, "", errors);
        if (!channelId.isBlank() && !isValidChannelId(channelId)) {
            errors.add(path + " must be a 17-20 digit Discord channel ID.");
        }
        return channelId;
    }

    public static int readHexColor(
            ConfigurationSection configuration,
            String path,
            int defaultColor,
            List<String> errors) {
        if (configuration == null) {
            return defaultColor;
        }
        Object colorValue = configuration.get(path);
        if (colorValue == null) {
            return defaultColor;
        }
        if (!(colorValue instanceof String stringValue)) {
            errors.add(path + " must be a six-digit hex color string.");
            return defaultColor;
        }
        String normalized = stringValue.startsWith("#") ? stringValue.substring(1) : stringValue;
        if (!normalized.matches("^[0-9a-fA-F]{6}$")) {
            errors.add(path + " must be a six-digit hex color string.");
            return defaultColor;
        }
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            errors.add(path + " must be a six-digit hex color string.");
            return defaultColor;
        }
    }

    public static DeathSettings defaultDeathSettings() {
        Map<String, Boolean> categories = new HashMap<>();
        Map<String, String> messages = new HashMap<>();
        for (DeathCategory category : DeathCategory.values()) {
            categories.put(category.key(), true);
            messages.put(category.key(), defaultDeathMessage(category.key()));
        }
        return new DeathSettings(
                true,
                true,
                true,
                true,
                true,
                "",
                "☠ Player death",
                "{message}\nCategory: {category}\nCause: {cause}",
                0xD32F2F,
                categories,
                messages);
    }

    public static AchievementSettings defaultAchievementSettings() {
        Map<String, Boolean> categories = new HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            categories.put(category.key(), true);
        }
        return new AchievementSettings(
                true,
                true,
                true,
                true,
                "",
                DEFAULT_ACHIEVEMENT_MESSAGES.get("normal"),
                DEFAULT_ACHIEVEMENT_MESSAGES.get("challenge"),
                "ADVANCEMENT UNLOCKED",
                "{player} has unlocked {title}.\n{description}",
                "CHALLENGE COMPLETED",
                "{player} has completed {title}!\n{description}",
                0x2196F3,
                categories);
    }

    public static DiscordEventsSettings defaultDiscordEventsSettings() {
        Map<String, Boolean> achievementCategories = new HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            if (category != AdvancementCategory.OTHER) {
                achievementCategories.put(category.key(), true);
            }
        }
        return new DiscordEventsSettings(
                true,
                true,
                DiscordEmbedFactory.DEFAULT_FOOTER,
                true,
                "",
                true,
                true,
                true,
                true,
                "",
                achievementCategories,
                true,
                true,
                "",
                true,
                true,
                true,
                false,
                "",
                true,
                true);
    }

    public static String defaultDeathMessage(String category) {
        return switch (category) {
            case "player-kill", "mob-kill" -> "&c☠ {victim} was slain by {killer}";
            case "fall" -> "&c☠ {victim} fell to their death";
            case "lava" -> "&c☠ {victim} tried to swim in lava";
            case "fire" -> "&c☠ {victim} burned to death";
            case "drowning" -> "&c☠ {victim} drowned";
            case "void" -> "&c☠ {victim} fell out of the world";
            case "explosion" -> "&c☠ {victim} died in an explosion";
            case "environmental" -> "&c☠ {victim} died from {cause}";
            default -> "&c☠ {victim} died";
        };
    }

    public static boolean isValidBotToken(String token) {
        return ConfigValidator.isValidBotToken(token);
    }

    public static boolean isValidSnowflake(String snowflake) {
        return ConfigValidator.isValidSnowflake(snowflake);
    }

    public static boolean isValidChannelId(String channelId) {
        return ConfigValidator.isValidChannelId(channelId);
    }

    public static boolean requiresDiscordChannel(Settings settings) {
        if (settings == null) {
            return true;
        }
        return requiresDiscordChannel(
                settings.chatEnabled(),
                settings.eventsEnabled(),
                settings.deathMessages(),
                settings.achievements(),
                settings.discordEvents());
    }

    private static boolean requiresDiscordChannel(
            boolean chatEnabled,
            boolean eventsEnabled,
            DeathSettings deathMessages,
            AchievementSettings achievements,
            DiscordEventsSettings discordEvents) {
        if (chatEnabled) {
            return true;
        }
        if (discordEvents == null || !discordEvents.enabled() || !discordEvents.embedsEnabled()) {
            return false;
        }
        boolean playerEventsEnabled = eventsEnabled
                && discordEvents.playerEventsEnabled()
                && (discordEvents.joinEnabled()
                || discordEvents.leaveEnabled()
                || discordEvents.firstJoinEnabled());
        if (playerEventsEnabled) {
            return true;
        }
        boolean deathNotificationsEnabled = deathMessages != null
                && deathMessages.enabled()
                && deathMessages.discordEnabled()
                && discordEvents.deathsEnabled()
                && (discordEvents.playerKillsEnabled()
                || discordEvents.mobKillsEnabled()
                || discordEvents.environmentalDeathsEnabled());
        if (deathNotificationsEnabled) {
            return true;
        }
        return achievements != null
                && achievements.enabled()
                && achievements.discordEnabled()
                && discordEvents.achievementsEnabled();
    }

    public static boolean isValidWebhookUrl(String url) {
        return ConfigValidator.isValidWebhookUrl(url);
    }

    public static String maskWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        var matcher = DISCORD_WEBHOOK_URL_PATTERN.matcher(url.trim());
        if (matcher.matches()) {
            String host = matcher.group(1);
            String id = matcher.group(2);
            return "https://" + host + "/api/webhooks/" + id + "/********";
        }
        return "********";
    }

    public static List<String> validateConfiguration(
            IntegrationMode mode,
            String token,
            String channelId,
            boolean requireChannelId,
            WebhookSettings webhook) {
        List<String> errors = new ArrayList<>();
        if (mode == null || mode == IntegrationMode.BOT) {
            if (!isValidBotToken(token)) {
                errors.add(
                        "Discord bot token is missing or invalid; set MINEBRIDGE_BOT_TOKEN "
                                + "or discord.bot-token.");
            }
            boolean channelProvided = channelId != null && !channelId.isBlank();
            if (requireChannelId ? !isValidChannelId(channelId)
                    : (channelProvided && !isValidChannelId(channelId))) {
                errors.add(
                        "Discord channel ID is missing or invalid; set discord.channel-id "
                                + "to the configured text channel's 17-20 digit ID.");
            }
        } else if (mode == IntegrationMode.WEBHOOK) {
            String url = webhook != null ? webhook.url() : "";
            if (url == null || url.isBlank()) {
                errors.add("discord.webhook.url is required when discord.mode is WEBHOOK.");
            } else if (!isValidWebhookUrl(url)) {
                errors.add(
                        "discord.webhook.url must be a valid HTTPS Discord webhook URL "
                                + "(e.g. https://discord.com/api/webhooks/ID/TOKEN).");
            }
        } else if (mode == IntegrationMode.HYBRID) {
            if (!isValidBotToken(token)) {
                errors.add(
                        "Discord bot token is missing or invalid; set MINEBRIDGE_BOT_TOKEN "
                                + "or discord.bot-token.");
            }
            boolean channelProvided = channelId != null && !channelId.isBlank();
            if (requireChannelId ? !isValidChannelId(channelId)
                    : (channelProvided && !isValidChannelId(channelId))) {
                errors.add(
                        "Discord channel ID is missing or invalid; set discord.channel-id "
                                + "to the configured text channel's 17-20 digit ID.");
            }
            String url = webhook != null ? webhook.url() : "";
            if (url == null || url.isBlank()) {
                errors.add("discord.webhook.url is required when discord.mode is HYBRID.");
            } else if (!isValidWebhookUrl(url)) {
                errors.add(
                        "discord.webhook.url must be a valid HTTPS Discord webhook URL "
                                + "(e.g. https://discord.com/api/webhooks/ID/TOKEN).");
            }
        }
        return List.copyOf(errors);
    }

    public static List<String> validateConfiguration(String token, String channelId) {
        return validateConfiguration(token, channelId, true);
    }

    public static List<String> validateConfiguration(
            String token, String channelId, boolean requireChannelId) {
        return validateConfiguration(
                IntegrationMode.BOT, token, channelId, requireChannelId, defaultWebhookSettings());
    }

    public static List<String> validateConfiguration(Settings settings) {
        if (settings == null) {
            return validateConfiguration("", "", true);
        }
        return validateConfiguration(
                settings.mode(),
                settings.botToken(),
                settings.channelId(),
                requiresDiscordChannel(settings),
                settings.webhook());
    }

    public List<String> validationErrors() {
        return validateConfiguration(settings);
    }

    public IntegrationMode mode() {
        return settings != null ? settings.mode() : IntegrationMode.BOT;
    }

    public HybridSettings hybridSettings() {
        return settings != null ? settings.hybrid() : defaultHybridSettings();
    }

    public WebhookSettings webhook() {
        return settings != null ? settings.webhook() : defaultWebhookSettings();
    }

    public String serverName() {
        return settings != null ? settings.serverName() : "Minecraft Server";
    }

    public EmbedFooterSettings embedFooter() {
        return settings != null ? settings.embedFooter() : defaultEmbedFooterSettings();
    }

    public ChatFormattingSettings chatFormatting() {
        return settings != null ? settings.chatFormatting() : defaultChatFormattingSettings();
    }

    public boolean chatEnabled() {
        return settings != null && settings.chatEnabled();
    }

    public boolean eventsEnabled() {
        return settings != null && settings.eventsEnabled();
    }

    public boolean updateCheckerEnabled() {
        return settings != null && settings.updateCheckerEnabled();
    }

    public boolean startupBannerEnabled() {
        return settings == null || settings.startup() == null || settings.startup().banner();
    }

    public boolean adminUpdateNotificationEnabled() {
        return settings == null || settings.notifications() == null || settings.notifications().adminUpdateNotification();
    }

    public boolean adminUpdateNotificationOnJoinEnabled() {
        return settings == null || settings.notifications() == null || settings.notifications().adminUpdateNotificationOnJoin();
    }

    private static boolean readBooleanFromSections(
            ConfigurationSection primary,
            ConfigurationSection secondary,
            String path,
            boolean defaultValue,
            List<String> errors) {
        if (primary != null && primary.contains(path)) {
            Object val = primary.get(path);
            if (val instanceof Boolean b) {
                return b;
            }
            if (val instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s))) {
                return Boolean.parseBoolean(s);
            }
            errors.add(path + " must be a boolean (true/false).");
        } else if (secondary != null && secondary.contains(path)) {
            Object val = secondary.get(path);
            if (val instanceof Boolean b) {
                return b;
            }
            if (val instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s))) {
                return Boolean.parseBoolean(s);
            }
            errors.add(path + " must be a boolean (true/false).");
        }
        return defaultValue;
    }

    public String message(String key, String fallback) {
        if (settings == null || settings.messages() == null) {
            return fallback;
        }
        return settings.messages().getOrDefault(key, fallback);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public YamlConfiguration loadBundledDefaults() throws IOException, InvalidConfigurationException {
        InputStream stream = plugin != null ? plugin.getResource("config.yml") : null;
        if (stream == null) {
            stream = ConfigManager.class.getResourceAsStream("/config.yml");
        }
        if (stream == null) {
            throw new IOException("Bundled config.yml is missing.");
        }
        try (InputStream in = stream;
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(reader);
            return defaults;
        }
    }

    private static boolean readBoolean(
            ConfigurationSection configuration,
            String path,
            boolean defaultValue,
            List<String> errors) {
        return ConfigLoader.readBoolean(configuration, path, defaultValue, errors);
    }

    private static String readStringOrDefault(
            ConfigurationSection configuration,
            String path,
            String defaultValue,
            List<String> errors) {
        return ConfigLoader.readString(configuration, path, defaultValue, errors);
    }

    private static boolean readBooleanFrom(
            ConfigurationSection section,
            String primaryPath,
            String fallbackPath,
            boolean defaultValue,
            List<String> errors) {
        if (section == null) {
            return defaultValue;
        }
        if (section.contains(primaryPath)) {
            return readBoolean(section, primaryPath, defaultValue, errors);
        }
        if (fallbackPath != null && section.contains(fallbackPath)) {
            return readBoolean(section, fallbackPath, defaultValue, errors);
        }
        return defaultValue;
    }

    private static String readStringFrom(
            ConfigurationSection section,
            String primaryPath,
            String fallbackPath,
            String defaultValue,
            List<String> errors) {
        if (section == null) {
            return defaultValue != null ? defaultValue : "";
        }
        if (section.contains(primaryPath)) {
            return readStringOrDefault(section, primaryPath, defaultValue, errors);
        }
        if (fallbackPath != null && section.contains(fallbackPath)) {
            return readStringOrDefault(section, fallbackPath, defaultValue, errors);
        }
        return defaultValue != null ? defaultValue : "";
    }

    private static String readOptionalChannelIdFrom(
            ConfigurationSection section,
            String primaryPath,
            String fallbackPath,
            List<String> errors) {
        if (section == null) {
            return "";
        }
        String path = section.contains(primaryPath) ? primaryPath
                : (fallbackPath != null && section.contains(fallbackPath) ? fallbackPath : primaryPath);
        return readOptionalChannelId(section, path, errors);
    }

    private static int readHexColorFrom(
            ConfigurationSection section,
            String primaryPath,
            String fallbackPath,
            int defaultColor,
            List<String> errors) {
        if (section == null) {
            return defaultColor;
        }
        String path = section.contains(primaryPath) ? primaryPath
                : (fallbackPath != null && section.contains(fallbackPath) ? fallbackPath : primaryPath);
        if (!section.contains(path)) {
            return defaultColor;
        }
        return readHexColor(section, path, defaultColor, errors);
    }
}
