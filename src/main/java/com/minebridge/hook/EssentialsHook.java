package com.minebridge.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Optional soft-dependency hook for EssentialsX.
 * Safely resolves player display nicknames without hard compile-time dependencies.
 * Respects fallback to Bukkit display name and real Minecraft username.
 */
public class EssentialsHook {

    private final BooleanSupplier availableChecker;
    private final Function<Player, String> nicknameResolver;
    private final Consumer<String> errorLogger;

    /**
     * Standard runtime constructor for Bukkit.
     * Uses reflection to access EssentialsX if present and enabled.
     */
    public EssentialsHook() {
        this((Consumer<String>) null);
    }

    /**
     * Runtime constructor with an optional error logger.
     */
    public EssentialsHook(Consumer<String> errorLogger) {
        ReflectionEssentialsResolver resolver = new ReflectionEssentialsResolver(errorLogger);
        this.availableChecker = resolver::isAvailable;
        this.nicknameResolver = resolver::getNickname;
        this.errorLogger = errorLogger;
    }

    /**
     * Constructor allowing isolated adapters/mocks for unit tests.
     */
    public EssentialsHook(BooleanSupplier availableChecker, Function<Player, String> nicknameResolver) {
        this(availableChecker, nicknameResolver, null);
    }

    public EssentialsHook(BooleanSupplier availableChecker, Function<Player, String> nicknameResolver, Consumer<String> errorLogger) {
        this.availableChecker = availableChecker != null ? availableChecker : () -> false;
        this.nicknameResolver = nicknameResolver != null ? nicknameResolver : (p -> p != null ? p.getName() : "");
        this.errorLogger = errorLogger;
    }

    /**
     * Checks whether EssentialsX is installed and enabled.
     */
    public boolean isAvailable() {
        try {
            return availableChecker.getAsBoolean();
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("EssentialsX: failed during isAvailable: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        }
    }

    /**
     * Resolves the player's nickname from EssentialsX, falling back to Bukkit display name or real name.
     *
     * @param player Bukkit player
     * @return the player's nickname or display name, or empty string if player is null
     */
    public String getNickname(Player player) {
        if (player == null) {
            return "";
        }
        try {
            if (isAvailable()) {
                String nick = nicknameResolver.apply(player);
                if (nick != null && !nick.isBlank()) {
                    return nick;
                }
            }
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("EssentialsX: failed during getNickname: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        String displayName = player.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return player.getName() != null ? player.getName() : "";
    }

    /**
     * Reflection-based resolver accessing EssentialsX User instance.
     */
    public static final class ReflectionEssentialsResolver {
        private final Consumer<String> errorLogger;
        private volatile boolean initialized;
        private volatile boolean available;
        private Method getUserMethod;
        private Method getNicknameMethod;

        public ReflectionEssentialsResolver() {
            this(null);
        }

        public ReflectionEssentialsResolver(Consumer<String> errorLogger) {
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
                Plugin essPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
                if (essPlugin == null || !essPlugin.isEnabled()) {
                    // Allow retrying later if load order enables Essentials after MineBridge
                    return;
                }

                ClassLoader loader = essPlugin.getClass().getClassLoader();
                Class<?> essClass = essPlugin.getClass();

                // Find getUser method: getUser(UUID), getUser(Player), or getUser(String)
                for (Method m : essClass.getMethods()) {
                    if ("getUser".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> param = m.getParameterTypes()[0];
                        if (UUID.class.isAssignableFrom(param) || Player.class.isAssignableFrom(param) || String.class.isAssignableFrom(param)) {
                            getUserMethod = m;
                            if (UUID.class.isAssignableFrom(param)) {
                                break; // Prefer UUID
                            }
                        }
                    }
                }

                Class<?> userClass = HookUtil.loadClass(loader, "com.earth2me.essentials.User");
                if (userClass == null) {
                    userClass = HookUtil.loadClass(loader, "net.ess3.api.IUser");
                }

                if (userClass != null) {
                    // Try getNickname, getNick, or getDisplayName on User
                    for (String name : new String[]{"getNickname", "getNick", "getDisplayName"}) {
                        Method m = HookUtil.findMethod(userClass, name);
                        if (m != null && m.getParameterCount() == 0 && String.class.isAssignableFrom(m.getReturnType())) {
                            getNicknameMethod = m;
                            break;
                        }
                    }
                }

                initialized = true;
                available = getUserMethod != null && getNicknameMethod != null;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                initialized = true;
                available = false;
                if (errorLogger != null) {
                    errorLogger.accept("EssentialsX: failed to initialize reflection resolver: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }

        public boolean isAvailable() {
            try {
                if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                    return false;
                }
                Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
                if (ess == null || !ess.isEnabled()) {
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

        public String getNickname(Player player) {
            if (player == null || !isAvailable()) {
                return "";
            }
            try {
                Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
                if (ess == null || getUserMethod == null || getNicknameMethod == null) {
                    return "";
                }
                Object user = invokeUser(ess, player);
                if (user == null) {
                    return "";
                }
                Object nickObj = getNicknameMethod.invoke(user);
                if (nickObj instanceof String nickStr && !nickStr.isBlank()) {
                    // Essentials nicknames often start with '~' prefix
                    if (nickStr.startsWith("~")) {
                        nickStr = nickStr.substring(1);
                    }
                    return nickStr;
                }
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                if (errorLogger != null) {
                    errorLogger.accept("EssentialsX: failed to getNickname for player " + player.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
            return "";
        }

        private Object invokeUser(Plugin ess, Player player) throws Exception {
            Class<?> paramType = getUserMethod.getParameterTypes()[0];
            if (UUID.class.isAssignableFrom(paramType)) {
                return getUserMethod.invoke(ess, player.getUniqueId());
            } else if (Player.class.isAssignableFrom(paramType)) {
                return getUserMethod.invoke(ess, player);
            } else {
                return getUserMethod.invoke(ess, player.getName());
            }
        }
    }
}
