package com.minebridge.discord;

import java.util.Map;
import java.util.regex.Pattern;

public final class RelayMessageFormatter {

    public static final int MAX_MESSAGE_LENGTH = 2000;

    private static final Pattern MINECRAFT_FORMATTING =
            Pattern.compile("(?i)§(?:[0-9A-FK-OR]|x(?:§[0-9A-F]){6})");
    private static final Pattern AMPERSAND_FORMATTING =
            Pattern.compile("(?i)&(?:[0-9A-FK-OR]|x(?:&[0-9A-F]){6})");
    private static final Pattern DISCORD_MASS_MENTION =
            Pattern.compile("(?i)@(everyone|here)\\b");
    private static final Pattern DISCORD_MARKDOWN =
            Pattern.compile("([\\\\`*_~|><\\[\\]()])");

    private RelayMessageFormatter() {
    }

    public static String formatForDiscord(String template, String player, String message) {
        return formatForDiscord(template, player, message, "");
    }

    public static String formatForDiscord(
            String template, String player, String message, String timestamp) {
        return formatForDiscord(template, player, message, timestamp, "", "");
    }

    public static String formatForDiscord(
            String template,
            String player,
            String message,
            String timestamp,
            String server,
            String prefix) {
        return formatForDiscord(template, player, player, message, timestamp, server, prefix, "", "", "");
    }

    public static String formatForDiscord(
            String template,
            String player,
            String nickname,
            String message,
            String timestamp,
            String server,
            String prefix,
            String suffix,
            String world,
            String worldAlias) {
        return formatForDiscord(
                template, player, nickname, message, timestamp, server, prefix, suffix, world, worldAlias, "", null);
    }

    public static String formatForDiscord(
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
            MinecraftToDiscordFormatter.Options formattingOptions) {
        String effectiveServer = server != null && !server.isBlank()
                ? server : "Minecraft Server";
        String effectivePrefix = prefix != null ? prefix : "";
        String effectiveSuffix = suffix != null ? suffix : "";
        String effectiveNickname = (nickname != null && !nickname.isBlank()) ? nickname : (player != null ? player : "");
        String effectiveWorld = world != null ? world : "";
        String effectiveWorldAlias = (worldAlias != null && !worldAlias.isBlank()) ? worldAlias : effectiveWorld;
        String effectiveUuid = uuid != null ? uuid : "";

        String preparedTemplate = template != null ? template : "";
        String formattedPrefix = "";
        String formattedSuffix = "";

        if (!effectivePrefix.isBlank()) {
            String sanitized = escapeDiscordPrefix(effectivePrefix.trim());
            formattedPrefix = sanitized + " ";
            preparedTemplate = preparedTemplate.replace("{prefix} ", "{prefix}");
        } else {
            preparedTemplate = preparedTemplate.replace("{prefix} ", "");
        }

        if (!effectiveSuffix.isBlank()) {
            String sanitized = escapeDiscordPrefix(effectiveSuffix.trim());
            formattedSuffix = " " + sanitized;
            preparedTemplate = preparedTemplate.replace(" {suffix}", "{suffix}");
        } else {
            preparedTemplate = preparedTemplate.replace(" {suffix}", "");
        }

        String formattedMessage = formattingOptions != null
                ? MinecraftToDiscordFormatter.format(message, formattingOptions)
                : escapeDiscordUserText(message);

        Map<String, String> placeholders = new java.util.HashMap<>(16);
        placeholders.put("player", escapeDiscordUserText(player));
        placeholders.put("nickname", escapeDiscordUserText(effectiveNickname));
        placeholders.put("display_name", escapeDiscordUserText(effectiveNickname));
        placeholders.put("message", formattedMessage);
        placeholders.put("timestamp", escapeDiscordUserText(timestamp));
        placeholders.put("server", escapeDiscordUserText(effectiveServer));
        placeholders.put("world", escapeDiscordUserText(effectiveWorld));
        placeholders.put("world_alias", escapeDiscordUserText(effectiveWorldAlias));
        placeholders.put("uuid", escapeDiscordUserText(effectiveUuid));
        placeholders.put("prefix", formattedPrefix);
        placeholders.put("suffix", formattedSuffix);

        return replacePlaceholders(preparedTemplate, placeholders);
    }

    public static String formatForMinecraft(String template, String player, String message) {
        return formatForMinecraft(template, player, player, player, message, "");
    }

    public static String formatForMinecraft(
            String template,
            String player,
            String displayName,
            String username,
            String message,
            String timestamp) {
        return formatForMinecraft(
                template, player, displayName, username, message, timestamp, "", "");
    }

    public static String formatForMinecraft(
            String template,
            String player,
            String displayName,
            String username,
            String message,
            String timestamp,
            String server,
            String prefix) {
        return formatForMinecraft(template, player, displayName, username, message, timestamp, server, prefix, "");
    }

    public static String formatForMinecraft(
            String template,
            String player,
            String displayName,
            String username,
            String message,
            String timestamp,
            String server,
            String prefix,
            String suffix) {
        return formatForMinecraft(
                template, player, displayName, username, displayName, message, timestamp, server, prefix, suffix, "", null);
    }

    public static String formatForMinecraft(
            String template,
            String player,
            String displayName,
            String username,
            String nickname,
            String message,
            String timestamp,
            String server,
            String prefix,
            String suffix,
            String channel,
            DiscordToMinecraftFormatter.Options formattingOptions) {
        String effectiveServer = server != null && !server.isBlank()
                ? server : "Minecraft Server";
        String effectivePrefix = prefix != null ? prefix : "";
        String effectiveSuffix = suffix != null ? suffix : "";
        String effectiveNickname = (nickname != null && !nickname.isBlank()) ? nickname : (displayName != null ? displayName : player);
        String effectiveChannel = channel != null ? channel : "";

        String preparedTemplate = template != null ? template : "";
        String formattedPrefix = "";
        String formattedSuffix = "";

        if (!effectivePrefix.isBlank()) {
            String sanitized = sanitizePrefix(effectivePrefix.trim());
            formattedPrefix = sanitized + " ";
            preparedTemplate = preparedTemplate.replace("{prefix} ", "{prefix}");
        } else {
            preparedTemplate = preparedTemplate.replace("{prefix} ", "");
        }

        if (!effectiveSuffix.isBlank()) {
            String sanitized = sanitizePrefix(effectiveSuffix.trim());
            formattedSuffix = " " + sanitized;
            preparedTemplate = preparedTemplate.replace(" {suffix}", "{suffix}");
        } else {
            preparedTemplate = preparedTemplate.replace(" {suffix}", "");
        }

        String formattedMessage = formattingOptions != null
                ? DiscordToMinecraftFormatter.format(message, formattingOptions)
                : sanitizeText(message);

        Map<String, String> placeholders = new java.util.HashMap<>(16);
        placeholders.put("player", sanitizeText(player));
        placeholders.put("display_name", sanitizeText(displayName));
        placeholders.put("username", sanitizeText(username));
        placeholders.put("nickname", sanitizeText(effectiveNickname));
        placeholders.put("channel", sanitizeText(effectiveChannel));
        placeholders.put("message", formattedMessage);
        placeholders.put("timestamp", sanitizeText(timestamp));
        placeholders.put("server", sanitizeText(effectiveServer));
        placeholders.put("prefix", formattedPrefix);
        placeholders.put("suffix", formattedSuffix);

        return replacePlaceholders(preparedTemplate, placeholders);
    }

    public static String sanitizePrefix(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String sanitized = AMPERSAND_FORMATTING.matcher(
                MINECRAFT_FORMATTING.matcher(input).replaceAll("")).replaceAll("")
                .replace("§", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder safe = new StringBuilder(sanitized.length());
        for (int index = 0; index < sanitized.length(); index++) {
            char current = sanitized.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 < sanitized.length()
                        && Character.isLowSurrogate(sanitized.charAt(index + 1))) {
                    safe.append(current).append(sanitized.charAt(++index));
                }
            } else if (!Character.isLowSurrogate(current)
                    && !Character.isISOControl(current)) {
                safe.append(current);
            }
        }
        return safe.toString();
    }

    public static String formatWebhookUsername(
            String template, String player, String server, String prefix) {
        return formatWebhookUsername(template, player, player, server, prefix, "", "", "");
    }

    public static String formatWebhookUsername(
            String template,
            String player,
            String nickname,
            String server,
            String prefix,
            String suffix,
            String world,
            String worldAlias) {
        return formatWebhookUsername(
                template, player, nickname, server, prefix, suffix, world, worldAlias, "");
    }

    public static String formatWebhookUsername(
            String template,
            String player,
            String nickname,
            String server,
            String prefix,
            String suffix,
            String world,
            String worldAlias,
            String uuid) {
        String effectiveTemplate = template != null && !template.isBlank() ? template : "{prefix}{player}";
        String effectivePrefix = prefix != null ? prefix.trim() : "";
        String effectiveSuffix = suffix != null ? suffix.trim() : "";
        String effectiveServer = server != null ? server : "";
        String effectivePlayer = player != null ? player : "";
        String effectiveNickname = (nickname != null && !nickname.isBlank()) ? nickname : effectivePlayer;
        String effectiveWorld = world != null ? world : "";
        String effectiveWorldAlias = (worldAlias != null && !worldAlias.isBlank()) ? worldAlias : effectiveWorld;
        String effectiveUuid = uuid != null ? uuid : "";

        String preparedTemplate = effectiveTemplate;
        String formattedPrefix = "";
        String formattedSuffix = "";

        if (!effectivePrefix.isBlank()) {
            String sanitized = sanitizePrefix(effectivePrefix);
            formattedPrefix = sanitized + " ";
            preparedTemplate = preparedTemplate.replace("{prefix} ", "{prefix}");
        } else {
            preparedTemplate = preparedTemplate.replace("{prefix} ", "");
        }

        if (!effectiveSuffix.isBlank()) {
            String sanitized = sanitizePrefix(effectiveSuffix);
            formattedSuffix = " " + sanitized;
            preparedTemplate = preparedTemplate.replace(" {suffix}", "{suffix}");
        } else {
            preparedTemplate = preparedTemplate.replace(" {suffix}", "");
        }

        return replacePlaceholders(preparedTemplate, Map.of(
                "prefix", formattedPrefix,
                "suffix", formattedSuffix,
                "player", effectivePlayer,
                "nickname", effectiveNickname,
                "display_name", effectiveNickname,
                "server", effectiveServer,
                "world", effectiveWorld,
                "world_alias", effectiveWorldAlias,
                "uuid", effectiveUuid));
    }

    public static String escapeDiscordPrefix(String input) {
        String sanitized = sanitizePrefix(input);
        String markdownEscaped = DISCORD_MARKDOWN.matcher(sanitized)
                .replaceAll("\\\\$1");
        return escapeDiscordMassMentions(markdownEscaped);
    }

    public static String sanitizeText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String sanitized = MINECRAFT_FORMATTING.matcher(input).replaceAll("")
                .replace("§", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder safe = new StringBuilder(sanitized.length());
        for (int index = 0; index < sanitized.length(); index++) {
            char current = sanitized.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 < sanitized.length()
                        && Character.isLowSurrogate(sanitized.charAt(index + 1))) {
                    safe.append(current).append(sanitized.charAt(++index));
                }
            } else if (!Character.isLowSurrogate(current)
                    && !Character.isISOControl(current)) {
                safe.append(current);
            }
        }
        return safe.toString().trim();
    }

    public static boolean isWithinDiscordLimit(String message) {
        return message != null && message.length() <= MAX_MESSAGE_LENGTH;
    }

    private static String escapeDiscordMassMentions(String input) {
        return DISCORD_MASS_MENTION.matcher(input)
                .replaceAll("@\u200B$1");
    }

    public static String escapeDiscordUserText(String input) {
        String sanitized = sanitizeText(input);
        String markdownEscaped = DISCORD_MARKDOWN.matcher(sanitized)
                .replaceAll("\\\\$1");
        return escapeDiscordMassMentions(markdownEscaped);
    }

    public static String replacePlaceholders(
            String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        StringBuilder formatted = new StringBuilder(template.length());
        for (int index = 0; index < template.length();) {
            if (template.charAt(index) == '{') {
                int closingBrace = template.indexOf('}', index + 1);
                if (closingBrace >= 0) {
                    String replacement = placeholders.get(
                            template.substring(index + 1, closingBrace));
                    if (replacement != null) {
                        formatted.append(replacement);
                        index = closingBrace + 1;
                        continue;
                    }
                }
            }
            formatted.append(template.charAt(index));
            index++;
        }
        return formatted.toString();
    }
}
