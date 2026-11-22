package com.minebridge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles loading, environment variable resolution, and default inheritance for YAML files.
 */
public final class ConfigLoader {

    private static final Pattern ENV_VAR_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z0-9_]+)(?::([^}]*))?\\}");

    private ConfigLoader() {
    }

    /**
     * Resolves ${VAR} or ${VAR:default} environment variable references.
     * Never writes resolved secrets back to disk.
     */
    public static String resolveEnvVars(String input, List<String> missingVars) {
        if (input == null || !input.contains("${")) {
            return input != null ? input : "";
        }

        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String envValue = System.getenv(varName);
            if (envValue != null && !envValue.isBlank()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
            } else if (defaultValue != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(defaultValue));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
                if (missingVars != null) {
                    missingVars.add("Required environment variable '${" + varName + "}' is not set or empty.");
                }
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Loads a YAML file. If the file does not exist, copies the bundled resource template first.
     * Merges bundled defaults for missing keys.
     */
    public static YamlConfiguration loadOrCreate(
            File file,
            String resourcePath,
            List<String> errors) {
        if (file == null) {
            if (errors != null) {
                errors.add("Target file reference is null.");
            }
            return new YamlConfiguration();
        }

        if (!file.exists()) {
            try {
                if (file.getParentFile() != null && !file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                InputStream resourceStream = ConfigDefaults.class.getResourceAsStream(resourcePath);
                if (resourceStream != null) {
                    try (InputStream in = resourceStream) {
                        Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    file.createNewFile();
                }
            } catch (IOException exception) {
                if (errors != null) {
                    errors.add("Could not create default file " + file.getName() + ": " + exception.getMessage());
                }
            }
        }

        YamlConfiguration config = new YamlConfiguration();
        YamlConfiguration defaults = ConfigDefaults.loadResourceYaml(resourcePath);
        if (!defaults.getKeys(true).isEmpty()) {
            config.setDefaults(defaults);
        }

        if (file.exists()) {
            try (InputStream in = new FileInputStream(file);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                config.load(reader);
            } catch (IOException | InvalidConfigurationException exception) {
                if (errors != null) {
                    errors.add("Failed to read " + file.getName() + " (" + exception.getClass().getSimpleName()
                            + "): " + exception.getMessage());
                }
            }
        }

        return config;
    }

    /**
     * Reads a string from configuration, resolving environment variables and applying default.
     */
    public static String readString(
            ConfigurationSection section,
            String path,
            String defaultValue,
            List<String> errors) {
        if (section == null) {
            return defaultValue != null ? defaultValue : "";
        }
        Object raw = section.get(path);
        if (raw == null) {
            return defaultValue != null ? defaultValue : "";
        }
        if (!(raw instanceof String rawString)) {
            if (errors != null) {
                errors.add(path + " must be a string.");
            }
            return defaultValue != null ? defaultValue : "";
        }
        return resolveEnvVars(rawString, errors);
    }

    /**
     * Reads a boolean value from configuration.
     */
    public static boolean readBoolean(
            ConfigurationSection section,
            String path,
            boolean defaultValue,
            List<String> errors) {
        if (section == null) {
            return defaultValue;
        }
        Object raw = section.get(path);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean boolValue) {
            return boolValue;
        }
        if (errors != null) {
            errors.add(path + " must be true or false.");
        }
        return defaultValue;
    }
}
