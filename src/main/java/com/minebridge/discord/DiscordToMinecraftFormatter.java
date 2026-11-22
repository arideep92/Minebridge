package com.minebridge.discord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dedicated formatting translation system converting Discord Markdown into
 * Minecraft chat formatting codes (§), preventing unauthorized color/style injection.
 */
public final class DiscordToMinecraftFormatter {

    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(?:[a-zA-Z0-9_-]+\n)?([\\s\\S]*?)```");
    private static final Pattern INLINE_CODE_PATTERN =
            Pattern.compile("`([^`\n]+)`");
    private static final Pattern SPOILER_PATTERN =
            Pattern.compile("\\|\\|([\\s\\S]*?)\\|\\|");
    private static final Pattern BOLD_UNDERLINE_PATTERN =
            Pattern.compile("(?:__\\*\\*|\\*\\*__)([\\s\\S]*?)(?:\\*\\*__|__\\*\\*)");
    private static final Pattern UNDERLINE_PATTERN =
            Pattern.compile("__([\\s\\S]*?)__");
    private static final Pattern BOLD_PATTERN =
            Pattern.compile("\\*\\*([\\s\\S]*?)\\*\\*");
    private static final Pattern STRIKETHROUGH_PATTERN =
            Pattern.compile("~~([\\s\\S]*?)~~");
    private static final Pattern ASTERISK_ITALIC_PATTERN =
            Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern UNDERSCORE_ITALIC_PATTERN =
            Pattern.compile("(?<![\\w_])_([^_\\n]+)_(?![\\w_])");
    private static final Pattern ANGLE_BRACKET_URL_PATTERN =
            Pattern.compile("<(https?://[^\\s>]+)>");
    private static final Pattern MULTI_RESET_PATTERN =
            Pattern.compile("(§r)+");

    public record Options(
            boolean formatConversion,
            boolean allowBold,
            boolean allowItalic,
            boolean allowUnderline,
            boolean allowStrikethrough,
            boolean allowCode,
            boolean showLinks) {
        public static final Options DEFAULT =
                new Options(true, true, true, true, true, true, true);
        public static final Options STRIP_ALL =
                new Options(false, false, false, false, false, false, true);
    }

    private DiscordToMinecraftFormatter() {
    }

    public static String format(String input) {
        return format(input, Options.DEFAULT);
    }

    public static String format(String input, Options options) {
        if (input == null || input.isBlank()) {
            return "";
        }
        if (options == null) {
            options = Options.DEFAULT;
        }

        // 1. Strip raw § to prevent color injection, rank spoofing, or formatting hacks
        String sanitized = input.replace("§", "");

        // 2. Normalize angle bracket URLs (e.g. <https://example.com> -> https://example.com)
        sanitized = ANGLE_BRACKET_URL_PATTERN.matcher(sanitized).replaceAll("$1");

        if (!options.formatConversion()) {
            return sanitizeControlChars(sanitized);
        }

        String result = sanitized;

        // 3. Process Code Blocks
        if (options.allowCode()) {
            result = CODE_BLOCK_PATTERN.matcher(result).replaceAll("§7$1§r");
            result = INLINE_CODE_PATTERN.matcher(result).replaceAll("§7$1§r");
        } else {
            result = CODE_BLOCK_PATTERN.matcher(result).replaceAll("$1");
            result = INLINE_CODE_PATTERN.matcher(result).replaceAll("$1");
        }

        // 4. Process Spoilers
        result = SPOILER_PATTERN.matcher(result).replaceAll("§7[Spoiler: $1]§r");

        // 5. Process Bold + Underline
        if (options.allowBold() && options.allowUnderline()) {
            result = BOLD_UNDERLINE_PATTERN.matcher(result).replaceAll("§n§l$1§r");
        }

        // 6. Process Underline
        if (options.allowUnderline()) {
            result = UNDERLINE_PATTERN.matcher(result).replaceAll("§n$1§r");
        } else {
            result = UNDERLINE_PATTERN.matcher(result).replaceAll("$1");
        }

        // 7. Process Bold
        if (options.allowBold()) {
            result = BOLD_PATTERN.matcher(result).replaceAll("§l$1§r");
        } else {
            result = BOLD_PATTERN.matcher(result).replaceAll("$1");
        }

        // 8. Process Strikethrough
        if (options.allowStrikethrough()) {
            result = STRIKETHROUGH_PATTERN.matcher(result).replaceAll("§m$1§r");
        } else {
            result = STRIKETHROUGH_PATTERN.matcher(result).replaceAll("$1");
        }

        // 9. Process Italic (*text* and _text_)
        if (options.allowItalic()) {
            result = ASTERISK_ITALIC_PATTERN.matcher(result).replaceAll("§o$1§r");
            result = UNDERSCORE_ITALIC_PATTERN.matcher(result).replaceAll("§o$1§r");
        } else {
            result = ASTERISK_ITALIC_PATTERN.matcher(result).replaceAll("$1");
            result = UNDERSCORE_ITALIC_PATTERN.matcher(result).replaceAll("$1");
        }

        // Clean up redundant reset sequences
        result = MULTI_RESET_PATTERN.matcher(result).replaceAll("§r");

        return sanitizeControlChars(result);
    }

    private static String sanitizeControlChars(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
