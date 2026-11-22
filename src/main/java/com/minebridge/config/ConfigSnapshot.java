package com.minebridge.config;

import com.minebridge.discord.IntegrationMode;

import java.util.Map;

/**
 * Immutable thread-safe snapshot representing the active MineBridge configuration.
 */
public record ConfigSnapshot(
        IntegrationMode mode,
        String botToken,
        String channelId,
        boolean chatEnabled,
        boolean eventsEnabled,
        Map<String, String> messages,
        boolean serverStatusEnabled,
        boolean playerListEnabled,
        ConfigManager.DeathSettings deathMessages,
        ConfigManager.AchievementSettings achievements,
        ConfigManager.DiscordEventsSettings discordEvents,
        ConfigManager.WebhookSettings webhook,
        String serverName,
        ConfigManager.EmbedFooterSettings embedFooter,
        boolean updateCheckerEnabled,
        ConfigManager.CrossWorldSettings crossWorld,
        ConfigManager.HybridSettings hybrid,
        ConfigManager.ChatFormattingSettings chatFormatting) {

    public ConfigSnapshot {
        mode = mode == null ? IntegrationMode.BOT : mode;
        botToken = botToken == null ? "" : botToken;
        channelId = channelId == null ? "" : channelId;
        messages = messages == null ? Map.of() : Map.copyOf(messages);
        deathMessages = deathMessages == null ? ConfigManager.defaultDeathSettings() : deathMessages;
        achievements = achievements == null ? ConfigManager.defaultAchievementSettings() : achievements;
        discordEvents = discordEvents == null ? ConfigManager.defaultDiscordEventsSettings() : discordEvents;
        webhook = webhook == null ? ConfigManager.defaultWebhookSettings() : webhook;
        serverName = serverName == null || serverName.isBlank() ? "Minecraft Server" : serverName;
        embedFooter = embedFooter == null ? ConfigManager.defaultEmbedFooterSettings() : embedFooter;
        crossWorld = crossWorld == null ? ConfigManager.defaultCrossWorldSettings() : crossWorld;
        hybrid = hybrid == null ? ConfigManager.defaultHybridSettings() : hybrid;
        chatFormatting = chatFormatting == null ? ConfigManager.defaultChatFormattingSettings() : chatFormatting;
    }

    public static ConfigSnapshot defaultSnapshot() {
        return new ConfigSnapshot(
                IntegrationMode.BOT,
                "",
                "",
                true,
                true,
                Map.of(
                        "messages.minecraft-to-discord", "{prefix}**{player}**{suffix}: {message}",
                        "messages.discord-to-minecraft", "&9[Discord] &f{player}: {message}",
                        "messages.player-join", "🟢 **{player}** joined the server.",
                        "messages.player-leave", "🔴 **{player}** left the server.",
                        "messages.server-status", "&7Server &8| &a{online}&7/&f{max} players online",
                        "messages.player-list", "&7Online players (&f{online}&7/&f{max}&7): {players}"
                ),
                true,
                true,
                ConfigManager.defaultDeathSettings(),
                ConfigManager.defaultAchievementSettings(),
                ConfigManager.defaultDiscordEventsSettings(),
                ConfigManager.defaultWebhookSettings(),
                "Minecraft Server",
                ConfigManager.defaultEmbedFooterSettings(),
                true,
                ConfigManager.defaultCrossWorldSettings(),
                ConfigManager.defaultHybridSettings(),
                ConfigManager.defaultChatFormattingSettings()
        );
    }

    public static ConfigSnapshot fromSettings(ConfigManager.Settings s) {
        if (s == null) {
            return defaultSnapshot();
        }
        return new ConfigSnapshot(
                s.mode(),
                s.botToken(),
                s.channelId(),
                s.chatEnabled(),
                s.eventsEnabled(),
                s.messages(),
                s.serverStatusEnabled(),
                s.playerListEnabled(),
                s.deathMessages(),
                s.achievements(),
                s.discordEvents(),
                s.webhook(),
                s.serverName(),
                s.embedFooter(),
                s.updateCheckerEnabled(),
                s.crossWorld(),
                s.hybrid(),
                s.chatFormatting()
        );
    }

    /**
     * Converts this snapshot into a legacy ConfigManager.Settings object
     * ensuring 100% backward compatibility with existing listeners and services.
     */
    public ConfigManager.Settings toSettings() {
        return new ConfigManager.Settings(
                mode,
                botToken,
                channelId,
                chatEnabled,
                eventsEnabled,
                messages,
                serverStatusEnabled,
                playerListEnabled,
                deathMessages,
                achievements,
                discordEvents,
                webhook,
                serverName,
                embedFooter,
                updateCheckerEnabled,
                crossWorld,
                hybrid,
                chatFormatting
        );
    }
}
