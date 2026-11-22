package com.minebridge.hook;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Optional soft-dependency hook for Multiverse-Core.
 * Safely resolves world alias information without hard compile-time dependencies.
 * Falls back to Bukkit World name when Multiverse-Core is unavailable or unconfigured.
 */
public class MultiverseHook {

    private final BooleanSupplier availableChecker;
    private final Function<World, String> aliasResolver;
    private final Consumer<String> errorLogger;

    /**
     * Standard runtime constructor for Bukkit.
     * Uses reflection to access Multiverse-Core if present and enabled.
     */
    public MultiverseHook() {
        this((Consumer<String>) null);
    }

    /**
     * Runtime constructor with an optional error logger.
     */
    public MultiverseHook(Consumer<String> errorLogger) {
        ReflectionMultiverseResolver resolver = new ReflectionMultiverseResolver(errorLogger);
        this.availableChecker = resolver::isAvailable;
        this.aliasResolver = resolver::getWorldAlias;
        this.errorLogger = errorLogger;
    }

    /**
     * Constructor allowing isolated adapters/mocks for unit tests.
     */
    public MultiverseHook(BooleanSupplier availableChecker, Function<World, String> aliasResolver) {
        this(availableChecker, aliasResolver, null);
    }

    public MultiverseHook(BooleanSupplier availableChecker, Function<World, String> aliasResolver, Consumer<String> errorLogger) {
        this.availableChecker = availableChecker != null ? availableChecker : () -> false;
        this.aliasResolver = aliasResolver != null ? aliasResolver : (w -> w != null ? w.getName() : "");
        this.errorLogger = errorLogger;
    }

    /**
     * Checks whether Multiverse-Core is installed and enabled.
     */
    public boolean isAvailable() {
        try {
            return availableChecker.getAsBoolean();
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Multiverse-Core: failed during isAvailable: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        }
    }

    /**
     * Resolves the friendly alias of the world, or falls back to Bukkit World name.
     *
     * @param world Bukkit World
     * @return world alias or world name, or empty string if world is null
     */
    public String getWorldAlias(World world) {
        if (world == null) {
            return "";
        }
        try {
            if (isAvailable()) {
                String alias = aliasResolver.apply(world);
                if (alias != null && !alias.isBlank()) {
                    return alias;
                }
            }
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Multiverse-Core: failed during getWorldAlias: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        return world.getName() != null ? world.getName() : "";
    }

    /**
     * Reflection-based resolver accessing Multiverse-Core MVWorldManager.
     */
    public static final class ReflectionMultiverseResolver {
        private final Consumer<String> errorLogger;
        private volatile boolean initialized;
        private volatile boolean available;
        private Method getMVWorldManagerMethod;
        private Method getMVWorldMethod;
        private Method getAliasMethod;

        public ReflectionMultiverseResolver() {
            this(null);
        }

        public ReflectionMultiverseResolver(Consumer<String> errorLogger) {
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
                Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                if (mvPlugin == null || !mvPlugin.isEnabled()) {
                    // Allow retrying later if load order enables Multiverse-Core after MineBridge
                    return;
                }

                ClassLoader loader = mvPlugin.getClass().getClassLoader();
                Class<?> mvClass = mvPlugin.getClass();
                getMVWorldManagerMethod = HookUtil.findMethod(mvClass, "getMVWorldManager");

                Class<?> worldManagerClass = HookUtil.loadClass(loader, "com.onarandombox.MultiverseCore.api.MVWorldManager");
                if (worldManagerClass != null) {
                    getMVWorldMethod = HookUtil.findMethod(worldManagerClass, "getMVWorld", World.class);
                    if (getMVWorldMethod == null) {
                        getMVWorldMethod = HookUtil.findMethod(worldManagerClass, "getMVWorld", String.class);
                    }
                }

                Class<?> mvWorldClass = HookUtil.loadClass(loader, "com.onarandombox.MultiverseCore.api.MultiverseWorld");
                if (mvWorldClass != null) {
                    getAliasMethod = HookUtil.findMethod(mvWorldClass, "getAlias");
                }

                initialized = true;
                available = getMVWorldManagerMethod != null && getMVWorldMethod != null && getAliasMethod != null;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                initialized = true;
                available = false;
                if (errorLogger != null) {
                    errorLogger.accept("Multiverse-Core: failed to initialize reflection resolver: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }

        public boolean isAvailable() {
            try {
                if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                    return false;
                }
                Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                if (mv == null || !mv.isEnabled()) {
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

        public String getWorldAlias(World world) {
            if (world == null || !isAvailable()) {
                return "";
            }
            try {
                Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                if (mv == null || getMVWorldManagerMethod == null || getMVWorldMethod == null || getAliasMethod == null) {
                    return "";
                }
                Object wm = getMVWorldManagerMethod.invoke(mv);
                if (wm == null) {
                    return "";
                }
                Object mvWorld;
                if (World.class.isAssignableFrom(getMVWorldMethod.getParameterTypes()[0])) {
                    mvWorld = getMVWorldMethod.invoke(wm, world);
                } else {
                    mvWorld = getMVWorldMethod.invoke(wm, world.getName());
                }
                if (mvWorld == null) {
                    return "";
                }
                Object aliasObj = getAliasMethod.invoke(mvWorld);
                if (aliasObj instanceof String aliasStr && !aliasStr.isBlank()) {
                    return aliasStr;
                }
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("Multiverse-Core: failed to getWorldAlias for world " + world.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
            return "";
        }
    }
}
