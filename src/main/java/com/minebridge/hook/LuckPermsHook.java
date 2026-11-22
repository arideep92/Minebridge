package com.minebridge.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Optional soft-dependency hook for LuckPerms.
 * Safely resolves player cached prefixes and suffixes using LuckPerms API context options
 * without hard compile or runtime dependencies and without performing blocking I/O on the Minecraft server thread.
 */
public class LuckPermsHook {

    private final BooleanSupplier enabledChecker;
    private final BiFunction<Player, UUID, String> prefixResolver;
    private final BiFunction<Player, UUID, String> suffixResolver;
    private final Consumer<String> errorLogger;

    /**
     * Standard runtime constructor for Bukkit.
     * Uses reflection to access LuckPerms 5.x if present.
     */
    public LuckPermsHook() {
        this((Consumer<String>) null);
    }

    /**
     * Runtime constructor with an optional error logger.
     */
    public LuckPermsHook(Consumer<String> errorLogger) {
        ReflectionMetaResolver resolver = new ReflectionMetaResolver(errorLogger);
        this.enabledChecker = LuckPermsHook::isLuckPermsPluginEnabled;
        this.prefixResolver = resolver::resolvePrefix;
        this.suffixResolver = resolver::resolveSuffix;
        this.errorLogger = errorLogger;
    }

    /**
     * Constructor allowing isolated adapters/mocks for unit tests.
     *
     * @param enabledChecker supplier indicating whether LuckPerms is installed and active
     * @param prefixResolver function resolving a player's prefix by UUID
     */
    public LuckPermsHook(BooleanSupplier enabledChecker, Function<UUID, String> prefixResolver) {
        this(enabledChecker, (player, uuid) -> prefixResolver != null ? prefixResolver.apply(uuid) : "", null, null);
    }

    /**
     * Constructor allowing contextual adapters/mocks for unit tests.
     *
     * @param enabledChecker supplier indicating whether LuckPerms is installed and active
     * @param prefixResolver bifunction resolving a player's prefix by Player and UUID
     */
    public LuckPermsHook(BooleanSupplier enabledChecker, BiFunction<Player, UUID, String> prefixResolver) {
        this(enabledChecker, prefixResolver, null, null);
    }

    /**
     * Full constructor for contextual prefix and suffix adapters.
     *
     * @param enabledChecker supplier indicating whether LuckPerms is installed and active
     * @param prefixResolver bifunction resolving a player's prefix by Player and UUID
     * @param suffixResolver bifunction resolving a player's suffix by Player and UUID
     */
    public LuckPermsHook(
            BooleanSupplier enabledChecker,
            BiFunction<Player, UUID, String> prefixResolver,
            BiFunction<Player, UUID, String> suffixResolver) {
        this(enabledChecker, prefixResolver, suffixResolver, null);
    }

    public LuckPermsHook(
            BooleanSupplier enabledChecker,
            BiFunction<Player, UUID, String> prefixResolver,
            BiFunction<Player, UUID, String> suffixResolver,
            Consumer<String> errorLogger) {
        this.enabledChecker = enabledChecker != null ? enabledChecker : () -> false;
        this.prefixResolver = prefixResolver != null ? prefixResolver : (player, uuid) -> "";
        this.suffixResolver = suffixResolver != null ? suffixResolver : (player, uuid) -> "";
        this.errorLogger = errorLogger;
    }

    /**
     * Checks whether LuckPerms is currently installed and enabled.
     *
     * @return true if LuckPerms is available, false otherwise
     */
    public boolean isAvailable() {
        try {
            return enabledChecker.getAsBoolean();
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("LuckPerms: failed during isAvailable: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        }
    }

    /**
     * Safely resolves a player's effective cached prefix according to their current context.
     *
     * @param player Bukkit player
     * @return the player's prefix, or an empty string if unavailable
     */
    public String getPrefix(Player player) {
        if (player == null || !isAvailable()) {
            return "";
        }
        try {
            String prefix = prefixResolver.apply(player, player.getUniqueId());
            return prefix != null ? prefix : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("LuckPerms: failed during getPrefix: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    /**
     * Safely resolves a player's cached prefix by UUID.
     * If the player is online, attempts to use their current context.
     *
     * @param playerUuid player UUID
     * @return the player's prefix, or an empty string if unavailable
     */
    public String getPrefix(UUID playerUuid) {
        if (playerUuid == null || !isAvailable()) {
            return "";
        }
        try {
            Player onlinePlayer = null;
            try {
                if (Bukkit.getServer() != null) {
                    onlinePlayer = Bukkit.getPlayer(playerUuid);
                }
            } catch (Throwable ignored) {
                HookUtil.checkFatal(ignored);
            }
            String prefix = prefixResolver.apply(onlinePlayer, playerUuid);
            return prefix != null ? prefix : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("LuckPerms: failed during getPrefix: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    /**
     * Safely resolves a player's effective cached suffix according to their current context.
     *
     * @param player Bukkit player
     * @return the player's suffix, or an empty string if unavailable
     */
    public String getSuffix(Player player) {
        if (player == null || !isAvailable()) {
            return "";
        }
        try {
            String suffix = suffixResolver.apply(player, player.getUniqueId());
            return suffix != null ? suffix : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("LuckPerms: failed during getSuffix: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    /**
     * Safely resolves a player's cached suffix by UUID.
     * If the player is online, attempts to use their current context.
     *
     * @param playerUuid player UUID
     * @return the player's suffix, or an empty string if unavailable
     */
    public String getSuffix(UUID playerUuid) {
        if (playerUuid == null || !isAvailable()) {
            return "";
        }
        try {
            Player onlinePlayer = null;
            try {
                if (Bukkit.getServer() != null) {
                    onlinePlayer = Bukkit.getPlayer(playerUuid);
                }
            } catch (Throwable ignored) {
                HookUtil.checkFatal(ignored);
            }
            String suffix = suffixResolver.apply(onlinePlayer, playerUuid);
            return suffix != null ? suffix : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("LuckPerms: failed during getSuffix: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    private static boolean isLuckPermsPluginEnabled() {
        try {
            if (Bukkit.getServer() == null) {
                return false;
            }
            Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            return plugin != null && plugin.isEnabled();
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
            return false;
        }
    }

    /**
     * Reflection-based metadata resolver for LuckPerms 5.x.
     * Resolves effective prefixes and suffixes using the player's current QueryOptions context.
     * Strictly accesses in-memory cached metadata without database or network I/O.
     */
    public static final class ReflectionMetaResolver {
        private final Consumer<String> errorLogger;
        private volatile boolean initialized;
        private volatile boolean available;

        private Method getApiMethod;
        private Method getUserManagerMethod;
        private Method getUserMethod;
        private Method getCachedDataMethod;
        private Method getMetaDataMethod;
        private Method getMetaDataWithOptionsMethod;
        private Method getPrefixMethod;
        private Method getSuffixMethod;
        private Method getContextManagerMethod;
        private Method getQueryOptionsPlayerMethod;
        private Method getQueryOptionsUserMethod;

        public ReflectionMetaResolver() {
            this(null);
        }

        public ReflectionMetaResolver(Consumer<String> errorLogger) {
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
                Plugin lpPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
                if (lpPlugin == null || !lpPlugin.isEnabled()) {
                    // Allow retrying later if load order enables LuckPerms after MineBridge
                    return;
                }

                ClassLoader loader = lpPlugin.getClass().getClassLoader();

                Class<?> providerClass = HookUtil.loadClass(loader, "net.luckperms.api.LuckPermsProvider");
                if (providerClass != null) {
                    getApiMethod = HookUtil.findMethod(providerClass, "get");
                }

                Class<?> luckPermsClass = HookUtil.loadClass(loader, "net.luckperms.api.LuckPerms");
                if (luckPermsClass != null) {
                    getUserManagerMethod = HookUtil.findMethod(luckPermsClass, "getUserManager");
                }

                Class<?> userManagerClass = HookUtil.loadClass(loader, "net.luckperms.api.model.user.UserManager");
                if (userManagerClass != null) {
                    getUserMethod = HookUtil.findMethod(userManagerClass, "getUser", UUID.class);
                }

                Class<?> userClass = HookUtil.loadClass(loader, "net.luckperms.api.model.user.User");
                if (userClass != null) {
                    getCachedDataMethod = HookUtil.findMethod(userClass, "getCachedData");
                }

                Class<?> cachedDataManagerClass = HookUtil.loadClass(loader, "net.luckperms.api.cacheddata.CachedDataManager");
                if (cachedDataManagerClass != null) {
                    getMetaDataMethod = HookUtil.findMethod(cachedDataManagerClass, "getMetaData");
                }

                Class<?> cachedMetaDataClass = HookUtil.loadClass(loader, "net.luckperms.api.cacheddata.CachedMetaData");
                if (cachedMetaDataClass != null) {
                    getPrefixMethod = HookUtil.findMethod(cachedMetaDataClass, "getPrefix");
                    getSuffixMethod = HookUtil.findMethod(cachedMetaDataClass, "getSuffix");
                }

                // ContextManager and contextual QueryOptions
                if (luckPermsClass != null && cachedDataManagerClass != null) {
                    try {
                        getContextManagerMethod = HookUtil.findMethod(luckPermsClass, "getContextManager");
                        Class<?> contextManagerClass = HookUtil.loadClass(loader, "net.luckperms.api.context.ContextManager");
                        if (contextManagerClass != null) {
                            for (Method m : contextManagerClass.getMethods()) {
                                if ("getQueryOptions".equals(m.getName()) && m.getParameterCount() == 1) {
                                    Class<?> paramType = m.getParameterTypes()[0];
                                    if (paramType.getName().contains("User")) {
                                        getQueryOptionsUserMethod = m;
                                    } else {
                                        getQueryOptionsPlayerMethod = m;
                                    }
                                }
                            }
                        }
                        Class<?> queryOptionsClass = HookUtil.loadClass(loader, "net.luckperms.api.query.QueryOptions");
                        if (queryOptionsClass != null) {
                            getMetaDataWithOptionsMethod = HookUtil.findMethod(cachedDataManagerClass, "getMetaData", queryOptionsClass);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                initialized = true;
                available = getApiMethod != null && getUserManagerMethod != null && getUserMethod != null;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                initialized = true;
                available = false;
                if (errorLogger != null) {
                    errorLogger.accept("LuckPerms: failed to initialize reflection resolver: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }

        public String resolvePrefix(Player player, UUID uuid) {
            return resolve(player, uuid, true);
        }

        public String resolveSuffix(Player player, UUID uuid) {
            return resolve(player, uuid, false);
        }

        private String resolve(Player player, UUID uuid, boolean prefix) {
            if (!initialized) {
                init();
            }
            if (!available) {
                return "";
            }
            UUID targetUuid = uuid != null ? uuid : (player != null ? player.getUniqueId() : null);
            if (targetUuid == null) {
                return "";
            }
            try {
                Object api = null;
                try {
                    api = getApiMethod.invoke(null);
                } catch (Throwable t) {
                    HookUtil.checkFatal(t);
                    // Happens if LuckPerms API singleton isn't initialized yet
                    return "";
                }
                if (api == null) {
                    return "";
                }
                Object userManager = getUserManagerMethod.invoke(api);
                if (userManager == null) {
                    return "";
                }
                Object user = getUserMethod.invoke(userManager, targetUuid);
                if (user == null) {
                    return "";
                }
                Object cachedData = getCachedDataMethod.invoke(user);
                if (cachedData == null) {
                    return "";
                }

                Object metaData = null;
                if (getContextManagerMethod != null && getMetaDataWithOptionsMethod != null) {
                    try {
                        Object contextManager = getContextManagerMethod.invoke(api);
                        if (contextManager != null) {
                            Object queryOptions = null;
                            if (player != null && getQueryOptionsPlayerMethod != null) {
                                try {
                                    queryOptions = getQueryOptionsPlayerMethod.invoke(contextManager, player);
                                } catch (Throwable ignored) {
                                    HookUtil.checkFatal(ignored);
                                }
                            }
                            if (queryOptions == null && getQueryOptionsUserMethod != null) {
                                try {
                                    queryOptions = getQueryOptionsUserMethod.invoke(contextManager, user);
                                } catch (Throwable ignored) {
                                    HookUtil.checkFatal(ignored);
                                }
                            }
                            if (queryOptions != null) {
                                metaData = getMetaDataWithOptionsMethod.invoke(cachedData, queryOptions);
                            }
                        }
                    } catch (Throwable ignored) {
                        HookUtil.checkFatal(ignored);
                    }
                }

                if (metaData == null && getMetaDataMethod != null) {
                    metaData = getMetaDataMethod.invoke(cachedData);
                }

                if (metaData == null) {
                    return "";
                }
                Method targetMethod = prefix ? getPrefixMethod : getSuffixMethod;
                if (targetMethod == null) {
                    return "";
                }
                Object result = targetMethod.invoke(metaData);
                return result != null ? (String) result : "";
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("LuckPerms: failed to resolve " + (prefix ? "prefix" : "suffix") + " for " + targetUuid + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return "";
            }
        }
    }

    /**
     * Backward-compatible alias for ReflectionMetaResolver.
     */
    public static final class ReflectionPrefixResolver implements BiFunction<Player, UUID, String> {
        private final ReflectionMetaResolver delegate;

        public ReflectionPrefixResolver() {
            this(null);
        }

        public ReflectionPrefixResolver(Consumer<String> errorLogger) {
            this.delegate = new ReflectionMetaResolver(errorLogger);
        }

        public String apply(UUID uuid) {
            return delegate.resolvePrefix(null, uuid);
        }

        @Override
        public String apply(Player player, UUID uuid) {
            return delegate.resolvePrefix(player, uuid);
        }
    }
}
