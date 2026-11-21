package com.minebridge.listener;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.RelayMessageFormatter;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.ChatMetadataService;
import com.minebridge.hook.LuckPermsHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Instant;
import java.util.function.Consumer;

public final class ChatListener implements Listener {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final ChatMetadataService chatMetadata;
    private final ChatDeduplicator deduplicator;
    private DiscordService discord;
    private WebhookService webhook;

    public ChatListener(MineBridgePlugin plugin, DiscordService discord, ConfigManager config) {
        this(plugin, discord, null, config, new ChatMetadataService(), new ChatDeduplicator());
    }

    public ChatListener(MineBridgePlugin plugin, WebhookService webhook, ConfigManager config) {
        this(plugin, null, webhook, config, new ChatMetadataService(), new ChatDeduplicator());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config) {
        this(plugin, discord, webhook, config, new ChatMetadataService(), new ChatDeduplicator());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            ConfigManager config,
            LuckPermsHook luckPerms) {
        this(plugin, discord, null, config, new ChatMetadataService(luckPerms, null, null, null, null), new ChatDeduplicator());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            WebhookService webhook,
            ConfigManager config,
            LuckPermsHook luckPerms) {
        this(plugin, null, webhook, config, new ChatMetadataService(luckPerms, null, null, null, null), new ChatDeduplicator());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            LuckPermsHook luckPerms) {
        this(plugin, discord, webhook, config, new ChatMetadataService(luckPerms, null, null, null, null), new ChatDeduplicator());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            LuckPermsHook luckPerms,
            ChatDeduplicator deduplicator) {
        this(plugin, discord, webhook, config, new ChatMetadataService(luckPerms, null, null, null, null), deduplicator);
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            ChatMetadataService chatMetadata) {
        this(plugin, discord, webhook, config, chatMetadata, new ChatDeduplicator());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            ChatMetadataService chatMetadata,
            ChatDeduplicator deduplicator) {
        this.plugin = plugin;
        this.discord = discord;
        this.webhook = webhook;
        this.config = config;
        this.chatMetadata = chatMetadata != null ? chatMetadata : new ChatMetadataService();
        this.deduplicator = deduplicator != null ? deduplicator : new ChatDeduplicator();
    }

    public void updateServices(DiscordService discord, WebhookService webhook) {
        this.discord = discord;
        this.webhook = webhook;
    }

    public ChatDeduplicator getDeduplicator() {
        return deduplicator;
    }

    public ChatMetadataService getChatMetadata() {
        return chatMetadata;
    }

    public ConfigManager getConfig() {
        return config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        if (config == null || !config.chatEnabled()) {
            return;
        }

        ConfigManager.CrossWorldSettings crossWorld = config.currentSettings() != null
                ? config.currentSettings().crossWorld()
                : ConfigManager.defaultCrossWorldSettings();

        if (!ChatClassifier.isGlobalChat(event, crossWorld)) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        if (deduplicator.isDuplicate(player.getUniqueId(), message)) {
            return;
        }

        relayChatMessage(player, message);
    }

    public void handlePaperChat(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }

        if (config == null || !config.chatEnabled()) {
            return;
        }

        if (deduplicator.isDuplicate(player.getUniqueId(), message)) {
            return;
        }

        relayChatMessage(player, message);
    }

    public void relayChatMessage(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }

        ConfigManager.Settings current = config != null ? config.currentSettings() : null;
        String server = current != null ? current.serverName() : "";

        String prefix = chatMetadata.getPrefix(player);
        String suffix = chatMetadata.getSuffix(player);
        String nickname = chatMetadata.getNickname(player);
        String world = chatMetadata.getWorldName(player);
        String worldAlias = chatMetadata.getWorldAlias(player);

        String template = config != null
                ? config.message("messages.minecraft-to-discord", "{prefix}**{player}**{suffix}: {message}")
                : "{prefix}**{player}**{suffix}: {message}";
        // PlaceholderAPI processes trusted server template only (never user message content)
        template = chatMetadata.applyPlaceholders(player, template);

        if (current != null && current.mode() == IntegrationMode.WEBHOOK) {
            if (webhook == null) {
                return;
            }
            ConfigManager.WebhookSettings webhookSettings = current.webhook();
            String usernameTemplate = webhookSettings != null && !webhookSettings.username().isBlank()
                    ? webhookSettings.username()
                    : "{prefix}{player}";
            usernameTemplate = chatMetadata.applyPlaceholders(player, usernameTemplate);

            String resolvedUsername = RelayMessageFormatter.formatWebhookUsername(
                    usernameTemplate, player.getName(), nickname, server, prefix, suffix, world, worldAlias);

            String avatarTemplate = webhookSettings != null && !webhookSettings.avatarUrl().isBlank()
                    ? webhookSettings.avatarUrl()
                    : "";
            avatarTemplate = chatMetadata.applyPlaceholders(player, avatarTemplate);
            String resolvedAvatar = avatarTemplate.isBlank() ? "" : avatarTemplate
                    .replace("{player}", player.getName())
                    .replace("{nickname}", nickname)
                    .replace("{server}", server != null ? server : "")
                    .replace("{prefix}", prefix)
                    .replace("{suffix}", suffix)
                    .replace("{world}", world)
                    .replace("{world_alias}", worldAlias);

            relayIfNonBlank(
                    template,
                    player.getName(),
                    nickname,
                    message,
                    Instant.now().toString(),
                    server,
                    prefix,
                    suffix,
                    world,
                    worldAlias,
                    formatted -> webhook.send(resolvedUsername, resolvedAvatar, formatted));
        } else {
            if (discord == null) {
                return;
            }
            relayIfNonBlank(
                    template,
                    player.getName(),
                    nickname,
                    message,
                    Instant.now().toString(),
                    server,
                    prefix,
                    suffix,
                    world,
                    worldAlias,
                    discord::send);
        }
    }

    public static boolean relayIfNonBlank(
            String template,
            String player,
            String message,
            String timestamp,
            Consumer<String> sender) {
        return relayIfNonBlank(template, player, message, timestamp, "", "", sender);
    }

    public static boolean relayIfNonBlank(
            String template,
            String player,
            String message,
            String timestamp,
            String server,
            String prefix,
            Consumer<String> sender) {
        return relayIfNonBlank(template, player, player, message, timestamp, server, prefix, "", "", "", sender);
    }

    public static boolean relayIfNonBlank(
            String template,
            String player,
            String nickname,
            String message,
            String timestamp,
            String server,
            String prefix,
            String suffix,
            String world,
            String worldAlias,
            Consumer<String> sender) {
        if (message == null || message.isBlank()) {
            return false;
        }
        sender.accept(RelayMessageFormatter.formatForDiscord(
                template, player, nickname, message, timestamp, server, prefix, suffix, world, worldAlias));
        return true;
    }
}
