package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.entities.MessageEmbed;

public final class DiscordEventNotifier {

    private volatile DiscordService discord;
    private volatile WebhookService webhook;
    private final ConfigManager config;

    public DiscordEventNotifier(DiscordService discord, ConfigManager config) {
        this(discord, null, config);
    }

    public DiscordEventNotifier(WebhookService webhook, ConfigManager config) {
        this(null, webhook, config);
    }

    public DiscordEventNotifier(
            DiscordService discord,
            WebhookService webhook,
            ConfigManager config) {
        this.discord = discord;
        this.webhook = webhook;
        this.config = config;
    }

    public void updateServices(DiscordService discord, WebhookService webhook) {
        this.discord = discord;
        this.webhook = webhook;
    }

    public void send(String channelId, MessageEmbed embed) {
        ConfigManager.Settings current = config.currentSettings();
        ConfigManager.DiscordEventsSettings settings = current.discordEvents();
        if (!settings.enabled() || !settings.embedsEnabled() || embed == null) {
            return;
        }

        if (current.mode() == IntegrationMode.WEBHOOK) {
            if (webhook != null) {
                webhook.sendEmbed(embed);
            }
        } else {
            if (discord != null) {
                discord.sendEventEmbed(channelId, embed);
            }
        }
    }

    public String footer() {
        ConfigManager.Settings current = config.currentSettings();
        String text = current.embedFooter() != null && !current.embedFooter().text().isBlank()
                ? current.embedFooter().text()
                : current.discordEvents().footer();
        if (text == null || text.isBlank()) {
            text = DiscordEmbedFactory.DEFAULT_FOOTER;
        }
        String serverName = current.serverName() != null && !current.serverName().isBlank()
                ? current.serverName()
                : "Minecraft Server";
        return text.replace("{server}", serverName);
    }

    public String footerIconUrl() {
        ConfigManager.Settings current = config.currentSettings();
        return current.embedFooter() != null ? current.embedFooter().iconUrl() : "";
    }
}
