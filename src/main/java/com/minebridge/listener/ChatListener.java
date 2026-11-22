package com.minebridge.listener;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.MinecraftToDiscordFormatter;
import com.minebridge.discord.RelayMessageFormatter;
import com.minebridge.discord.SkinAvatarService;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.ChatMetadataService;
import com.minebridge.hook.LuckPermsHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.function.Consumer;

public final class ChatListener implements Listener {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final ChatMetadataService chatMetadata;
    private final ChatDeduplicator deduplicator;
    private final SkinAvatarService skinAvatarService;
    private volatile DiscordService discord;
    private volatile WebhookService webhook;

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
        this(plugin, discord, webhook, config, chatMetadata, new ChatDeduplicator(), new SkinAvatarService());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            ChatMetadataService chatMetadata,
            ChatDeduplicator deduplicator) {
        this(plugin, discord, webhook, config, chatMetadata, deduplicator, new SkinAvatarService());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            ChatMetadataService chatMetadata,
            SkinAvatarService skinAvatarService) {
        this(plugin, discord, webhook, config, chatMetadata, new ChatDeduplicator(), skinAvatarService);
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            ChatMetadataService chatMetadata,
            ChatDeduplicator deduplicator,
            SkinAvatarService skinAvatarService) {
        this.plugin = plugin;
        this.discord = discord;
        this.webhook = webhook;
        this.config = config;
        this.chatMetadata = chatMetadata != null ? chatMetadata : new ChatMetadataService();
        this.deduplicator = deduplicator != null ? deduplicator : new ChatDeduplicator();
        this.skinAvatarService = skinAvatarService != null ? skinAvatarService : new SkinAvatarService();
    }

    public void updateServices(DiscordService discord, WebhookService webhook) {
        this.discord = discord;
        this.webhook = webhook;
    }

    public SkinAvatarService getSkinAvatarService() {
        return skinAvatarService;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event != null && event.getPlayer() != null) {
            skinAvatarService.invalidate(event.getPlayer().getUniqueId());
            if (webhook != null && webhook.getSkinAvatarService() != null) {
                webhook.getSkinAvatarService().invalidate(event.getPlayer().getUniqueId());
            }
        }
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
        String uuidStr = player.getUniqueId() != null ? player.getUniqueId().toString() : "";

        ConfigManager.MinecraftChatFormattingSettings mcFormatting =
                current != null && current.chatFormatting() != null
                        ? current.chatFormatting().minecraftToDiscord()
                        : ConfigManager.defaultMinecraftChatFormattingSettings();

        boolean isWebhookDelivery = (current != null && (current.mode() == IntegrationMode.WEBHOOK || current.mode() == IntegrationMode.HYBRID));
        String templateKey = isWebhookDelivery ? "messages.minecraft-to-discord-webhook" : "messages.minecraft-to-discord";
        String defaultTemplate = isWebhookDelivery ? "{message}" : "{prefix}{player}{suffix} » {message}";
        String template = config != null
                ? config.message(templateKey, defaultTemplate)
                : defaultTemplate;
        // PlaceholderAPI processes trusted server template only (never user message content)
        template = chatMetadata.applyPlaceholders(player, template);

        if (current != null && (current.mode() == IntegrationMode.WEBHOOK || current.mode() == IntegrationMode.HYBRID)) {
            if (webhook == null) {
                return;
            }
            ConfigManager.WebhookSettings webhookSettings = current.webhook();
            String usernameTemplate = webhookSettings != null && !webhookSettings.username().isBlank()
                    ? webhookSettings.username()
                    : "{prefix}{player}";
            usernameTemplate = chatMetadata.applyPlaceholders(player, usernameTemplate);

            String resolvedUsername = RelayMessageFormatter.formatWebhookUsername(
                    usernameTemplate, player.getName(), nickname, server, prefix, suffix, world, worldAlias, uuidStr);

            String avatarTemplate = webhookSettings != null && !webhookSettings.avatarUrl().isBlank()
                    ? webhookSettings.avatarUrl()
                    : SkinAvatarService.DEFAULT_AVATAR_TEMPLATE;
            avatarTemplate = chatMetadata.applyPlaceholders(player, avatarTemplate);

            String resolvedAvatar;
            if (SkinAvatarService.isDefaultOrCrafatar(avatarTemplate)) {
                resolvedAvatar = webhook != null
                        ? webhook.resolvePlayerAvatarUrl(player.getUniqueId())
                        : skinAvatarService.getPlayerAvatar(player.getUniqueId(), "");
            } else {
                resolvedAvatar = skinAvatarService.getAvatarUrl(
                        player.getUniqueId(), player.getName(), avatarTemplate, server);
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
                    uuidStr,
                    mcFormatting.toOptions(),
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
                    uuidStr,
                    mcFormatting.toOptions(),
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
        return relayIfNonBlank(
                template, player, nickname, message, timestamp, server, prefix, suffix, world, worldAlias, "", null, sender);
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
            String uuid,
            MinecraftToDiscordFormatter.Options formattingOptions,
            Consumer<String> sender) {
        if (message == null || message.isBlank()) {
            return false;
        }
        sender.accept(RelayMessageFormatter.formatForDiscord(
                template, player, nickname, message, timestamp, server, prefix, suffix, world, worldAlias, uuid, formattingOptions));
        return true;
    }
}
