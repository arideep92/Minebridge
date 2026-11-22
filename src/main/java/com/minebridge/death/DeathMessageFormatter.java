package com.minebridge.death;

import com.minebridge.discord.RelayMessageFormatter;
import org.bukkit.ChatColor;

import java.util.Map;

public final class DeathMessageFormatter {

    private DeathMessageFormatter() {
    }

    public static String formatMinecraft(String template, DeathContext context) {
        String expanded = replace(template, context, null, false);
        return ChatColor.translateAlternateColorCodes('&', expanded);
    }

    public static String formatDiscord(String template, DeathContext context, String message) {
        return replace(template, context, message, true);
    }

    public static String formatDiscordMessage(String template, DeathContext context) {
        String minecraftFormatted = ChatColor.translateAlternateColorCodes('&', template);
        String withoutColors = ChatColor.stripColor(minecraftFormatted);
        return replace(withoutColors, context, null, true);
    }

    private static String replace(
            String template, DeathContext context, String message, boolean escapeDiscordText) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        Map<String, String> placeholders = Map.of(
                "victim", safe(context.victim(), escapeDiscordText),
                "killer", safe(context.killer(), escapeDiscordText),
                "cause", safe(context.cause(), escapeDiscordText),
                "category", safe(context.category().displayName(), escapeDiscordText),
                "message", message == null ? "" : message);
        return RelayMessageFormatter.replacePlaceholders(template, placeholders);
    }

    private static String safe(String value, boolean escapeDiscordText) {
        String sanitized = RelayMessageFormatter.sanitizeText(value);
        if (escapeDiscordText) {
            return RelayMessageFormatter.escapeDiscordUserText(sanitized);
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', sanitized));
    }
}
