package com.minebridge.hook;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unified metadata and chat provider orchestration facade.
 * Manages optional integrations (LuckPerms, Vault, EssentialsX, Multiverse-Core, PlaceholderAPI)
 * with strict provider precedence:
 * - Prefix / Suffix: LuckPerms (native) -> Vault Chat (fallback) -> empty
 * - Nickname / Display: EssentialsX -> Bukkit DisplayName -> Player Name
 * - World Name: Bukkit World Name
 * - World Alias: Multiverse-Core -> Bukkit World Name
 * - Placeholders: PlaceholderAPI (trusted templates only, never user message content)
 *
 * All integrations are strictly optional. Failures in any integration are caught and logged,
 * falling back safely so MineBridge never fails or disables because of a third-party hook.
 */
public class ChatMetadataService {

    private final LuckPermsHook luckPerms;
    private final VaultHook vault;
    private final EssentialsHook essentials;
    private final MultiverseHook multiverse;
    private final PlaceholderApiHook placeholderApi;
    private final Consumer<String> errorLogger;

    /**
     * Standard runtime constructor for Bukkit.
     */
    public ChatMetadataService() {
        this((Consumer<String>) null);
    }

    /**
     * Runtime constructor with an error logger.
     */
    public ChatMetadataService(Consumer<String> errorLogger) {
        this(
                safeInit("LuckPerms", () -> new LuckPermsHook(errorLogger), new LuckPermsHook(() -> false, (p, u) -> "", (p, u) -> ""), errorLogger),
                safeInit("Vault", () -> new VaultHook(errorLogger), new VaultHook(() -> false, (p, w) -> "", (p, w) -> "", p -> ""), errorLogger),
                safeInit("EssentialsX", () -> new EssentialsHook(errorLogger), new EssentialsHook(() -> false, p -> p != null ? p.getName() : ""), errorLogger),
                safeInit("Multiverse-Core", () -> new MultiverseHook(errorLogger), new MultiverseHook(() -> false, w -> w != null ? w.getName() : ""), errorLogger),
                safeInit("PlaceholderAPI", () -> new PlaceholderApiHook(errorLogger), new PlaceholderApiHook(() -> false, (p, t) -> t), errorLogger),
                errorLogger
        );
    }

    /**
     * Constructor allowing injected hooks for unit tests.
     */
    public ChatMetadataService(
            LuckPermsHook luckPerms,
            VaultHook vault,
            EssentialsHook essentials,
            MultiverseHook multiverse,
            PlaceholderApiHook placeholderApi) {
        this(luckPerms, vault, essentials, multiverse, placeholderApi, null);
    }

    /**
     * Full constructor allowing injected hooks and error logger.
     */
    public ChatMetadataService(
            LuckPermsHook luckPerms,
            VaultHook vault,
            EssentialsHook essentials,
            MultiverseHook multiverse,
            PlaceholderApiHook placeholderApi,
            Consumer<String> errorLogger) {
        this.errorLogger = errorLogger;
        this.luckPerms = luckPerms != null ? luckPerms : safeInit("LuckPerms", () -> new LuckPermsHook(errorLogger), new LuckPermsHook(() -> false, (p, u) -> "", (p, u) -> ""), errorLogger);
        this.vault = vault != null ? vault : safeInit("Vault", () -> new VaultHook(errorLogger), new VaultHook(() -> false, (p, w) -> "", (p, w) -> "", p -> ""), errorLogger);
        this.essentials = essentials != null ? essentials : safeInit("EssentialsX", () -> new EssentialsHook(errorLogger), new EssentialsHook(() -> false, p -> p != null ? p.getName() : ""), errorLogger);
        this.multiverse = multiverse != null ? multiverse : safeInit("Multiverse-Core", () -> new MultiverseHook(errorLogger), new MultiverseHook(() -> false, w -> w != null ? w.getName() : ""), errorLogger);
        this.placeholderApi = placeholderApi != null ? placeholderApi : safeInit("PlaceholderAPI", () -> new PlaceholderApiHook(errorLogger), new PlaceholderApiHook(() -> false, (p, t) -> t), errorLogger);
    }

    private static <T> T safeInit(String name, Supplier<T> supplier, T fallback, Consumer<String> errorLogger) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            if (errorLogger != null) {
                errorLogger.accept("Failed to initialize optional integration " + name + ": "
                        + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
            }
            return fallback;
        }
    }

    private void logHookError(String integration, String action, Throwable t) {
        HookUtil.checkFatal(t);
        if (errorLogger != null) {
            errorLogger.accept("Optional integration " + integration + " failed during " + action + ": "
                    + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
        }
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPerms;
    }

    public VaultHook getVaultHook() {
        return vault;
    }

    public EssentialsHook getEssentialsHook() {
        return essentials;
    }

    public MultiverseHook getMultiverseHook() {
        return multiverse;
    }

    public PlaceholderApiHook getPlaceholderApiHook() {
        return placeholderApi;
    }

    /**
     * Resolves effective prefix with strict precedence: LuckPerms -> Vault -> empty.
     */
    public String getPrefix(Player player) {
        if (player == null) {
            return "";
        }
        try {
            if (luckPerms != null && luckPerms.isAvailable()) {
                String lpPrefix = luckPerms.getPrefix(player);
                if (lpPrefix != null && !lpPrefix.isBlank()) {
                    return lpPrefix;
                }
            }
        } catch (Throwable t) {
            logHookError("LuckPerms", "getPrefix", t);
        }
        try {
            if (vault != null && vault.isAvailable()) {
                String vaultPrefix = vault.getPrefix(player);
                if (vaultPrefix != null && !vaultPrefix.isBlank()) {
                    return vaultPrefix;
                }
            }
        } catch (Throwable t) {
            logHookError("Vault", "getPrefix", t);
        }
        return "";
    }

    /**
     * Resolves effective suffix with strict precedence: LuckPerms -> Vault -> empty.
     */
    public String getSuffix(Player player) {
        if (player == null) {
            return "";
        }
        try {
            if (luckPerms != null && luckPerms.isAvailable()) {
                String lpSuffix = luckPerms.getSuffix(player);
                if (lpSuffix != null && !lpSuffix.isBlank()) {
                    return lpSuffix;
                }
            }
        } catch (Throwable t) {
            logHookError("LuckPerms", "getSuffix", t);
        }
        try {
            if (vault != null && vault.isAvailable()) {
                String vaultSuffix = vault.getSuffix(player);
                if (vaultSuffix != null && !vaultSuffix.isBlank()) {
                    return vaultSuffix;
                }
            }
        } catch (Throwable t) {
            logHookError("Vault", "getSuffix", t);
        }
        return "";
    }

    /**
     * Resolves player display nickname: EssentialsX -> Bukkit DisplayName -> Real Name.
     */
    public String getNickname(Player player) {
        if (player == null) {
            return "";
        }
        try {
            if (essentials != null && essentials.isAvailable()) {
                String nick = essentials.getNickname(player);
                if (nick != null && !nick.isBlank()) {
                    return nick;
                }
            }
        } catch (Throwable t) {
            logHookError("EssentialsX", "getNickname", t);
        }
        try {
            String displayName = player.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
        }
        try {
            return player.getName() != null ? player.getName() : "";
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
            return "";
        }
    }

    /**
     * Resolves player's current world name.
     */
    public String getWorldName(Player player) {
        if (player == null) {
            return "";
        }
        try {
            if (player.getWorld() != null && player.getWorld().getName() != null) {
                return player.getWorld().getName();
            }
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
        }
        return "";
    }

    /**
     * Resolves player's current world alias: Multiverse-Core -> Bukkit World Name.
     */
    public String getWorldAlias(Player player) {
        if (player == null) {
            return "";
        }
        World world = null;
        try {
            world = player.getWorld();
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
        }
        if (world == null) {
            return "";
        }
        try {
            if (multiverse != null && multiverse.isAvailable()) {
                String alias = multiverse.getWorldAlias(world);
                if (alias != null && !alias.isBlank()) {
                    return alias;
                }
            }
        } catch (Throwable t) {
            logHookError("Multiverse-Core", "getWorldAlias", t);
        }
        try {
            return world.getName() != null ? world.getName() : "";
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
            return "";
        }
    }

    /**
     * Applies PlaceholderAPI on trusted server templates. Never on user chat messages.
     */
    public String applyPlaceholders(Player player, String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        try {
            if (placeholderApi != null) {
                return placeholderApi.applyPlaceholders(player, template);
            }
        } catch (Throwable t) {
            logHookError("PlaceholderAPI", "applyPlaceholders", t);
        }
        return template;
    }
}
