package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordToMinecraftFormatterTest {

    @Test
    void testStripsRawSectionSignToPreventColorInjection() {
        String input = "§4§l[Owner] Steve: I am the owner §c§lHacked!";
        String formatted = DiscordToMinecraftFormatter.format(input);
        assertFalse(formatted.contains("§4"));
        assertFalse(formatted.contains("§c"));
        assertTrue(formatted.contains("4l[Owner] Steve: I am the owner"));
    }

    @Test
    void testBasicMarkdownConversions() {
        assertEquals("Hello §lworld§r!", DiscordToMinecraftFormatter.format("Hello **world**!"));
        assertEquals("Hello §oitalic§r!", DiscordToMinecraftFormatter.format("Hello *italic*!"));
        assertEquals("Hello §oitalic§r!", DiscordToMinecraftFormatter.format("Hello _italic_!"));
        assertEquals("Hello §nunderlined§r!", DiscordToMinecraftFormatter.format("Hello __underlined__!"));
        assertEquals("Hello §mstrikethrough§r!", DiscordToMinecraftFormatter.format("Hello ~~strikethrough~~!"));
    }

    @Test
    void testCodeBlocksAndInlineCode() {
        assertEquals("Run §7/help§r command", DiscordToMinecraftFormatter.format("Run `/help` command"));
        String multilineCode = "```java\nSystem.out.println(\"hello\");\n```";
        String formatted = DiscordToMinecraftFormatter.format(multilineCode);
        assertTrue(formatted.startsWith("§7"));
        assertTrue(formatted.contains("System.out.println"));
        assertTrue(formatted.endsWith("§r"));
    }

    @Test
    void testSpoilers() {
        assertEquals("Secret: §7[Spoiler: 42]§r", DiscordToMinecraftFormatter.format("Secret: ||42||"));
    }

    @Test
    void testIntraWordUnderscoresNotItalicized() {
        String input = "my_variable_name and some_other_identifier";
        String formatted = DiscordToMinecraftFormatter.format(input);
        assertEquals(input, formatted);
    }

    @Test
    void testAngleBracketUrlsNormalized() {
        String input = "Check out <https://example.com> now";
        String formatted = DiscordToMinecraftFormatter.format(input);
        assertEquals("Check out https://example.com now", formatted);
    }

    @Test
    void testFormatConversionDisabled() {
        DiscordToMinecraftFormatter.Options options =
                new DiscordToMinecraftFormatter.Options(false, false, false, false, false, false, true);
        String formatted = DiscordToMinecraftFormatter.format("**Bold** and §4Red", options);
        assertEquals("**Bold** and 4Red", formatted);
    }

    @Test
    void testIndividualFeatureToggles() {
        DiscordToMinecraftFormatter.Options options =
                new DiscordToMinecraftFormatter.Options(true, false, true, true, true, true, true);
        String formatted = DiscordToMinecraftFormatter.format("**NotBold** *Italic*", options);
        assertFalse(formatted.contains("§l"));
        assertTrue(formatted.contains("§oItalic§r"));
    }

    @Test
    void testNullAndEmptyHandling() {
        assertEquals("", DiscordToMinecraftFormatter.format(null));
        assertEquals("", DiscordToMinecraftFormatter.format("   "));
    }
}
