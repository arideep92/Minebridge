package com.minebridge.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Optional soft-dependency hook for PlaceholderAPI.
 * Safely parses %...% placeholders on trusted server templates.
 * Never processes player-generated chat message content.
 */
public class PlaceholderApiHook {

    private final BooleanSupplier availableChecker;
    private final BiFunction<Player, String, String> placeholderReplacer;
    private final Consumer<String> errorLogger;

    /**
     * Standard runtime constructor for Bukkit.
     * Uses reflection to access PlaceholderAPI if present and enabled.
     */
    public PlaceholderApiHook() {
        this((Consumer<String>) null);
    }

    /**
     * Runtime constructor with an optional error logger.
     */
    public PlaceholderApiHook(Consumer<String> errorLogger) {
        ReflectionPlaceholderApiResolver resolver = new ReflectionPlaceholderApiResolver(errorLogger);
        this.availableChecker = resolver::isAvailable;
        this.placeholderReplacer = resolver::setPlaceholders;
        this.errorLogger = errorLogger;
    }

    /**
     * Constructor allowing isolated adapters/mocks for unit tests.
     */
    public PlaceholderApiHook(BooleanSupplier availableChecker, BiFunction<Player, String, String> placeholderReplacer) {
        this(availableChecker, placeholderReplacer, null);
    }

    public PlaceholderApiHook(BooleanSupplier availableChecker, BiFunction<Player, String, String> placeholderReplacer, Consumer<String> errorLogger) {
        this.availableChecker = availableChecker != null ? availableChecker : () -> false;
        this.placeholderReplacer = placeholderReplacer != null ? placeholderReplacer : ((p, t) -> t);
        this.errorLogger = errorLogger;
    }

    /**
     * Checks whether PlaceholderAPI is installed and enabled.
     */
    public boolean isAvailable() {
        try {
            return availableChecker.getAsBoolean();
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("PlaceholderAPI: failed during isAvailable: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        }
    }

    /**
     * Replaces placeholders in the trusted template using PlaceholderAPI.
     * Skips processing immediately if template does not contain '%' or if PlaceholderAPI is unavailable.
     *
     * @param player Bukkit player
     * @param template configured template
     * @return template with placeholders expanded, or original template if unavailable
     */
    public String applyPlaceholders(Player player, String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (template.indexOf('%') < 0 || player == null || !isAvailable()) {
            return template;
        }
        try {
            String expanded = placeholderReplacer.apply(player, template);
            return expanded != null ? expanded : template;
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("PlaceholderAPI: failed during applyPlaceholders: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return template;
        }
    }

    /**
     * Reflection-based resolver accessing PlaceholderAPI.setPlaceholders(Player, String).
     */
    public static final class ReflectionPlaceholderApiResolver {
        private final Consumer<String> errorLogger;
        private volatile boolean initialized;
        private volatile boolean available;
        private Method setPlaceholdersMethod;

        public ReflectionPlaceholderApiResolver() {
            this(null);
        }

        public ReflectionPlaceholderApiResolver(Consumer<String> errorLogger) {
            this.errorLogger = errorLogger;
        }

        private synchronized void init() {
            if (initialized) {
                return;
            }
            try {
                if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                    return;
                }
                Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
                if (papi == null || !papi.isEnabled()) {
                    // Allow retrying later if load order enables PlaceholderAPI after MineBridge
                    return;
                }

                Class<?> papiClass = HookUtil.loadClass(papi.getClass().getClassLoader(), "me.clip.placeholderapi.PlaceholderAPI");
                if (papiClass != null) {
                    setPlaceholdersMethod = HookUtil.findMethod(papiClass, "setPlaceholders", Player.class, String.class);
                }

                initialized = true;
                available = setPlaceholdersMethod != null;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                initialized = true;
                available = false;
                if (errorLogger != null) {
                    errorLogger.accept("PlaceholderAPI: failed to initialize reflection resolver: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }

        public boolean isAvailable() {
            try {
                if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                    return false;
                }
                Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
                if (papi == null || !papi.isEnabled()) {
                    return false;
                }
                if (!initialized) {
                    init();
                }
                return available;
            } catch (Throwable ignored) {
                HookUtil.checkFatal(ignored);
                return false;
            }
        }

        public String setPlaceholders(Player player, String template) {
            if (player == null || template == null || !isAvailable() || setPlaceholdersMethod == null) {
                return template;
            }
            try {
                Object result = setPlaceholdersMethod.invoke(null, player, template);
                return result instanceof String str ? str : template;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("PlaceholderAPI: failed to setPlaceholders for player " + player.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return template;
            }
        }
    }
}
