package com.minebridge.listener;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.RelayMessageFormatter;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Instant;
import java.util.function.Consumer;

public final class ChatListener implements Listener {

    private final MineBridgePlugin plugin;
    private volatile DiscordService discord;
    private volatile WebhookService webhook;
    private final ConfigManager config;
    private final LuckPermsHook luckPerms;

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            ConfigManager config) {
        this(plugin, discord, null, config, new LuckPermsHook());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            WebhookService webhook,
            ConfigManager config) {
        this(plugin, null, webhook, config, new LuckPermsHook());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config) {
        this(plugin, discord, webhook, config, new LuckPermsHook());
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            ConfigManager config,
            LuckPermsHook luckPerms) {
        this(plugin, discord, null, config, luckPerms);
    }

    public ChatListener(
            MineBridgePlugin plugin,
            WebhookService webhook,
            ConfigManager config,
            LuckPermsHook luckPerms) {
        this(plugin, null, webhook, config, luckPerms);
    }

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config,
            LuckPermsHook luckPerms) {
        this.plugin = plugin;
        this.discord = discord;
        this.webhook = webhook;
        this.config = config;
        this.luckPerms = luckPerms != null ? luckPerms : new LuckPermsHook();
    }

    public void updateServices(DiscordService discord, WebhookService webhook) {
        this.discord = discord;
        this.webhook = webhook;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!config.chatEnabled()) {
            return;
        }

        String template = config.message(
                "messages.minecraft-to-discord", "**{player}**: {message}");
        ConfigManager.Settings current = config.currentSettings();
        String server = current.serverName();
        String prefix = luckPerms.getPrefix(event.getPlayer());

        if (current.mode() == IntegrationMode.WEBHOOK) {
            if (webhook == null) {
                return;
            }
            ConfigManager.WebhookSettings webhookSettings = current.webhook();
            String usernameTemplate = webhookSettings != null && !webhookSettings.username().isBlank()
                    ? webhookSettings.username()
                    : "{player}";
            String resolvedUsername = usernameTemplate
                    .replace("{player}", event.getPlayer().getName())
                    .replace("{server}", server != null ? server : "")
                    .replace("{prefix}", prefix);

            String avatarTemplate = webhookSettings != null && !webhookSettings.avatarUrl().isBlank()
                    ? webhookSettings.avatarUrl()
                    : "";
            String resolvedAvatar = avatarTemplate.isBlank() ? "" : avatarTemplate
                    .replace("{player}", event.getPlayer().getName())
                    .replace("{server}", server != null ? server : "")
                    .replace("{prefix}", prefix);

            relayIfNonBlank(
                    template,
                    event.getPlayer().getName(),
                    event.getMessage(),
                    Instant.now().toString(),
                    server,
                    prefix,
                    formatted -> webhook.send(resolvedUsername, resolvedAvatar, formatted));
        } else {
            if (discord == null) {
                return;
            }
            relayIfNonBlank(
                    template,
                    event.getPlayer().getName(),
                    event.getMessage(),
                    Instant.now().toString(),
                    server,
                    prefix,
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
        if (message == null || message.isBlank()) {
            return false;
        }
        sender.accept(RelayMessageFormatter.formatForDiscord(
                template, player, message, timestamp, server, prefix));
        return true;
    }
}
