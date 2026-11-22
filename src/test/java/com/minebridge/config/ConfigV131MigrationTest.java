package com.minebridge.config;

import com.minebridge.discord.IntegrationMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigV131MigrationTest {

    @TempDir
    File dataFolder;

    @Test
    @DisplayName("Realistic v1.3.1 legacy configuration migrates accurately into the 4-file structure")
    void migratesRealisticV131Configuration() throws Exception {
        File configFile = new File(dataFolder, ConfigDefaults.FILE_CONFIG);

        // Build authentic v1.3.1 configuration with customizations
        YamlConfiguration legacyV131 = new YamlConfiguration();
        legacyV131.set("discord.mode", "BOT");
        legacyV131.set("discord.bot-token", "synthetic-v131-token-98765");
        legacyV131.set("discord.channel-id", "123456789012345678");

        legacyV131.set("discord.webhook.url", "https://discord.com/api/webhooks/123456789012345678/legacy_wh_token");
        legacyV131.set("discord.webhook.username", "[SMP] {player}");
        legacyV131.set("discord.webhook.avatar-url", "https://mc-heads.net/avatar/{player}/100");

        legacyV131.set("discord.embed.footer.text", "MineBridge v1.3.1 Legacy Server");
        legacyV131.set("discord.embed.footer.icon-url", "https://example.com/footer-icon.png");

        legacyV131.set("server.name", "Survival Realm v1.3.1");

        legacyV131.set("chat.cross-world.mode", "GLOBAL");
        legacyV131.set("chat.cross-world.enabled-worlds", List.of());

        legacyV131.set("messages.minecraft-to-discord", "{prefix}**{player}**: {message}");
        legacyV131.set("messages.discord-to-minecraft", "&9[Discord] &f{player}: {message}");
        legacyV131.set("messages.player-join", "👋 **{player}** logged in!");
        legacyV131.set("messages.player-leave", "🚪 **{player}** logged out!");
        legacyV131.set("messages.server-status", "&7Server &8| &a{online}&7/&f{max}");
        legacyV131.set("messages.player-list", "&7Online: {players}");

        legacyV131.set("features.chat-relay", true);
        legacyV131.set("features.join-leave-notifications", true);
        legacyV131.set("features.server-status", true);
        legacyV131.set("features.player-list", true);
        legacyV131.set("update-checker.enabled", true);

        legacyV131.set("death-messages.enabled", true);
        legacyV131.set("death-messages.minecraft.enabled", true);
        legacyV131.set("death-messages.minecraft.broadcast", true);
        legacyV131.set("death-messages.discord.enabled", true);
        legacyV131.set("death-messages.discord.channel-id", "123456789012345679");
        legacyV131.set("death-messages.discord.embed", true);
        legacyV131.set("death-messages.discord.title", "☠ Player Perished");
        legacyV131.set("death-messages.discord.description", "{message}\nCategory: {category}");
        legacyV131.set("death-messages.discord.color", "#B71C1C");
        legacyV131.set("death-messages.categories.player-kill", true);
        legacyV131.set("death-messages.categories.mob-kill", true);
        legacyV131.set("death-messages.categories.fall", true);
        legacyV131.set("death-messages.categories.lava", false); // Custom disabled category
        legacyV131.set("death-messages.categories.fire", true);
        legacyV131.set("death-messages.categories.drowning", true);
        legacyV131.set("death-messages.categories.void", true);
        legacyV131.set("death-messages.categories.explosion", true);
        legacyV131.set("death-messages.categories.environmental", true);
        legacyV131.set("death-messages.categories.other", true);
        legacyV131.set("death-messages.messages.lava", "&c☠ {victim} melted in hot lava!");

        legacyV131.set("achievements.enabled", true);
        legacyV131.set("achievements.minecraft.enabled", true);
        legacyV131.set("achievements.discord.enabled", true);
        legacyV131.set("achievements.discord.channel-id", "123456789012345680");
        legacyV131.set("achievements.discord.title", "ACHIEVEMENT UNLOCKED");
        legacyV131.set("achievements.discord.description", "{player} got {title}!");
        legacyV131.set("achievements.discord.challenge-title", "CHALLENGE BEATEN");
        legacyV131.set("achievements.discord.challenge-description", "{player} conquered {title}!");
        legacyV131.set("achievements.discord.color", "#1E88E5");
        legacyV131.set("achievements.challenges.enabled", true);
        legacyV131.set("achievements.categories.story", true);
        legacyV131.set("achievements.categories.nether", true);
        legacyV131.set("achievements.categories.end", false); // Custom disabled category
        legacyV131.set("achievements.categories.adventure", true);
        legacyV131.set("achievements.categories.husbandry", true);
        legacyV131.set("achievements.categories.other", true);
        legacyV131.set("achievements.messages.normal", "&a✦ {player} reached advancement [{title}]!");
        legacyV131.set("achievements.messages.challenge", "&6🏆 {player} beat challenge [{title}]!");

        legacyV131.set("discord-events.enabled", true);
        legacyV131.set("discord-events.embeds.enabled", true);
        legacyV131.set("discord-events.embeds.footer", "MineBridge | Minecraft Events");
        legacyV131.set("discord-events.player-events.enabled", true);
        legacyV131.set("discord-events.player-events.channel-id", "123456789012345681");
        legacyV131.set("discord-events.player-events.join", true);
        legacyV131.set("discord-events.player-events.leave", true);
        legacyV131.set("discord-events.player-events.first-join", false); // Custom disabled
        legacyV131.set("discord-events.achievements.enabled", true);
        legacyV131.set("discord-events.achievements.channel-id", "123456789012345680");
        legacyV131.set("discord-events.deaths.enabled", true);
        legacyV131.set("discord-events.deaths.channel-id", "123456789012345679");

        legacyV131.save(configFile);

        // 1. Verify migration detection
        assertTrue(ConfigMigrationManager.isLegacy(dataFolder));

        // 2. Perform Migration
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        ConfigMigrationManager.MigrationResult result =
                ConfigMigrationManager.migrate(dataFolder, infoLogs::add, warnLogs::add);

        assertTrue(result.migrated(), "Migration should succeed");
        assertTrue(result.keysMigrated() >= 40, "Should migrate all customized keys");
        assertNotNull(result.backupPath());
        assertTrue(new File(result.backupPath()).exists(), "Backup file must be on disk");

        // 3. Verify target files exist
        File targetConfig = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
        File targetMessages = new File(dataFolder, ConfigDefaults.FILE_MESSAGES);
        File targetEvents = new File(dataFolder, ConfigDefaults.FILE_EVENTS);
        File targetAdvanced = new File(dataFolder, ConfigDefaults.FILE_ADVANCED);

        assertTrue(targetConfig.exists());
        assertTrue(targetMessages.exists());
        assertTrue(targetEvents.exists());
        assertTrue(targetAdvanced.exists());

        // 4. Verify migrated values in each file
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(targetConfig);
        assertEquals("BOT", cfg.getString("discord.mode"));
        assertEquals("synthetic-v131-token-98765", cfg.getString("discord.bot-token"));
        assertEquals("123456789012345678", cfg.getString("discord.channel-id"));
        assertEquals("Survival Realm v1.3.1", cfg.getString("server.name"));
        assertTrue(cfg.getBoolean("features.chat-relay"));
        assertTrue(cfg.getBoolean("features.join-leave"));
        assertTrue(cfg.getBoolean("features.deaths"));
        assertTrue(cfg.getBoolean("features.advancements"));

        YamlConfiguration msg = YamlConfiguration.loadConfiguration(targetMessages);
        assertEquals("👋 **{player}** logged in!", msg.getString("player.join"));
        assertEquals("🚪 **{player}** logged out!", msg.getString("player.leave"));
        assertEquals("&c☠ {victim} melted in hot lava!", msg.getString("deaths.lava"));
        assertEquals("&6🏆 {player} beat challenge [{title}]!", msg.getString("advancements.challenge"));

        YamlConfiguration ev = YamlConfiguration.loadConfiguration(targetEvents);
        assertEquals("123456789012345681", ev.getString("player-events.channel-id"));
        assertFalse(ev.getBoolean("player-events.first-join"));
        assertEquals("123456789012345679", ev.getString("deaths.discord.channel-id"));
        assertEquals("☠ Player Perished", ev.getString("deaths.discord.title"));
        assertEquals("#B71C1C", ev.getString("deaths.discord.color"));
        assertFalse(ev.getBoolean("deaths.categories.lava"));
        assertEquals("123456789012345680", ev.getString("advancements.discord.channel-id"));
        assertFalse(ev.getBoolean("advancements.categories.end"));

        YamlConfiguration adv = YamlConfiguration.loadConfiguration(targetAdvanced);
        assertEquals("[SMP] {player}", adv.getString("webhook.username"));
        assertEquals("MineBridge v1.3.1 Legacy Server", adv.getString("embeds.footer.text"));
        assertEquals(ConfigDefaults.CURRENT_MIGRATION_VERSION, adv.getInt("migration.version"));

        // 5. Test Plugin Integration: Mock Plugin & ConfigManager.loadCandidate()
        org.bukkit.plugin.Plugin mockPlugin = (org.bukkit.plugin.Plugin) Proxy.newProxyInstance(
                org.bukkit.plugin.Plugin.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.Plugin.class},
                (proxy, method, args) -> {
                    if ("getDataFolder".equals(method.getName())) return dataFolder;
                    if ("getLogger".equals(method.getName())) return java.util.logging.Logger.getGlobal();
                    return null;
                });

        ConfigManager configManager = new ConfigManager(mockPlugin);
        ConfigManager.Candidate candidate = configManager.loadCandidate();

        assertNotNull(candidate);
        assertTrue(candidate.valid(), "Migrated configuration candidate must be completely valid: " + candidate.errors());

        configManager.commit(candidate);

        // Verify loaded settings match the v1.3.1 customizations
        assertEquals(IntegrationMode.BOT, configManager.mode());
        assertEquals("synthetic-v131-token-98765", configManager.getBotToken());
        assertEquals("123456789012345678", configManager.getChannelId());
        assertEquals("Survival Realm v1.3.1", configManager.serverName());
        assertEquals("MineBridge v1.3.1 Legacy Server", configManager.embedFooter().text());
        assertEquals("[SMP] {player}", configManager.webhook().username());

        ConfigManager.DeathSettings deathSettings = candidate.settings().deathMessages();
        assertFalse(deathSettings.categories().get("lava"));
        assertEquals(0xB71C1C, deathSettings.embedColor());
        assertEquals("&c☠ {victim} melted in hot lava!", deathSettings.messages().get("lava"));

        ConfigManager.AchievementSettings achSettings = candidate.settings().achievements();
        assertFalse(achSettings.categories().get("end"));
        assertEquals(0x1E88E5, achSettings.embedColor());

        ConfigManager.DiscordEventsSettings discEvents = candidate.settings().discordEvents();
        assertEquals("123456789012345681", discEvents.playerEventsChannelId());
        assertFalse(discEvents.firstJoinEnabled());

        // 6. Idempotency verification: subsequent migration run is not needed
        assertFalse(ConfigMigrationManager.isLegacy(dataFolder));
        ConfigMigrationManager.MigrationResult secondRun =
                ConfigMigrationManager.migrate(dataFolder, infoLogs::add, warnLogs::add);
        assertFalse(secondRun.migrated());
    }
}
