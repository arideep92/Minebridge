package com.minebridge.death;

import com.minebridge.config.ConfigManager.DeathSettings;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.DiscordEventNotifier;

public final class DiscordDeathNotifier {

    private final DiscordEventNotifier notifier;

    public DiscordDeathNotifier(DiscordEventNotifier notifier) {
        this.notifier = notifier;
    }

    public void notify(
            DeathSettings settings,
            String channelId,
            DeathContext context,
            String message) {
        String titleTemplate = settings.embedTitle() != null && !settings.embedTitle().isBlank()
                ? settings.embedTitle()
                : "☠ Player death";
        String title = DeathMessageFormatter.formatDiscord(titleTemplate, context, message);
        String description = DeathMessageFormatter.formatDiscord(
                settings.embedDescription(), context, message);
        var embed = DiscordEmbedFactory.death(
                title,
                description,
                context,
                settings.embedColor(),
                notifier.footer(),
                notifier.footerIconUrl());
        notifier.send(channelId, embed);
    }
}
