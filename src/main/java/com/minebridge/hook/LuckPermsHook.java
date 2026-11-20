package com.minebridge.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Optional soft-dependency hook for LuckPerms.
 * Safely resolves player cached prefixes without hard compile or runtime dependencies
 * and without performing blocking I/O on the Minecraft server thread.
 */
public class LuckPermsHook {

    private final BooleanSupplier enabledChecker;
    private final Function<UUID, String> prefixResolver;

    /**
     * Standard runtime constructor for Bukkit.
     * Uses reflection to access LuckPerms 5.x if present.
     */
    public LuckPermsHook() {
        this(LuckPermsHook::isLuckPermsPluginEnabled, new ReflectionPrefixResolver());
    }

    /**
     * Constructor allowing isolated adapters/mocks for unit tests.
     *
     * @param enabledChecker supplier indicating whether LuckPerms is installed and active
     * @param prefixResolver function resolving a player's prefix by UUID
     */
    public LuckPermsHook(BooleanSupplier enabledChecker, Function<UUID, String> prefixResolver) {
        this.enabledChecker = enabledChecker != null ? enabledChecker : () -> false;
        this.prefixResolver = prefixResolver != null ? prefixResolver : uuid -> "";
    }

    /**
     * Checks whether LuckPerms is currently installed and enabled.
     *
     * @return true if LuckPerms is available, false otherwise
     */
    public boolean isAvailable() {
        try {
            return enabledChecker.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Safely resolves a player's cached prefix.
     *
     * @param player Bukkit player
     * @return the player's prefix, or an empty string if unavailable
     */
    public String getPrefix(Player player) {
        if (player == null) {
            return "";
        }
        return getPrefix(player.getUniqueId());
    }

    /**
     * Safely resolves a player's cached prefix by UUID.
     *
     * @param playerUuid player UUID
     * @return the player's prefix, or an empty string if unavailable
     */
    public String getPrefix(UUID playerUuid) {
        if (playerUuid == null || !isAvailable()) {
            return "";
        }
        try {
            String prefix = prefixResolver.apply(playerUuid);
            return prefix != null ? prefix : "";
        } catch (Throwable ignored) {
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
            return false;
        }
    }

    /**
     * Reflection-based prefix resolver for LuckPerms 5.x.
     * Strictly accesses in-memory cached metadata without database or network I/O.
     */
    public static final class ReflectionPrefixResolver implements Function<UUID, String> {
        private volatile boolean initialized;
        private volatile boolean available;

        private Method getApiMethod;
        private Method getUserManagerMethod;
        private Method getUserMethod;
        private Method getCachedDataMethod;
        private Method getMetaDataMethod;
        private Method getPrefixMethod;

        public ReflectionPrefixResolver() {
        }

        private synchronized void init() {
            if (initialized) {
                return;
            }
            initialized = true;
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                getApiMethod = providerClass.getMethod("get");
                Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
                getUserManagerMethod = luckPermsClass.getMethod("getUserManager");
                Class<?> userManagerClass = Class.forName("net.luckperms.api.model.user.UserManager");
                getUserMethod = userManagerClass.getMethod("getUser", UUID.class);
                Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
                getCachedDataMethod = userClass.getMethod("getCachedData");
                Class<?> cachedDataManagerClass = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
                getMetaDataMethod = cachedDataManagerClass.getMethod("getMetaData");
                Class<?> cachedMetaDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
                getPrefixMethod = cachedMetaDataClass.getMethod("getPrefix");
                available = true;
            } catch (Throwable ignored) {
                available = false;
            }
        }

        @Override
        public String apply(UUID uuid) {
            if (!initialized) {
                init();
            }
            if (!available || uuid == null) {
                return "";
            }
            try {
                Object api = getApiMethod.invoke(null);
                if (api == null) {
                    return "";
                }
                Object userManager = getUserManagerMethod.invoke(api);
                if (userManager == null) {
                    return "";
                }
                // Strictly non-blocking cached user lookup; returns null if not loaded in memory
                Object user = getUserMethod.invoke(userManager, uuid);
                if (user == null) {
                    return "";
                }
                Object cachedData = getCachedDataMethod.invoke(user);
                if (cachedData == null) {
                    return "";
                }
                Object metaData = getMetaDataMethod.invoke(cachedData);
                if (metaData == null) {
                    return "";
                }
                Object prefix = getPrefixMethod.invoke(metaData);
                return prefix != null ? (String) prefix : "";
            } catch (Throwable ignored) {
                return "";
            }
        }
    }
}
