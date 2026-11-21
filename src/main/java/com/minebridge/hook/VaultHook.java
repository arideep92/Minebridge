package com.minebridge.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Optional soft-dependency hook for Vault (Chat and Permission APIs).
 * Provides a compatibility fallback for alternative permission and chat providers.
 * Accesses Vault services independently via Bukkit ServicesManager using reflection
 * to avoid hard compile-time dependencies.
 */
public class VaultHook {

    private final BooleanSupplier availableChecker;
    private final BiFunction<Player, String, String> prefixResolver;
    private final BiFunction<Player, String, String> suffixResolver;
    private final Function<Player, String> primaryGroupResolver;
    private final Consumer<String> errorLogger;

    /**
     * Standard runtime constructor for Bukkit.
     * Uses reflection and Bukkit ServicesManager.
     */
    public VaultHook() {
        this((Consumer<String>) null);
    }

    /**
     * Runtime constructor with an optional error logger.
     */
    public VaultHook(Consumer<String> errorLogger) {
        ReflectionVaultResolver resolver = new ReflectionVaultResolver(errorLogger);
        this.availableChecker = resolver::isAvailable;
        this.prefixResolver = resolver::getPrefix;
        this.suffixResolver = resolver::getSuffix;
        this.primaryGroupResolver = resolver::getPrimaryGroup;
        this.errorLogger = errorLogger;
    }

    /**
     * Constructor allowing isolated adapters/mocks for unit tests.
     */
    public VaultHook(
            BooleanSupplier availableChecker,
            BiFunction<Player, String, String> prefixResolver,
            BiFunction<Player, String, String> suffixResolver,
            Function<Player, String> primaryGroupResolver) {
        this(availableChecker, prefixResolver, suffixResolver, primaryGroupResolver, null);
    }

    public VaultHook(
            BooleanSupplier availableChecker,
            BiFunction<Player, String, String> prefixResolver,
            BiFunction<Player, String, String> suffixResolver,
            Function<Player, String> primaryGroupResolver,
            Consumer<String> errorLogger) {
        this.availableChecker = availableChecker != null ? availableChecker : () -> false;
        this.prefixResolver = prefixResolver != null ? prefixResolver : (player, world) -> "";
        this.suffixResolver = suffixResolver != null ? suffixResolver : (player, world) -> "";
        this.primaryGroupResolver = primaryGroupResolver != null ? primaryGroupResolver : player -> "";
        this.errorLogger = errorLogger;
    }

    /**
     * Checks whether Vault is installed and at least one Vault service (Chat or Permission) is registered.
     */
    public boolean isAvailable() {
        try {
            return availableChecker.getAsBoolean();
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Vault: failed during isAvailable: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        }
    }

    /**
     * Resolves the player's prefix from Vault Chat service.
     *
     * @param player Bukkit player
     * @return the player's prefix, or empty string if unavailable
     */
    public String getPrefix(Player player) {
        if (player == null || !isAvailable()) {
            return "";
        }
        try {
            String world = player.getWorld() != null ? player.getWorld().getName() : "";
            String prefix = prefixResolver.apply(player, world);
            return prefix != null ? prefix : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Vault: failed during getPrefix: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    /**
     * Resolves the player's suffix from Vault Chat service.
     *
     * @param player Bukkit player
     * @return the player's suffix, or empty string if unavailable
     */
    public String getSuffix(Player player) {
        if (player == null || !isAvailable()) {
            return "";
        }
        try {
            String world = player.getWorld() != null ? player.getWorld().getName() : "";
            String suffix = suffixResolver.apply(player, world);
            return suffix != null ? suffix : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Vault: failed during getSuffix: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    /**
     * Resolves the player's primary permission group from Vault Permission service.
     *
     * @param player Bukkit player
     * @return primary group name, or empty string if unavailable
     */
    public String getPrimaryGroup(Player player) {
        if (player == null || !isAvailable()) {
            return "";
        }
        try {
            String group = primaryGroupResolver.apply(player);
            return group != null ? group : "";
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Vault: failed during getPrimaryGroup: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return "";
        }
    }

    /**
     * Reflection-based resolver accessing Vault services dynamically via Bukkit ServicesManager.
     */
    public static final class ReflectionVaultResolver {
        private final Consumer<String> errorLogger;
        private volatile boolean initialized;
        private Class<?> chatClass;
        private Class<?> permissionClass;
        private Method getPlayerPrefixMethod;
        private Method getPlayerSuffixMethod;
        private Method getPrimaryGroupMethod;

        public ReflectionVaultResolver() {
            this(null);
        }

        public ReflectionVaultResolver(Consumer<String> errorLogger) {
            this.errorLogger = errorLogger;
        }

        private synchronized void init() {
            if (initialized) {
                return;
            }
            try {
                if (Bukkit.getServer() == null || Bukkit.getServicesManager() == null) {
                    return;
                }

                // If plugin manager is available, verify Vault is loaded and enabled (protects against load ordering issues)
                if (Bukkit.getPluginManager() != null) {
                    Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
                    if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
                        // Allow retrying later when Vault is enabled
                        return;
                    }
                }

                ClassLoader classLoader = VaultHook.class.getClassLoader();
                if (Bukkit.getPluginManager() != null) {
                    Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
                    if (vaultPlugin != null) {
                        classLoader = vaultPlugin.getClass().getClassLoader();
                    }
                }

                try {
                    chatClass = Class.forName("net.milkbowl.vault.chat.Chat", true, classLoader);
                } catch (ClassNotFoundException e) {
                    try {
                        chatClass = Class.forName("net.milkbowl.vault.chat.Chat");
                    } catch (ClassNotFoundException ignored) {
                    }
                }

                if (chatClass != null) {
                    getPlayerPrefixMethod = findCompatibleMethod(chatClass, "getPlayerPrefix");
                    getPlayerSuffixMethod = findCompatibleMethod(chatClass, "getPlayerSuffix");
                }

                try {
                    permissionClass = Class.forName("net.milkbowl.vault.permission.Permission", true, classLoader);
                } catch (ClassNotFoundException e) {
                    try {
                        permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
                    } catch (ClassNotFoundException ignored) {
                    }
                }

                if (permissionClass != null) {
                    getPrimaryGroupMethod = findCompatibleGroupMethod(permissionClass, "getPrimaryGroup");
                }

                initialized = true;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                initialized = true;
                if (errorLogger != null) {
                    errorLogger.accept("Vault: failed to initialize reflection resolver: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }

        private static Method findCompatibleMethod(Class<?> clazz, String methodName) {
            Method fallback = null;
            for (Method m : clazz.getMethods()) {
                if (methodName.equals(m.getName())) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && Player.class.isAssignableFrom(p[0])) {
                        return m; // Best match: getPlayerPrefix(Player)
                    }
                    if (p.length == 2 && String.class.isAssignableFrom(p[0]) && Player.class.isAssignableFrom(p[1])) {
                        fallback = m; // getPlayerPrefix(String world, Player player)
                    }
                    if (p.length == 2 && String.class.isAssignableFrom(p[0]) && String.class.isAssignableFrom(p[1])) {
                        if (fallback == null) fallback = m; // getPlayerPrefix(String world, String player)
                    }
                    if (p.length == 1 && String.class.isAssignableFrom(p[0])) {
                        if (fallback == null) fallback = m; // getPlayerPrefix(String player)
                    }
                }
            }
            return fallback;
        }

        private static Method findCompatibleGroupMethod(Class<?> clazz, String methodName) {
            Method fallback = null;
            for (Method m : clazz.getMethods()) {
                if (methodName.equals(m.getName())) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && Player.class.isAssignableFrom(p[0])) {
                        return m;
                    }
                    if (p.length == 2 && String.class.isAssignableFrom(p[0]) && Player.class.isAssignableFrom(p[1])) {
                        fallback = m;
                    }
                    if (p.length == 2 && String.class.isAssignableFrom(p[0]) && String.class.isAssignableFrom(p[1])) {
                        if (fallback == null) fallback = m;
                    }
                    if (p.length == 1 && String.class.isAssignableFrom(p[0])) {
                        if (fallback == null) fallback = m;
                    }
                }
            }
            return fallback;
        }

        public boolean isAvailable() {
            if (!initialized) {
                init();
            }
            return getChatProvider() != null || getPermissionProvider() != null;
        }

        private Object getChatProvider() {
            if (!initialized) {
                init();
            }
            if (chatClass == null || Bukkit.getServer() == null || Bukkit.getServicesManager() == null) {
                return null;
            }
            try {
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(chatClass);
                return rsp != null ? rsp.getProvider() : null;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("Vault: failed to get Chat provider: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return null;
            }
        }

        private Object getPermissionProvider() {
            if (!initialized) {
                init();
            }
            if (permissionClass == null || Bukkit.getServer() == null || Bukkit.getServicesManager() == null) {
                return null;
            }
            try {
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(permissionClass);
                return rsp != null ? rsp.getProvider() : null;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("Vault: failed to get Permission provider: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return null;
            }
        }

        public String getPrefix(Player player, String world) {
            Object chat = getChatProvider();
            if (chat == null || player == null || getPlayerPrefixMethod == null) {
                return "";
            }
            try {
                Object prefix = invokeMethod(getPlayerPrefixMethod, chat, player, world);
                return prefix != null ? (String) prefix : "";
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("Vault: failed to getPrefix for player " + player.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return "";
            }
        }

        public String getSuffix(Player player, String world) {
            Object chat = getChatProvider();
            if (chat == null || player == null || getPlayerSuffixMethod == null) {
                return "";
            }
            try {
                Object suffix = invokeMethod(getPlayerSuffixMethod, chat, player, world);
                return suffix != null ? (String) suffix : "";
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("Vault: failed to getSuffix for player " + player.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return "";
            }
        }

        public String getPrimaryGroup(Player player) {
            Object permission = getPermissionProvider();
            if (permission == null || player == null || getPrimaryGroupMethod == null) {
                return "";
            }
            try {
                String world = player.getWorld() != null ? player.getWorld().getName() : "";
                Object group = invokeMethod(getPrimaryGroupMethod, permission, player, world);
                return group != null ? (String) group : "";
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("Vault: failed to getPrimaryGroup for player " + player.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return "";
            }
        }

        private static Object invokeMethod(Method method, Object target, Player player, String world) throws Exception {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1) {
                if (Player.class.isAssignableFrom(params[0])) {
                    return method.invoke(target, player);
                }
                if (String.class.isAssignableFrom(params[0])) {
                    return method.invoke(target, player.getName());
                }
            } else if (params.length == 2) {
                if (String.class.isAssignableFrom(params[0])) {
                    if (Player.class.isAssignableFrom(params[1])) {
                        return method.invoke(target, world, player);
                    }
                    if (String.class.isAssignableFrom(params[1])) {
                        return method.invoke(target, world, player.getName());
                    }
                }
            }
            return null;
        }
    }
}
