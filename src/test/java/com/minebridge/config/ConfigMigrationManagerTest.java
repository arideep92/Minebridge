package com.minebridge.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationManagerTest {

    @TempDir
    File dataFolder;

    @Test
    @DisplayName("Detects legacy single-file configuration and rejects modern 4-file configuration")
    void detectsLegacyConfiguration() throws Exception {
        assertFalse(ConfigMigrationManager.isLegacy(dataFolder));

        File configFile = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
        YamlConfiguration legacyYaml = new YamlConfiguration();
        legacyYaml.set("discord.mode", "BOT");
        legacyYaml.set("death-messages.enabled", true);
        legacyYaml.set("discord-events.enabled", true);
        legacyYaml.save(configFile);

        assertTrue(ConfigMigrationManager.isLegacy(dataFolder), "Should detect legacy config with death-messages");

        // Now simulate completed migration with advanced.yml containing migration.version >= 1
        File advancedFile = new File(dataFolder, ConfigDefaults.FILE_ADVANCED);
        YamlConfiguration advancedYaml = new YamlConfiguration();
        advancedYaml.set("migration.version", ConfigDefaults.CURRENT_MIGRATION_VERSION);
        advancedYaml.save(advancedFile);

        // Remove legacy keys from config.yml
        YamlConfiguration modernConfig = new YamlConfiguration();
        modernConfig.set("discord.mode", "BOT");
        modernConfig.set("features.deaths", true);
        modernConfig.save(configFile);

        assertFalse(ConfigMigrationManager.isLegacy(dataFolder), "Modern configuration must not be flagged as legacy");
    }

    @Test
    @DisplayName("Migrates all key groups, creates backup, preserves custom values, and is idempotent")
    void migratesLegacyConfigurationFully() throws Exception {
        File configFile = new File(dataFolder, ConfigDefaults.FILE_CONFIG);

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("discord.mode", "HYBRID");
        legacy.set("discord.bot-token", "legacy-token-abc");
        legacy.set("discord.channel-id", "123456789012345678");
        legacy.set("discord.webhook.url", "https://discord.com/api/webhooks/123456789012345678/token_xyz");
        legacy.set("discord.webhook.username", "CustomBot");
        legacy.set("server.name", "Epic SMP Server");
        legacy.set("features.chat-relay", true);
        legacy.set("features.join-leave-notifications", false);
        legacy.set("messages.player-join", "✨ {player} arrived!");
        legacy.set("death-messages.enabled", true);
        legacy.set("death-messages.categories.lava", false);
        legacy.set("death-messages.messages.lava", "{victim} tried swimming in lava!");
        legacy.set("achievements.enabled", true);
        legacy.set("achievements.messages.challenge", "Challenge done by {player}!");
        legacy.set("discord-events.server-events.enabled", true);
        legacy.set("discord-events.server-events.channel-id", "987654321098765432");
        legacy.set("chat.cross-world.mode", "PER_WORLD");
        legacy.set("chat.cross-world.enabled-worlds", List.of("world", "world_nether"));
        legacy.set("chat.minecraft-to-discord.format-conversion", false);
        legacy.set("custom.unrecognized.key", "custom_value_to_preserve");

        legacy.save(configFile);

        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();

        ConfigMigrationManager.MigrationResult result =
                ConfigMigrationManager.migrate(dataFolder, infoLogs::add, warnLogs::add);

        assertTrue(result.migrated());
        assertTrue(result.keysMigrated() > 10, "Migrated keys: " + result.keysMigrated());
        assertEquals(1, result.unrecognizedKeys(), "Unrecognized keys count");
        assertNotNull(result.backupPath());
        assertTrue(new File(result.backupPath()).exists(), "Backup file must exist on disk");

        // Verify the 4 files exist
        File targetConfig = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
        File targetMessages = new File(dataFolder, ConfigDefaults.FILE_MESSAGES);
        File targetEvents = new File(dataFolder, ConfigDefaults.FILE_EVENTS);
        File targetAdvanced = new File(dataFolder, ConfigDefaults.FILE_ADVANCED);

        assertTrue(targetConfig.exists());
        assertTrue(targetMessages.exists());
        assertTrue(targetEvents.exists());
        assertTrue(targetAdvanced.exists());

        // Verify values migrated correctly
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(targetConfig);
        assertEquals("HYBRID", cfg.getString("discord.mode"));
        assertEquals("legacy-token-abc", cfg.getString("discord.bot-token"));
        assertEquals("123456789012345678", cfg.getString("discord.channel-id"));
        assertEquals("https://discord.com/api/webhooks/123456789012345678/token_xyz", cfg.getString("discord.webhook.url"));
        assertEquals("Epic SMP Server", cfg.getString("server.name"));
        assertFalse(cfg.getBoolean("features.join-leave"));

        YamlConfiguration msg = YamlConfiguration.loadConfiguration(targetMessages);
        assertEquals("✨ {player} arrived!", msg.getString("player.join"));
        assertEquals("{victim} tried swimming in lava!", msg.getString("deaths.lava"));
        assertEquals("Challenge done by {player}!", msg.getString("advancements.challenge"));

        YamlConfiguration ev = YamlConfiguration.loadConfiguration(targetEvents);
        assertTrue(ev.getBoolean("deaths.enabled"));
        assertFalse(ev.getBoolean("deaths.categories.lava"));
        assertTrue(ev.getBoolean("server-events.enabled"));
        assertEquals("987654321098765432", ev.getString("server-events.channel-id"));

        YamlConfiguration adv = YamlConfiguration.loadConfiguration(targetAdvanced);
        assertEquals("CustomBot", adv.getString("webhook.username"));
        assertEquals("PER_WORLD", adv.getString("chat-routing.cross-world.mode"));
        assertEquals(List.of("world", "world_nether"), adv.getStringList("chat-routing.cross-world.enabled-worlds"));
        assertFalse(adv.getBoolean("formatting.minecraft-to-discord.format-conversion"));
        assertEquals(ConfigDefaults.CURRENT_MIGRATION_VERSION, adv.getInt("migration.version"));

        // Verify backup contains the unrecognized key
        YamlConfiguration backupYaml = YamlConfiguration.loadConfiguration(new File(result.backupPath()));
        assertEquals("custom_value_to_preserve", backupYaml.getString("custom.unrecognized.key"));

        // Idempotency check: running migrate again must be NOT_NEEDED
        ConfigMigrationManager.MigrationResult secondRun =
                ConfigMigrationManager.migrate(dataFolder, infoLogs::add, warnLogs::add);
        assertFalse(secondRun.migrated());
    }
}
