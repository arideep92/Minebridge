package com.minebridge.config;

import com.minebridge.discord.IntegrationMode;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates configuration values across config.yml, messages.yml, events.yml, and advanced.yml
 * with human-readable, actionable error messages.
 */
public final class ConfigValidator {

    public static final Pattern SNOWFLAKE_PATTERN = Pattern.compile("^[0-9]{17,20}$");
    public static final Pattern DISCORD_WEBHOOK_URL_PATTERN = Pattern.compile(
            "^https://(discord\\.com|discordapp\\.com|canary\\.discord\\.com|ptb\\.discord\\.com)/api/webhooks/([0-9]{17,20})/([A-Za-z0-9_-]+)(?:\\?.*)?$");
    public static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#?[0-9A-Fa-f]{6}$");
    public static final BigInteger MAX_UNSIGNED_64_BIT = new BigInteger("18446744073709551615");

    public static final Set<String> PLACEHOLDERS_MC_TO_DISCORD = Set.of(
            "player", "nickname", "display_name", "message", "timestamp", "server", "prefix", "suffix", "world", "world_alias", "uuid");
    public static final Set<String> PLACEHOLDERS_DISCORD_TO_MC = Set.of(
            "player", "display_name", "username", "nickname", "channel", "message", "timestamp", "server", "prefix", "suffix");
    public static final Set<String> PLACEHOLDERS_PLAYER_JOIN_LEAVE = Set.of(
            "player", "nickname", "display_name", "timestamp", "server", "prefix", "suffix", "world", "world_alias");
    public static final Set<String> PLACEHOLDERS_SERVER_STATUS = Set.of("online", "max", "server");
    public static final Set<String> PLACEHOLDERS_PLAYER_LIST = Set.of("online", "max", "players", "server");
    public static final Set<String> PLACEHOLDERS_DEATH = Set.of(
            "victim", "killer", "cause", "category", "server", "prefix");
    public static final Set<String> PLACEHOLDERS_DEATH_EMBED = Set.of(
            "victim", "killer", "cause", "category", "message", "server", "prefix");
    public static final Set<String> PLACEHOLDERS_ACHIEVEMENT = Set.of(
            "player", "title", "description", "category", "challenge", "server", "prefix");

    private ConfigValidator() {
    }

    public static boolean isValidSnowflake(String snowflake) {
        if (snowflake == null || !SNOWFLAKE_PATTERN.matcher(snowflake).matches()) {
            return false;
        }
        try {
            BigInteger value = new BigInteger(snowflake);
            return value.signum() >= 0 && value.compareTo(MAX_UNSIGNED_64_BIT) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public static boolean isValidChannelId(String channelId) {
        return isValidSnowflake(channelId);
    }

    public static boolean isValidWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var matcher = DISCORD_WEBHOOK_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            return false;
        }
        return isValidSnowflake(matcher.group(2));
    }

    public static boolean isValidBotToken(String token) {
        return token != null
                && !token.isBlank()
                && token.length() <= 512
                && !"PUT_YOUR_BOT_TOKEN_HERE".equals(token)
                && token.chars().noneMatch(Character::isWhitespace);
    }

    public static int parseHexColor(String hex, int defaultColor, List<String> errors, String file, String key) {
        if (hex == null || hex.isBlank()) {
            return defaultColor;
        }
        String trimmed = hex.trim();
        if (!HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            if (errors != null) {
                String prefix = (file != null && !file.isBlank() ? "File: " + file + " | " : "");
                errors.add(prefix + key + " must be a six-digit hex color string (e.g. #RRGGBB), got: " + hex);
            }
            return defaultColor;
        }
        String normalized = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException exception) {
            if (errors != null) {
                String prefix = (file != null && !file.isBlank() ? "File: " + file + " | " : "");
                errors.add(prefix + key + " | Invalid hex color: " + hex);
            }
            return defaultColor;
        }
    }

    public static void validateTemplatePlaceholders(
            String file,
            String key,
            String template,
            Set<String> allowed,
            List<String> errors) {
        if (template == null || template.isEmpty()) {
            return;
        }
        String filePrefix = (file != null && !file.isBlank() ? "File: " + file + " | " : "");
        for (int index = 0; index < template.length(); ) {
            char current = template.charAt(index);
            if (current == '}') {
                errors.add(filePrefix + key + " contains malformed placeholder braces at index " + index);
                return;
            }
            if (current != '{') {
                index++;
                continue;
            }

            int closingBrace = template.indexOf('}', index + 1);
            int nestedBrace = template.indexOf('{', index + 1);
            if (closingBrace < 0 || (nestedBrace >= 0 && nestedBrace < closingBrace)) {
                errors.add(filePrefix + key + " contains malformed placeholder braces.");
                return;
            }

            String placeholder = template.substring(index + 1, closingBrace);
            if (allowed != null && !allowed.contains(placeholder)) {
                errors.add(filePrefix + key + " contains an unsupported placeholder; check its allowed values.");
            }
            index = closingBrace + 1;
        }
    }
}
