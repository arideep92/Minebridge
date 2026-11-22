package com.minebridge.death;

import com.minebridge.config.ConfigManager.DeathSettings;
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
        String titleTemplate = settings.embedTitle() != null ? settings.embedTitle() : "";
        if ("☠ Player death".equalsIgnoreCase(titleTemplate) || "DEATH".equalsIgnoreCase(titleTemplate)
                || "PLAYER DEATH".equalsIgnoreCase(titleTemplate)) {
            titleTemplate = "";
        }
        String title = titleTemplate.isBlank() ? "" : DeathMessageFormatter.formatDiscord(titleTemplate, context, message);
        String descTemplate = settings.embedDescription() != null && !settings.embedDescription().isBlank()
                ? settings.embedDescription()
                : "{message}";
        if ("{message}\nCategory: {category}\nCause: {cause}".equals(descTemplate)) {
            descTemplate = "{message}";
        }
        String description = DeathMessageFormatter.formatDiscord(
                descTemplate, context, message);
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
