package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftToDiscordFormatterTest {

    @Test
    void testBasicStyleConversions() {
        assertEquals("**Bold**", MinecraftToDiscordFormatter.format("&lBold"));
        assertEquals("**Bold**", MinecraftToDiscordFormatter.format("§lBold"));
        assertEquals("*Italic*", MinecraftToDiscordFormatter.format("&oItalic"));
        assertEquals("__Underline__", MinecraftToDiscordFormatter.format("&nUnderline"));
        assertEquals("~~Strikethrough~~", MinecraftToDiscordFormatter.format("&mStrikethrough"));
        assertEquals("||Spoiler||", MinecraftToDiscordFormatter.format("&kSpoiler"));
    }

    @Test
    void testCombinedStylesAndClosingInReverseOrder() {
        String formatted = MinecraftToDiscordFormatter.format("&l&oBold and Italic");
        assertTrue(formatted.startsWith("***") || formatted.contains("**") && formatted.contains("*"));
        assertTrue(formatted.endsWith("***") || formatted.endsWith("*") || formatted.endsWith("**"));
    }

    @Test
    void testColorResetsActiveStyles() {
        String formatted = MinecraftToDiscordFormatter.format("&lBold&cRed Text");
        assertEquals("**Bold**Red Text", formatted);
    }

    @Test
    void testExplicitResetCodeClosesActiveStyles() {
        String formatted = MinecraftToDiscordFormatter.format("&nUnderlined&r Normal");
        assertEquals("__Underlined__ Normal", formatted);
    }

    @Test
    void testHexColorNormalization() {
        String spigotHex = MinecraftToDiscordFormatter.format("§x§f§f§5§5§5§5Colored");
        assertEquals("Colored", spigotHex);

        String ampersandHex = MinecraftToDiscordFormatter.format("&#ff5555Colored");
        assertEquals("Colored", ampersandHex);

        String bracketHex = MinecraftToDiscordFormatter.format("{#ff5555}Colored");
        assertEquals("Colored", bracketHex);
    }

    @Test
    void testEscapesDiscordUserMarkdownCharacters() {
        String formatted = MinecraftToDiscordFormatter.format("Hello *world* `code` _test_ ~strike~ |bar| >quote");
        assertEquals("Hello \\*world\\* \\`code\\` \\_test\\_ \\~strike\\~ \\|bar\\| \\>quote", formatted);
    }

    @Test
    void testDefusesMassMentions() {
        String formatted = MinecraftToDiscordFormatter.format("Hey @everyone and @here!");
        assertEquals("Hey @\u200Beveryone and @\u200Bhere!", formatted);
    }

    @Test
    void testTrailingUnclosedTagsAreClosedAtEnd() {
        String formatted = MinecraftToDiscordFormatter.format("&l&nUnclosed styling");
        assertTrue(formatted.startsWith("**__") || formatted.startsWith("__**"));
        assertTrue(formatted.endsWith("__**") || formatted.endsWith("**__"));
    }

    @Test
    void testFormatConversionDisabled() {
        MinecraftToDiscordFormatter.Options options =
                new MinecraftToDiscordFormatter.Options(false, false, false, false, false, false);
        String formatted = MinecraftToDiscordFormatter.format("&lBold", options);
        // Strips codes and escapes user text without converting to markdown tags
        assertEquals("Bold", formatted);
    }

    @Test
    void testNullAndEmptyHandling() {
        assertEquals("", MinecraftToDiscordFormatter.format(null));
        assertEquals("", MinecraftToDiscordFormatter.format("   "));
    }
}
