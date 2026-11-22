package com.minebridge.discord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * Dedicated formatting conversion system translating Minecraft formatting codes
 * (& and §, including hex colors) into Discord Markdown.
 */
public final class MinecraftToDiscordFormatter {

    private static final Pattern SPIGOT_HEX_PATTERN = Pattern.compile("(?i)§x(?:§[0-9A-Fa-f]){6}");
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("(?i)&#([0-9A-Fa-f]{6})");
    private static final Pattern BRACKET_HEX_PATTERN = Pattern.compile("(?i)[{<]#([0-9A-Fa-f]{6})[}>]");
    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("(?i)[&§][0-9a-fk-or]");

    public record Options(
            boolean formatConversion,
            boolean allowBold,
            boolean allowItalic,
            boolean allowUnderline,
            boolean allowStrikethrough,
            boolean allowObfuscated) {
        public static final Options DEFAULT = new Options(true, true, true, true, true, true);
        public static final Options STRIP_ALL = new Options(false, false, false, false, false, false);
    }

    private MinecraftToDiscordFormatter() {
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

        // Normalize hex color patterns away (Discord plain messages do not support arbitrary text colors)
        String text = SPIGOT_HEX_PATTERN.matcher(input).replaceAll("§r");
        text = AMPERSAND_HEX_PATTERN.matcher(text).replaceAll("&r");
        text = BRACKET_HEX_PATTERN.matcher(text).replaceAll("");

        if (!options.formatConversion()) {
            String stripped = FORMAT_CODE_PATTERN.matcher(text).replaceAll("");
            return RelayMessageFormatter.escapeDiscordUserText(stripped);
        }

        StringBuilder result = new StringBuilder(text.length() + 16);
        Deque<String> activeStyles = new ArrayDeque<>();
        boolean isBold = false;
        boolean isItalic = false;
        boolean isUnderline = false;
        boolean isStrikethrough = false;
        boolean isObfuscated = false;

        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Check for format code prefix: either '§' or '&'
            if ((c == '§' || c == '&') && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (isFormatOrColorCode(code)) {
                    i++; // consume code char
                    switch (code) {
                        case 'l': // Bold
                            if (options.allowBold() && !isBold) {
                                result.append("**");
                                activeStyles.push("**");
                                isBold = true;
                            }
                            break;
                        case 'o': // Italic
                            if (options.allowItalic() && !isItalic) {
                                result.append("*");
                                activeStyles.push("*");
                                isItalic = true;
                            }
                            break;
                        case 'n': // Underline
                            if (options.allowUnderline() && !isUnderline) {
                                result.append("__");
                                activeStyles.push("__");
                                isUnderline = true;
                            }
                            break;
                        case 'm': // Strikethrough
                            if (options.allowStrikethrough() && !isStrikethrough) {
                                result.append("~~");
                                activeStyles.push("~~");
                                isStrikethrough = true;
                            }
                            break;
                        case 'k': // Obfuscated -> Discord spoiler ||
                            if (options.allowObfuscated() && !isObfuscated) {
                                result.append("||");
                                activeStyles.push("||");
                                isObfuscated = true;
                            }
                            break;
                        case 'r': // Reset
                        case '0': case '1': case '2': case '3': case '4':
                        case '5': case '6': case '7': case '8': case '9':
                        case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                            // In Minecraft, colors and reset close all active styles
                            closeAllStyles(result, activeStyles);
                            isBold = false;
                            isItalic = false;
                            isUnderline = false;
                            isStrikethrough = false;
                            isObfuscated = false;
                            break;
                        default:
                            break;
                    }
                    continue;
                }
            }

            // Regular character: escape Discord markdown characters and mass mentions
            appendEscapedChar(result, text, i);
        }

        // Close any dangling styles at the end of the message
        closeAllStyles(result, activeStyles);

        return sanitizeDiscordOutput(result.toString());
    }

    private static boolean isFormatOrColorCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                || c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r';
    }

    private static void closeAllStyles(StringBuilder sb, Deque<String> activeStyles) {
        while (!activeStyles.isEmpty()) {
            sb.append(activeStyles.pop());
        }
    }

    private static void appendEscapedChar(StringBuilder sb, String text, int index) {
        char c = text.charAt(index);
        switch (c) {
            case '*':
            case '_':
            case '~':
            case '`':
            case '\\':
            case '|':
            case '>':
                sb.append('\\').append(c);
                break;
            case '@':
                // Defend against mass mentions: @everyone and @here
                if (index + 8 <= text.length() && text.regionMatches(true, index, "@everyone", 0, 9)) {
                    sb.append("@\u200B");
                } else if (index + 4 <= text.length() && text.regionMatches(true, index, "@here", 0, 5)) {
                    sb.append("@\u200B");
                } else {
                    sb.append(c);
                }
                break;
            default:
                if (c == '\r') {
                    // Ignore CR
                } else if (c == '\n' || c == '\t') {
                    sb.append(c);
                } else if (!Character.isISOControl(c)) {
                    sb.append(c);
                }
                break;
        }
    }

    private static String sanitizeDiscordOutput(String output) {
        return output
                .replace("****", "")
                .replace("____", "")
                .replace("~~~~", "")
                .replace("||||", "");
    }
}
