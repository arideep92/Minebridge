package com.minebridge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages detection, backup, transformation, and validation of legacy single-file
 * configuration (v1.0.0–v1.3.4) into the modular 4-file structure.
 */
public final class ConfigMigrationManager {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static final Set<String> KNOWN_LEGACY_KEYS = Set.of(
            "discord.mode", "discord.bot-token", "discord.channel-id",
            "discord.webhook.url", "discord.webhook.username", "discord.webhook.avatar-url",
            "discord.hybrid.event-delivery", "discord.embed.footer.text", "discord.embed.footer.icon-url",
            "server.name", "update-checker.enabled",
            "chat.cross-world.mode", "chat.cross-world.enabled-worlds",
            "chat.minecraft-to-discord.format-conversion", "chat.minecraft-to-discord.allow-bold",
            "chat.minecraft-to-discord.allow-italic", "chat.minecraft-to-discord.allow-underline",
            "chat.minecraft-to-discord.allow-strikethrough", "chat.minecraft-to-discord.allow-obfuscated",
            "chat.discord-to-minecraft.format-conversion", "chat.discord-to-minecraft.allow-bold",
            "chat.discord-to-minecraft.allow-italic", "chat.discord-to-minecraft.allow-underline",
            "chat.discord-to-minecraft.allow-strikethrough", "chat.discord-to-minecraft.allow-code",
            "chat.discord-to-minecraft.show-attachments",
            "messages.minecraft-to-discord", "messages.discord-to-minecraft",
            "messages.player-join", "messages.player-leave",
            "messages.server-status", "messages.player-list",
            "features.chat-relay", "features.join-leave", "features.join-leave-notifications",
            "features.server-status", "features.player-list",
            "death-messages.enabled", "death-messages.minecraft.enabled", "death-messages.minecraft.broadcast",
            "death-messages.discord.enabled", "death-messages.discord.channel-id", "death-messages.discord.embed",
            "death-messages.discord.title", "death-messages.discord.description", "death-messages.discord.color",
            "death-messages.categories.player-kill", "death-messages.categories.mob-kill",
            "death-messages.categories.fall", "death-messages.categories.lava", "death-messages.categories.fire",
            "death-messages.categories.drowning", "death-messages.categories.void",
            "death-messages.categories.explosion", "death-messages.categories.environmental",
            "death-messages.categories.other",
            "death-messages.messages.player-kill", "death-messages.messages.mob-kill",
            "death-messages.messages.fall", "death-messages.messages.lava", "death-messages.messages.fire",
            "death-messages.messages.drowning", "death-messages.messages.void",
            "death-messages.messages.explosion", "death-messages.messages.environmental",
            "death-messages.messages.other",
            "achievements.enabled", "achievements.minecraft.enabled",
            "achievements.discord.enabled", "achievements.discord.channel-id",
            "achievements.discord.title", "achievements.discord.description",
            "achievements.discord.challenge-title", "achievements.discord.challenge-description",
            "achievements.discord.color", "achievements.challenges.enabled",
            "achievements.categories.story", "achievements.categories.nether", "achievements.categories.end",
            "achievements.categories.adventure", "achievements.categories.husbandry", "achievements.categories.other",
            "achievements.messages.normal", "achievements.messages.challenge",
            "discord-events.enabled", "discord-events.embeds.enabled", "discord-events.embeds.footer",
            "discord-events.player-events.enabled", "discord-events.player-events.channel-id",
            "discord-events.player-events.join", "discord-events.player-events.leave",
            "discord-events.player-events.first-join",
            "discord-events.achievements.enabled", "discord-events.achievements.channel-id",
            "discord-events.achievements.story", "discord-events.achievements.nether", "discord-events.achievements.end",
            "discord-events.achievements.adventure", "discord-events.achievements.husbandry",
            "discord-events.achievements.challenges",
            "discord-events.deaths.enabled", "discord-events.deaths.channel-id",
            "discord-events.deaths.player-kill", "discord-events.deaths.mob-kill", "discord-events.deaths.environmental",
            "discord-events.server-events.enabled", "discord-events.server-events.channel-id",
            "discord-events.server-events.start", "discord-events.server-events.stop"
    );

    public record MigrationResult(
            boolean migrated,
            int keysMigrated,
            int unrecognizedKeys,
            String backupPath,
            List<String> warnings) {
        public static final MigrationResult NOT_NEEDED =
                new MigrationResult(false, 0, 0, "", List.of());
    }

    private ConfigMigrationManager() {
    }

    /**
     * Checks if the data folder contains a legacy single-file configuration format.
     */
    public static boolean isLegacy(File dataFolder) {
        if (dataFolder == null || !dataFolder.exists()) {
            return false;
        }

        File configFile = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
        if (!configFile.exists()) {
            return false;
        }

        File advancedFile = new File(dataFolder, ConfigDefaults.FILE_ADVANCED);
        if (advancedFile.exists()) {
            YamlConfiguration advanced = new YamlConfiguration();
            try {
                advanced.load(advancedFile);
                if (advanced.getInt("migration.version", 0) >= ConfigDefaults.CURRENT_MIGRATION_VERSION) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
            return config.contains("death-messages")
                    || config.contains("discord-events")
                    || config.contains("achievements")
                    || config.contains("messages.minecraft-to-discord")
                    || config.contains("messages.discord-to-minecraft")
                    || config.contains("chat.cross-world")
                    || config.contains("features.join-leave-notifications");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Migrates legacy configuration into the 4-file modular layout.
     * Idempotent, creates timestamped backups, and uses staged validation.
     */
    public static MigrationResult migrate(
            File dataFolder,
            Consumer<String> infoLogger,
            Consumer<String> warnLogger) {
        if (!isLegacy(dataFolder)) {
            return MigrationResult.NOT_NEEDED;
        }

        File configFile = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
        if (!configFile.exists()) {
            return MigrationResult.NOT_NEEDED;
        }

        List<String> warnings = new ArrayList<>();
        AtomicInteger migratedCount = new AtomicInteger(0);
        AtomicInteger unrecognizedCount = new AtomicInteger(0);

        // 1. Create timestamped backup under backups/
        File backupDir = new File(dataFolder, "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        File backupFile = new File(backupDir, "config-legacy-" + timestamp + ".yml");
        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (infoLogger != null) {
                infoLogger.accept("Legacy configuration detected. Created backup at " + backupFile.getName());
            }
        } catch (IOException e) {
            if (warnLogger != null) {
                warnLogger.accept("Failed to create backup before migration: " + e.getMessage());
            }
            return new MigrationResult(false, 0, 0, "", List.of("Backup failed: " + e.getMessage()));
        }

        // 2. Load legacy YAML
        YamlConfiguration legacy = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            legacy.load(reader);
        } catch (Exception e) {
            if (warnLogger != null) {
                warnLogger.accept("Could not read legacy config for migration: " + e.getMessage());
            }
            return new MigrationResult(false, 0, 0, backupFile.getAbsolutePath(), List.of("Read failed: " + e.getMessage()));
        }

        // Identify any unrecognized leaf keys
        for (String key : legacy.getKeys(true)) {
            if (!legacy.isConfigurationSection(key)) {
                if (!KNOWN_LEGACY_KEYS.contains(key)) {
                    unrecognizedCount.incrementAndGet();
                    warnings.add("Unrecognized configuration key '" + key + "' was preserved in legacy backup: " + backupFile.getName());
                }
            }
        }

        // 3. Prepare Staging Directory
        File stagingDir = new File(dataFolder, ".migration-staging-" + System.currentTimeMillis());
        stagingDir.mkdirs();

        try {
            File stagingConfig = new File(stagingDir, ConfigDefaults.FILE_CONFIG);
            File stagingMessages = new File(stagingDir, ConfigDefaults.FILE_MESSAGES);
            File stagingEvents = new File(stagingDir, ConfigDefaults.FILE_EVENTS);
            File stagingAdvanced = new File(stagingDir, ConfigDefaults.FILE_ADVANCED);

            YamlConfiguration targetConfig = ConfigDefaults.defaultConfig();
            YamlConfiguration targetMessages = ConfigDefaults.defaultMessages();
            YamlConfiguration targetEvents = ConfigDefaults.defaultEvents();
            YamlConfiguration targetAdvanced = ConfigDefaults.defaultAdvanced();

            // 4. Map keys to config.yml
            migrateKey(legacy, "discord.mode", targetConfig, "discord.mode", migratedCount);
            migrateKey(legacy, "discord.bot-token", targetConfig, "discord.bot-token", migratedCount);
            migrateKey(legacy, "discord.channel-id", targetConfig, "discord.channel-id", migratedCount);
            migrateKey(legacy, "discord.webhook.url", targetConfig, "discord.webhook.url", migratedCount);
            migrateKey(legacy, "features.chat-relay", targetConfig, "features.chat-relay", migratedCount);
            if (legacy.contains("features.join-leave-notifications")) {
                migrateKey(legacy, "features.join-leave-notifications", targetConfig, "features.join-leave", migratedCount);
            } else {
                migrateKey(legacy, "features.join-leave", targetConfig, "features.join-leave", migratedCount);
            }
            if (legacy.contains("death-messages.enabled")) {
                migrateKey(legacy, "death-messages.enabled", targetConfig, "features.deaths", migratedCount);
            }
            if (legacy.contains("achievements.enabled")) {
                migrateKey(legacy, "achievements.enabled", targetConfig, "features.advancements", migratedCount);
            }
            migrateKey(legacy, "features.server-status", targetConfig, "features.server-status", migratedCount);
            migrateKey(legacy, "features.player-list", targetConfig, "features.player-list", migratedCount);
            migrateKey(legacy, "server.name", targetConfig, "server.name", migratedCount);
            migrateKey(legacy, "update-checker.enabled", targetConfig, "update-checker.enabled", migratedCount);

            // 5. Map keys to messages.yml
            migrateKey(legacy, "messages.minecraft-to-discord", targetMessages, "chat.minecraft-to-discord", migratedCount);
            migrateKey(legacy, "messages.discord-to-minecraft", targetMessages, "chat.discord-to-minecraft", migratedCount);
            migrateKey(legacy, "messages.player-join", targetMessages, "player.join", migratedCount);
            migrateKey(legacy, "messages.player-leave", targetMessages, "player.leave", migratedCount);
            migrateKey(legacy, "messages.server-status", targetMessages, "player.server-status", migratedCount);
            migrateKey(legacy, "messages.player-list", targetMessages, "player.player-list", migratedCount);

            ConfigurationSection deathMsgSec = legacy.getConfigurationSection("death-messages.messages");
            if (deathMsgSec != null) {
                for (String key : deathMsgSec.getKeys(false)) {
                    migrateKey(legacy, "death-messages.messages." + key, targetMessages, "deaths." + key, migratedCount);
                }
            }

            migrateKey(legacy, "achievements.messages.normal", targetMessages, "advancements.normal", migratedCount);
            migrateKey(legacy, "achievements.messages.challenge", targetMessages, "advancements.challenge", migratedCount);

            // 6. Map keys to events.yml
            migrateKey(legacy, "discord-events.player-events.enabled", targetEvents, "player-events.enabled", migratedCount);
            migrateKey(legacy, "discord-events.player-events.channel-id", targetEvents, "player-events.channel-id", migratedCount);
            migrateKey(legacy, "discord-events.player-events.join", targetEvents, "player-events.join", migratedCount);
            migrateKey(legacy, "discord-events.player-events.leave", targetEvents, "player-events.leave", migratedCount);
            migrateKey(legacy, "discord-events.player-events.first-join", targetEvents, "player-events.first-join", migratedCount);
            migrateKey(legacy, "discord-events.embeds.enabled", targetEvents, "player-events.embeds.enabled", migratedCount);

            migrateKey(legacy, "death-messages.enabled", targetEvents, "deaths.enabled", migratedCount);
            migrateKey(legacy, "death-messages.minecraft.enabled", targetEvents, "deaths.minecraft.enabled", migratedCount);
            migrateKey(legacy, "death-messages.minecraft.broadcast", targetEvents, "deaths.minecraft.broadcast", migratedCount);
            migrateKey(legacy, "death-messages.discord.enabled", targetEvents, "deaths.discord.enabled", migratedCount);
            migrateKey(legacy, "death-messages.discord.channel-id", targetEvents, "deaths.discord.channel-id", migratedCount);
            migrateKey(legacy, "death-messages.discord.embed", targetEvents, "deaths.discord.embed", migratedCount);
            migrateKey(legacy, "death-messages.discord.title", targetEvents, "deaths.discord.title", migratedCount);
            migrateKey(legacy, "death-messages.discord.description", targetEvents, "deaths.discord.description", migratedCount);
            migrateKey(legacy, "death-messages.discord.color", targetEvents, "deaths.discord.color", migratedCount);

            ConfigurationSection deathCatSec = legacy.getConfigurationSection("death-messages.categories");
            if (deathCatSec != null) {
                for (String key : deathCatSec.getKeys(false)) {
                    migrateKey(legacy, "death-messages.categories." + key, targetEvents, "deaths.categories." + key, migratedCount);
                }
            }

            migrateKey(legacy, "achievements.enabled", targetEvents, "advancements.enabled", migratedCount);
            migrateKey(legacy, "achievements.minecraft.enabled", targetEvents, "advancements.minecraft.enabled", migratedCount);
            migrateKey(legacy, "achievements.discord.enabled", targetEvents, "advancements.discord.enabled", migratedCount);
            migrateKey(legacy, "achievements.discord.channel-id", targetEvents, "advancements.discord.channel-id", migratedCount);
            migrateKey(legacy, "achievements.discord.title", targetEvents, "advancements.discord.title", migratedCount);
            migrateKey(legacy, "achievements.discord.description", targetEvents, "advancements.discord.description", migratedCount);
            migrateKey(legacy, "achievements.discord.challenge-title", targetEvents, "advancements.discord.challenge-title", migratedCount);
            migrateKey(legacy, "achievements.discord.challenge-description", targetEvents, "advancements.discord.challenge-description", migratedCount);
            migrateKey(legacy, "achievements.discord.color", targetEvents, "advancements.discord.color", migratedCount);
            migrateKey(legacy, "achievements.challenges.enabled", targetEvents, "advancements.challenges.enabled", migratedCount);

            ConfigurationSection achCatSec = legacy.getConfigurationSection("achievements.categories");
            if (achCatSec != null) {
                for (String key : achCatSec.getKeys(false)) {
                    migrateKey(legacy, "achievements.categories." + key, targetEvents, "advancements.categories." + key, migratedCount);
                }
            }

            migrateKey(legacy, "discord-events.server-events.enabled", targetEvents, "server-events.enabled", migratedCount);
            migrateKey(legacy, "discord-events.server-events.channel-id", targetEvents, "server-events.channel-id", migratedCount);
            migrateKey(legacy, "discord-events.server-events.start", targetEvents, "server-events.start", migratedCount);
            migrateKey(legacy, "discord-events.server-events.stop", targetEvents, "server-events.stop", migratedCount);

            // 7. Map keys to advanced.yml
            migrateKey(legacy, "chat.cross-world.mode", targetAdvanced, "chat-routing.cross-world.mode", migratedCount);
            migrateKey(legacy, "chat.cross-world.enabled-worlds", targetAdvanced, "chat-routing.cross-world.enabled-worlds", migratedCount);

            migrateKey(legacy, "chat.minecraft-to-discord.format-conversion", targetAdvanced, "formatting.minecraft-to-discord.format-conversion", migratedCount);
            migrateKey(legacy, "chat.minecraft-to-discord.allow-bold", targetAdvanced, "formatting.minecraft-to-discord.allow-bold", migratedCount);
            migrateKey(legacy, "chat.minecraft-to-discord.allow-italic", targetAdvanced, "formatting.minecraft-to-discord.allow-italic", migratedCount);
            migrateKey(legacy, "chat.minecraft-to-discord.allow-underline", targetAdvanced, "formatting.minecraft-to-discord.allow-underline", migratedCount);
            migrateKey(legacy, "chat.minecraft-to-discord.allow-strikethrough", targetAdvanced, "formatting.minecraft-to-discord.allow-strikethrough", migratedCount);
            migrateKey(legacy, "chat.minecraft-to-discord.allow-obfuscated", targetAdvanced, "formatting.minecraft-to-discord.allow-obfuscated", migratedCount);

            migrateKey(legacy, "chat.discord-to-minecraft.format-conversion", targetAdvanced, "formatting.discord-to-minecraft.format-conversion", migratedCount);
            migrateKey(legacy, "chat.discord-to-minecraft.allow-bold", targetAdvanced, "formatting.discord-to-minecraft.allow-bold", migratedCount);
            migrateKey(legacy, "chat.discord-to-minecraft.allow-italic", targetAdvanced, "formatting.discord-to-minecraft.allow-italic", migratedCount);
            migrateKey(legacy, "chat.discord-to-minecraft.allow-underline", targetAdvanced, "formatting.discord-to-minecraft.allow-underline", migratedCount);
            migrateKey(legacy, "chat.discord-to-minecraft.allow-strikethrough", targetAdvanced, "formatting.discord-to-minecraft.allow-strikethrough", migratedCount);
            migrateKey(legacy, "chat.discord-to-minecraft.allow-code", targetAdvanced, "formatting.discord-to-minecraft.allow-code", migratedCount);
            migrateKey(legacy, "chat.discord-to-minecraft.show-attachments", targetAdvanced, "formatting.discord-to-minecraft.show-attachments", migratedCount);

            migrateKey(legacy, "discord.webhook.username", targetAdvanced, "webhook.username", migratedCount);
            migrateKey(legacy, "discord.webhook.avatar-url", targetAdvanced, "webhook.avatar-url", migratedCount);
            migrateKey(legacy, "discord.hybrid.event-delivery", targetAdvanced, "hybrid.event-delivery", migratedCount);
            migrateKey(legacy, "discord.embed.footer.text", targetAdvanced, "embeds.footer.text", migratedCount);
            migrateKey(legacy, "discord.embed.footer.icon-url", targetAdvanced, "embeds.footer.icon-url", migratedCount);
            migrateKey(legacy, "discord-events.embeds.footer", targetAdvanced, "embeds.footer.default-text", migratedCount);

            targetAdvanced.set("migration.version", ConfigDefaults.CURRENT_MIGRATION_VERSION);

            // 8. Save staged files
            targetConfig.save(stagingConfig);
            targetMessages.save(stagingMessages);
            targetEvents.save(stagingEvents);
            targetAdvanced.save(stagingAdvanced);

            // 9. Move staged files to dataFolder
            File destConfig = new File(dataFolder, ConfigDefaults.FILE_CONFIG);
            File destMessages = new File(dataFolder, ConfigDefaults.FILE_MESSAGES);
            File destEvents = new File(dataFolder, ConfigDefaults.FILE_EVENTS);
            File destAdvanced = new File(dataFolder, ConfigDefaults.FILE_ADVANCED);

            safeDeploy(stagingConfig, destConfig);
            safeDeploy(stagingMessages, destMessages);
            safeDeploy(stagingEvents, destEvents);
            safeDeploy(stagingAdvanced, destAdvanced);

            if (infoLogger != null) {
                infoLogger.accept("Migrated " + migratedCount.get() + " configuration keys to modular files. ("
                        + unrecognizedCount.get() + " unrecognized keys preserved in backup: " + backupFile.getName() + ")");
                infoLogger.accept("Generated modular configuration files: config.yml, messages.yml, events.yml, advanced.yml");
            }

            return new MigrationResult(true, migratedCount.get(), unrecognizedCount.get(), backupFile.getAbsolutePath(), warnings);
        } catch (Exception e) {
            if (warnLogger != null) {
                warnLogger.accept("Configuration migration failed: " + e.getMessage());
            }
            return new MigrationResult(false, 0, unrecognizedCount.get(), backupFile.getAbsolutePath(), List.of("Migration failed: " + e.getMessage()));
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    private static void migrateKey(
            ConfigurationSection source,
            String sourcePath,
            ConfigurationSection target,
            String targetPath,
            AtomicInteger count) {
        if (source != null && source.contains(sourcePath)) {
            Object val = source.get(sourcePath);
            target.set(targetPath, val);
            count.incrementAndGet();
        }
    }

    private static void safeDeploy(File source, File destination) throws IOException {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
