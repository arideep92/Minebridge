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

class ConfigLoaderTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("Loads existing file or generates default template from resource if missing")
    void loadsOrCreatesDefaultFiles() {
        File configFile = new File(tempDir, "config.yml");
        File messagesFile = new File(tempDir, "messages.yml");
        File eventsFile = new File(tempDir, "events.yml");
        File advancedFile = new File(tempDir, "advanced.yml");

        assertFalse(configFile.exists());
        assertFalse(messagesFile.exists());
        assertFalse(eventsFile.exists());
        assertFalse(advancedFile.exists());

        List<String> errors = new ArrayList<>();
        YamlConfiguration config = ConfigLoader.loadOrCreate(configFile, ConfigDefaults.RESOURCE_CONFIG, errors);
        YamlConfiguration messages = ConfigLoader.loadOrCreate(messagesFile, ConfigDefaults.RESOURCE_MESSAGES, errors);
        YamlConfiguration events = ConfigLoader.loadOrCreate(eventsFile, ConfigDefaults.RESOURCE_EVENTS, errors);
        YamlConfiguration advanced = ConfigLoader.loadOrCreate(advancedFile, ConfigDefaults.RESOURCE_ADVANCED, errors);

        assertTrue(errors.isEmpty(), "Errors: " + errors);
        assertTrue(configFile.exists());
        assertTrue(messagesFile.exists());
        assertTrue(eventsFile.exists());
        assertTrue(advancedFile.exists());

        assertEquals("BOT", config.getString("discord.mode"));
        assertEquals("Minecraft Server", config.getString("server.name"));
        assertTrue(messages.contains("chat.minecraft-to-discord"));
        assertTrue(events.contains("player-events.enabled"));
        assertTrue(advanced.contains("migration.version"));
    }

    @Test
    @DisplayName("Resolves environment variables with default values when env var is unset")
    void resolvesEnvVarsWithDefaults() {
        List<String> missing = new ArrayList<>();
        String input = "${UNSET_TEST_BOT_TOKEN:synthetic-default-token}";
        String resolved = ConfigLoader.resolveEnvVars(input, missing);

        assertEquals("synthetic-default-token", resolved);
        assertTrue(missing.isEmpty());
    }

    @Test
    @DisplayName("Reports missing environment variable when unset and no default provided")
    void reportsMissingRequiredEnvVar() {
        List<String> missing = new ArrayList<>();
        String input = "${UNSET_MANDATORY_SECRET_KEY_XYZ}";
        String resolved = ConfigLoader.resolveEnvVars(input, missing);

        assertEquals("", resolved);
        assertEquals(1, missing.size());
        assertTrue(missing.getFirst().contains("UNSET_MANDATORY_SECRET_KEY_XYZ"));
    }

    @Test
    @DisplayName("Handles multiple env vars and mixed literal text")
    void resolvesMultipleEnvVars() {
        List<String> missing = new ArrayList<>();
        String input = "Prefix-${UNSET_VAR_1:default1}-Middle-${UNSET_VAR_2:default2}-Suffix";
        String resolved = ConfigLoader.resolveEnvVars(input, missing);

        assertEquals("Prefix-default1-Middle-default2-Suffix", resolved);
        assertTrue(missing.isEmpty());
    }

    @Test
    @DisplayName("Handles empty default value syntax ${VAR:}")
    void handlesEmptyDefaultSyntax() {
        List<String> missing = new ArrayList<>();
        String input = "${UNSET_EMPTY_DEFAULT:}";
        String resolved = ConfigLoader.resolveEnvVars(input, missing);

        assertEquals("", resolved);
        assertTrue(missing.isEmpty());
    }

    @Test
    @DisplayName("Preserves original string on disk without writing resolved env vars")
    void doesNotWriteSecretsBackToDisk() throws Exception {
        File configFile = new File(tempDir, "config.yml");
        String rawContent = "discord:\n  bot-token: \"${MINEBRIDGE_TEST_TOKEN:synthetic-in-memory-token}\"\n";
        Files.writeString(configFile.toPath(), rawContent);

        List<String> errors = new ArrayList<>();
        YamlConfiguration loaded = ConfigLoader.loadOrCreate(configFile, ConfigDefaults.RESOURCE_CONFIG, errors);
        String resolvedToken = ConfigLoader.readString(loaded, "discord.bot-token", "", errors);

        assertEquals("synthetic-in-memory-token", resolvedToken);

        // Verify disk content still contains ${MINEBRIDGE_TEST_TOKEN:synthetic-in-memory-token}
        String diskContent = Files.readString(configFile.toPath());
        assertTrue(diskContent.contains("${MINEBRIDGE_TEST_TOKEN:synthetic-in-memory-token}"));
        assertFalse(diskContent.contains("synthetic-in-memory-token\n"));
    }

    @Test
    @DisplayName("Reads boolean and string with error logging on type mismatch")
    void readsValuesWithValidation() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("valid.bool", true);
        config.set("invalid.bool", "not-a-bool");
        config.set("valid.string", "hello world");
        config.set("invalid.string", 12345);

        List<String> errors = new ArrayList<>();
        boolean bool1 = ConfigLoader.readBoolean(config, "valid.bool", false, errors);
        assertTrue(bool1);
        assertTrue(errors.isEmpty());

        boolean bool2 = ConfigLoader.readBoolean(config, "invalid.bool", false, errors);
        assertFalse(bool2);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("invalid.bool must be true or false"));

        errors.clear();
        String str1 = ConfigLoader.readString(config, "valid.string", "", errors);
        assertEquals("hello world", str1);
        assertTrue(errors.isEmpty());

        String str2 = ConfigLoader.readString(config, "invalid.string", "fallback", errors);
        assertEquals("fallback", str2);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("invalid.string must be a string"));
    }
}
