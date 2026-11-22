package com.minebridge.config;

import com.minebridge.discord.IntegrationMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultModularConfigIntegrityTest {

    @Test
    @DisplayName("Default modular resource templates (config, messages, events, advanced) are valid and complete")
    void defaultTemplatesAreValidAndComplete() {
        YamlConfiguration config = ConfigDefaults.defaultConfig();
        YamlConfiguration messages = ConfigDefaults.defaultMessages();
        YamlConfiguration events = ConfigDefaults.defaultEvents();
        YamlConfiguration advanced = ConfigDefaults.defaultAdvanced();

        assertNotNull(config);
        assertNotNull(messages);
        assertNotNull(events);
        assertNotNull(advanced);

        // 1. config.yml checks
        assertEquals("BOT", config.getString("discord.mode"));
        assertEquals("", config.getString("discord.bot-token"));
        assertEquals("", config.getString("discord.channel-id"));
        assertTrue(config.getBoolean("features.chat-relay"));
        assertTrue(config.getBoolean("features.join-leave"));
        assertTrue(config.getBoolean("features.deaths"));
        assertTrue(config.getBoolean("features.advancements"));
        assertTrue(config.getBoolean("features.server-status"));
        assertTrue(config.getBoolean("features.player-list"));
        assertEquals("Minecraft Server", config.getString("server.name"));
        assertTrue(config.getBoolean("update-checker.enabled"));

        // 2. messages.yml checks
        assertTrue(messages.contains("chat.minecraft-to-discord"));
        assertTrue(messages.contains("chat.discord-to-minecraft"));
        assertTrue(messages.contains("player.join"));
        assertTrue(messages.contains("player.leave"));
        assertTrue(messages.contains("player.first-join"));
        assertTrue(messages.contains("player.server-status"));
        assertTrue(messages.contains("player.player-list"));
        assertTrue(messages.contains("deaths.player-kill"));
        assertTrue(messages.contains("deaths.mob-kill"));
        assertTrue(messages.contains("deaths.fall"));
        assertTrue(messages.contains("deaths.lava"));
        assertTrue(messages.contains("deaths.fire"));
        assertTrue(messages.contains("deaths.drowning"));
        assertTrue(messages.contains("deaths.void"));
        assertTrue(messages.contains("deaths.explosion"));
        assertTrue(messages.contains("deaths.environmental"));
        assertTrue(messages.contains("deaths.other"));
        assertTrue(messages.contains("advancements.normal"));
        assertTrue(messages.contains("advancements.challenge"));

        // 3. events.yml checks
        assertTrue(events.getBoolean("player-events.enabled"));
        assertTrue(events.getBoolean("player-events.join"));
        assertTrue(events.getBoolean("player-events.leave"));
        assertTrue(events.getBoolean("player-events.first-join"));
        assertTrue(events.getBoolean("player-events.embeds.enabled"));
        assertTrue(events.getBoolean("deaths.enabled"));
        assertTrue(events.getBoolean("deaths.minecraft.enabled"));
        assertTrue(events.getBoolean("deaths.minecraft.broadcast"));
        assertTrue(events.getBoolean("deaths.discord.enabled"));
        assertTrue(events.getBoolean("deaths.discord.embed"));
        assertTrue(events.contains("deaths.categories"));
        assertTrue(events.getBoolean("advancements.enabled"));
        assertTrue(events.getBoolean("advancements.minecraft.enabled"));
        assertTrue(events.getBoolean("advancements.discord.enabled"));
        assertTrue(events.getBoolean("advancements.challenges.enabled"));
        assertTrue(events.contains("advancements.categories"));
        assertFalse(events.getBoolean("server-events.enabled"));
        assertTrue(events.getBoolean("server-events.start"));
        assertTrue(events.getBoolean("server-events.stop"));

        // 4. advanced.yml checks
        assertEquals("GLOBAL", advanced.getString("chat-routing.cross-world.mode"));
        assertTrue(advanced.getBoolean("formatting.minecraft-to-discord.format-conversion"));
        assertTrue(advanced.getBoolean("formatting.discord-to-minecraft.format-conversion"));
        assertEquals("{prefix}{player}", advanced.getString("webhook.username"));
        assertEquals("WEBHOOK", advanced.getString("hybrid.event-delivery"));
        assertEquals(ConfigDefaults.CURRENT_MIGRATION_VERSION, advanced.getInt("migration.version"));

        // 5. Candidate validation with supplied valid bot token and channel
        config.set("discord.bot-token", "synthetic-valid-bot-token-12345");
        config.set("discord.channel-id", "123456789012345678");

        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Candidate candidate = manager.candidateFromModular(config, messages, events, advanced, null);

        assertNotNull(candidate);
        assertTrue(candidate.valid(), "Candidate from default templates must be 100% valid: " + candidate.errors());
        assertEquals(IntegrationMode.BOT, candidate.settings().mode());
        assertEquals("synthetic-valid-bot-token-12345", candidate.settings().botToken());
        assertEquals("123456789012345678", candidate.settings().channelId());
        assertEquals("Minecraft Server", candidate.settings().serverName());

        // 6. Test HYBRID mode candidate with webhook URL
        config.set("discord.mode", "HYBRID");
        config.set("discord.webhook.url", "https://discord.com/api/webhooks/123456789012345678/token_xyz");

        ConfigManager.Candidate hybridCandidate = manager.candidateFromModular(config, messages, events, advanced, null);
        assertTrue(hybridCandidate.valid(), "HYBRID candidate must be valid: " + hybridCandidate.errors());
        assertEquals(IntegrationMode.HYBRID, hybridCandidate.settings().mode());
        assertEquals(IntegrationMode.WEBHOOK, hybridCandidate.settings().hybrid().eventDelivery());

        // 7. Test WEBHOOK mode candidate
        config.set("discord.mode", "WEBHOOK");
        ConfigManager.Candidate webhookCandidate = manager.candidateFromModular(config, messages, events, advanced, null);
        assertTrue(webhookCandidate.valid(), "WEBHOOK candidate must be valid: " + webhookCandidate.errors());
        assertEquals(IntegrationMode.WEBHOOK, webhookCandidate.settings().mode());
    }
}
