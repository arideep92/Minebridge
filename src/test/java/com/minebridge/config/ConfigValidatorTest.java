package com.minebridge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    @Test
    @DisplayName("Validates Discord bot token formats")
    void validatesBotToken() {
        assertFalse(ConfigValidator.isValidBotToken(null));
        assertFalse(ConfigValidator.isValidBotToken(""));
        assertFalse(ConfigValidator.isValidBotToken("   "));
        assertFalse(ConfigValidator.isValidBotToken("PUT_YOUR_BOT_TOKEN_HERE"));
        assertFalse(ConfigValidator.isValidBotToken("token with space"));
        assertFalse(ConfigValidator.isValidBotToken("a".repeat(513)));
        assertTrue(ConfigValidator.isValidBotToken("valid-discord-bot-token-string"));
    }

    @Test
    @DisplayName("Validates Discord snowflake channel IDs")
    void validatesSnowflakeChannelIds() {
        assertFalse(ConfigValidator.isValidChannelId(null));
        assertFalse(ConfigValidator.isValidChannelId(""));
        assertFalse(ConfigValidator.isValidChannelId("abc"));
        assertFalse(ConfigValidator.isValidChannelId("1234567890123456")); // 16 digits
        assertFalse(ConfigValidator.isValidChannelId("123456789012345678901")); // 21 digits
        assertFalse(ConfigValidator.isValidChannelId("18446744073709551616")); // > 2^64-1

        assertTrue(ConfigValidator.isValidChannelId("12345678901234567")); // 17 digits
        assertTrue(ConfigValidator.isValidChannelId("123456789012345678")); // 18 digits
        assertTrue(ConfigValidator.isValidChannelId("1234567890123456789")); // 19 digits
        assertTrue(ConfigValidator.isValidChannelId("12345678901234567890")); // 20 digits
        assertTrue(ConfigValidator.isValidChannelId("18446744073709551615")); // max unsigned 64-bit
    }

    @Test
    @DisplayName("Validates Discord webhook URLs")
    void validatesWebhookUrls() {
        assertFalse(ConfigValidator.isValidWebhookUrl(null));
        assertFalse(ConfigValidator.isValidWebhookUrl(""));
        assertFalse(ConfigValidator.isValidWebhookUrl("http://discord.com/api/webhooks/123456789012345678/token")); // non-https
        assertFalse(ConfigValidator.isValidWebhookUrl("https://malicious.com/api/webhooks/123456789012345678/token"));
        assertFalse(ConfigValidator.isValidWebhookUrl("https://discord.com/api/webhooks/123/token")); // invalid snowflake

        assertTrue(ConfigValidator.isValidWebhookUrl("https://discord.com/api/webhooks/123456789012345678/sample-token_123"));
        assertTrue(ConfigValidator.isValidWebhookUrl("https://discordapp.com/api/webhooks/123456789012345678/sample-token_123"));
        assertTrue(ConfigValidator.isValidWebhookUrl("https://canary.discord.com/api/webhooks/123456789012345678/sample-token_123"));
        assertTrue(ConfigValidator.isValidWebhookUrl("https://ptb.discord.com/api/webhooks/123456789012345678/sample-token_123"));
    }

    @Test
    @DisplayName("Parses hex colors with or without # and reports invalid formats")
    void parsesHexColors() {
        List<String> errors = new ArrayList<>();
        assertEquals(0xD32F2F, ConfigValidator.parseHexColor("#D32F2F", 0, errors, "events.yml", "deaths.discord.color"));
        assertEquals(0x2196F3, ConfigValidator.parseHexColor("2196F3", 0, errors, "events.yml", "advancements.discord.color"));
        assertTrue(errors.isEmpty());

        int fallback = ConfigValidator.parseHexColor("INVALID_HEX", 0x123456, errors, "events.yml", "test.color");
        assertEquals(0x123456, fallback);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("events.yml"));
        assertTrue(errors.getFirst().contains("test.color"));
    }

    @Test
    @DisplayName("Validates template placeholders and flags malformed braces or unsupported tokens")
    void validatesTemplatePlaceholders() {
        Set<String> allowed = Set.of("player", "message", "prefix");

        List<String> errors = new ArrayList<>();
        ConfigValidator.validateTemplatePlaceholders("messages.yml", "chat.minecraft-to-discord",
                "{prefix}**{player}**: {message}", allowed, errors);
        assertTrue(errors.isEmpty());

        ConfigValidator.validateTemplatePlaceholders("messages.yml", "chat.minecraft-to-discord",
                "{player} {unsupported}", allowed, errors);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("unsupported placeholder"));

        errors.clear();
        ConfigValidator.validateTemplatePlaceholders("messages.yml", "chat.minecraft-to-discord",
                "{player", allowed, errors);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("malformed placeholder braces"));
    }
}
