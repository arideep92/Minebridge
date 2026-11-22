package com.minebridge.config;

import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.IntegrationMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerWebhookTest {

    private static final String VALID_TOKEN = "synthetic-test-bot-token-12345";
    private static final String VALID_CHANNEL = "123456789012345678";
    private static final String VALID_WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/sample-token_ABC123";

    private YamlConfiguration parseYaml(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            try (var stream = getClass().getResourceAsStream("/config.yml")) {
                if (stream != null) {
                    YamlConfiguration defaults = new YamlConfiguration();
                    defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    config.setDefaults(defaults);
                }
            }
            config.load(new StringReader(yaml));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        return config;
    }

    private ConfigManager configManager() {
        return new ConfigManager(null);
    }

    @Test
    void defaultModeIsBotWhenUnspecified() {
        String yaml = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);

        assertTrue(candidate.valid(), "Expected valid config: " + candidate.errors());
        assertEquals(IntegrationMode.BOT, candidate.settings().mode());
        assertEquals(VALID_TOKEN, candidate.settings().botToken());
        assertEquals(VALID_CHANNEL, candidate.settings().channelId());
        assertEquals("Minecraft Server", candidate.settings().serverName());
        assertEquals(DiscordEmbedFactory.DEFAULT_FOOTER, candidate.settings().embedFooter().text());
    }

    @Test
    void explicitBotModeParsesCaseInsensitively() {
        String yaml = """
                discord:
                  mode: "bot"
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);

        assertTrue(candidate.valid(), "Expected valid config: " + candidate.errors());
        assertEquals(IntegrationMode.BOT, candidate.settings().mode());
    }

    @Test
    void explicitWebhookModeParsesCaseInsensitivelyAndDoesNotRequireBotTokenOrChannel() {
        String yaml = """
                discord:
                  mode: "webhook"
                  webhook:
                    url: "%s"
                    username: "{player}"
                    avatar-url: "https://example.com/avatar.png"
                server:
                  name: "Survival World"
                """.formatted(VALID_WEBHOOK_URL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);

        assertTrue(candidate.valid(), "Expected valid config: " + candidate.errors());
        assertEquals(IntegrationMode.WEBHOOK, candidate.settings().mode());
        assertEquals(VALID_WEBHOOK_URL, candidate.settings().webhook().url());
        assertEquals("{player}", candidate.settings().webhook().username());
        assertEquals("https://example.com/avatar.png", candidate.settings().webhook().avatarUrl());
        assertEquals("Survival World", candidate.settings().serverName());
    }

    @Test
    void webhookModeRequiresWebhookUrl() {
        String yaml = """
                discord:
                  mode: "WEBHOOK"
                  webhook:
                    url: ""
                """;

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                err -> err.contains("discord.webhook.url is required when discord.mode is WEBHOOK.")));
    }

    @Test
    void rejectsInvalidIntegrationMode() {
        String yaml = """
                discord:
                  mode: "INVALID_UNKNOWN_MODE"
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                err -> err.contains("discord.mode must be either BOT, WEBHOOK, or HYBRID.")));
    }

    @Test
    void rejectsNonStringIntegrationMode() {
        String yaml = """
                discord:
                  mode: 123
                  bot-token: "%s"
                  channel-id: "%s"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                err -> err.contains("discord.mode must be a string.")));
    }

    @Test
    void validatesApprovedDiscordWebhookHosts() {
        assertTrue(ConfigManager.isValidWebhookUrl(
                "https://discord.com/api/webhooks/123456789012345678/token_123-abc"));
        assertTrue(ConfigManager.isValidWebhookUrl(
                "https://discordapp.com/api/webhooks/123456789012345678/token_123-abc"));
        assertTrue(ConfigManager.isValidWebhookUrl(
                "https://canary.discord.com/api/webhooks/123456789012345678/token_123-abc"));
        assertTrue(ConfigManager.isValidWebhookUrl(
                "https://ptb.discord.com/api/webhooks/123456789012345678/token_123-abc"));
        assertTrue(ConfigManager.isValidWebhookUrl(
                "https://discord.com/api/webhooks/123456789012345678/token_123?wait=true"));

        // Reject insecure HTTP
        assertFalse(ConfigManager.isValidWebhookUrl(
                "http://discord.com/api/webhooks/123456789012345678/token_123"));

        // Reject arbitrary hosts
        assertFalse(ConfigManager.isValidWebhookUrl(
                "https://attacker.com/api/webhooks/123456789012345678/token_123"));
        assertFalse(ConfigManager.isValidWebhookUrl(
                "https://discord.com.evil.com/api/webhooks/123456789012345678/token_123"));

        // Reject malformed paths or snowflakes
        assertFalse(ConfigManager.isValidWebhookUrl(
                "https://discord.com/api/other/123456789012345678/token_123"));
        assertFalse(ConfigManager.isValidWebhookUrl(
                "https://discord.com/api/webhooks/123/token_123"));
        assertFalse(ConfigManager.isValidWebhookUrl(
                "https://discord.com/api/webhooks/18446744073709551616/token_123")); // Exceeds unsigned 64-bit max
        assertFalse(ConfigManager.isValidWebhookUrl(
                "https://discord.com/api/webhooks/123456789012345678/")); // Missing token
        assertFalse(ConfigManager.isValidWebhookUrl(null));
        assertFalse(ConfigManager.isValidWebhookUrl(""));
    }

    @Test
    void masksWebhookUrlTokensSafely() {
        String masked = ConfigManager.maskWebhookUrl(
                "https://discord.com/api/webhooks/123456789012345678/super-secret-token");
        assertEquals("https://discord.com/api/webhooks/123456789012345678/********", masked);

        assertEquals("", ConfigManager.maskWebhookUrl(""));
        assertEquals("", ConfigManager.maskWebhookUrl(null));
        assertEquals("********", ConfigManager.maskWebhookUrl("invalid-url"));
    }

    @Test
    void parsesCustomEmbedFooterAndFallsBackToLegacyWhenOmitted() {
        String yamlWithFooter = """
                discord:
                  mode: "BOT"
                  bot-token: "%s"
                  channel-id: "%s"
                  embed:
                    footer:
                      text: "MineBridge | {server}"
                      icon-url: "https://example.com/icon.png"
                server:
                  name: "SMP Server"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yamlWithFooter), null);
        assertTrue(candidate.valid(), "Errors: " + candidate.errors());
        assertEquals("MineBridge | {server}", candidate.settings().embedFooter().text());
        assertEquals("https://example.com/icon.png", candidate.settings().embedFooter().iconUrl());
        assertEquals("SMP Server", candidate.settings().serverName());

        String yamlLegacyFooter = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                discord-events:
                  embeds:
                    footer: "Custom Legacy Footer"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate legacyCandidate = configManager().candidateFrom(parseYaml(yamlLegacyFooter), null);
        assertTrue(legacyCandidate.valid(), "Errors: " + legacyCandidate.errors());
        assertEquals("Custom Legacy Footer", legacyCandidate.settings().embedFooter().text());
        assertEquals("", legacyCandidate.settings().embedFooter().iconUrl());
    }

    @Test
    void acceptsServerAndPrefixPlaceholdersInTemplates() {
        String yaml = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                messages:
                  minecraft-to-discord: "[{server}] {prefix}{player}: {message}"
                  player-join: "🟢 {prefix}{player} joined {server}."
                  player-leave: "🔴 {prefix}{player} left {server}."
                  server-status: "&7{server} &8| &a{online}&7/&f{max} players online"
                  player-list: "&7{server} (&f{online}&7/&f{max}&7): {players}"
                death-messages:
                  messages:
                    player-kill: "&c☠ {prefix}{victim} was slain on {server} by {killer}"
                achievements:
                  messages:
                    normal: "&a✦ {prefix}{player} made an advancement on {server} [{title}]!"
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);
        assertTrue(candidate.valid(), "Errors: " + candidate.errors());
    }

    @Test
    void integrationModeHelperEnum() {
        assertEquals(IntegrationMode.BOT, IntegrationMode.fromString(null));
        assertEquals(IntegrationMode.BOT, IntegrationMode.fromString(""));
        assertEquals(IntegrationMode.BOT, IntegrationMode.fromString("  "));
        assertEquals(IntegrationMode.BOT, IntegrationMode.fromString("bot"));
        assertEquals(IntegrationMode.BOT, IntegrationMode.fromString("BOT"));
        assertEquals(IntegrationMode.WEBHOOK, IntegrationMode.fromString("webhook"));
        assertEquals(IntegrationMode.WEBHOOK, IntegrationMode.fromString("WEBHOOK"));
        assertEquals(IntegrationMode.HYBRID, IntegrationMode.fromString("hybrid"));
        assertEquals(IntegrationMode.HYBRID, IntegrationMode.fromString("HYBRID"));
        assertNull(IntegrationMode.fromString("unknown", null));
        assertEquals(IntegrationMode.BOT, IntegrationMode.fromString("unknown", IntegrationMode.BOT));
        assertEquals(IntegrationMode.HYBRID, IntegrationMode.fromString("unknown", IntegrationMode.HYBRID));
    }

    @Test
    void readsChatFormattingSettings() {
        String yaml = """
                discord:
                  bot-token: "%s"
                  channel-id: "%s"
                chat:
                  minecraft-to-discord:
                    format-conversion: true
                    allow-bold: false
                    allow-italic: true
                  discord-to-minecraft:
                    format-conversion: false
                    show-attachments: false
                """.formatted(VALID_TOKEN, VALID_CHANNEL);

        ConfigManager.Candidate candidate = configManager().candidateFrom(parseYaml(yaml), null);
        assertTrue(candidate.valid(), "Errors: " + candidate.errors());
        ConfigManager.ChatFormattingSettings formatting = candidate.settings().chatFormatting();
        assertNotNull(formatting);
        assertTrue(formatting.minecraftToDiscord().formatConversion());
        assertFalse(formatting.minecraftToDiscord().allowBold());
        assertTrue(formatting.minecraftToDiscord().allowItalic());
        assertFalse(formatting.discordToMinecraft().formatConversion());
        assertFalse(formatting.discordToMinecraft().showAttachments());
    }
}
