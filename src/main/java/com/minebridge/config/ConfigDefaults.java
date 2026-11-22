package com.minebridge.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralized defaults and templates for the four MineBridge configuration files:
 * config.yml, messages.yml, events.yml, and advanced.yml.
 */
public final class ConfigDefaults {

    public static final String FILE_CONFIG = "config.yml";
    public static final String FILE_MESSAGES = "messages.yml";
    public static final String FILE_EVENTS = "events.yml";
    public static final String FILE_ADVANCED = "advanced.yml";

    public static final String RESOURCE_CONFIG = "/config.yml";
    public static final String RESOURCE_MESSAGES = "/messages.yml";
    public static final String RESOURCE_EVENTS = "/events.yml";
    public static final String RESOURCE_ADVANCED = "/advanced.yml";
    public static final String RESOURCE_LEGACY_CONFIG = "/legacy-config.yml";

    public static final int CURRENT_MIGRATION_VERSION = 1;

    private ConfigDefaults() {
    }

    public static YamlConfiguration loadResourceYaml(String resourcePath) {
        InputStream stream = ConfigDefaults.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            return new YamlConfiguration();
        }
        try (InputStream in = stream;
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            return new YamlConfiguration();
        }
    }

    public static YamlConfiguration defaultConfig() {
        return loadResourceYaml(RESOURCE_CONFIG);
    }

    public static YamlConfiguration defaultMessages() {
        return loadResourceYaml(RESOURCE_MESSAGES);
    }

    public static YamlConfiguration defaultEvents() {
        return loadResourceYaml(RESOURCE_EVENTS);
    }

    public static YamlConfiguration defaultAdvanced() {
        return loadResourceYaml(RESOURCE_ADVANCED);
    }

    public static YamlConfiguration defaultLegacyConfig() {
        YamlConfiguration legacy = loadResourceYaml(RESOURCE_LEGACY_CONFIG);
        if (legacy.getKeys(false).isEmpty()) {
            return loadResourceYaml(RESOURCE_CONFIG);
        }
        return legacy;
    }
}
