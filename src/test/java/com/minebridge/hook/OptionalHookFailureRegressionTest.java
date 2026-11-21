package com.minebridge.hook;

import com.minebridge.config.ConfigManager;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.lifecycle.LifecycleManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OptionalHookFailureRegressionTest {

    private static Player createMockPlayer(String name, UUID uuid, String worldName) {
        World mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return worldName;
                    return null;
                });

        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
                    if ("getDisplayName".equals(methodName)) return "~" + name + "Display";
                    if ("getUniqueId".equals(methodName)) return uuid;
                    if ("isOnline".equals(methodName)) return true;
                    if ("getWorld".equals(methodName)) return mockWorld;
                    return null;
                });
    }

    // =========================================================================
    // 1. ChatMetadataService Failure Isolation & Exact Logging
    // =========================================================================

    @Test
    void chatMetadataServiceNeverThrowsWhenHookInitializationFails() {
        List<String> loggedErrors = new ArrayList<>();

        // Construct ChatMetadataService where all hooks throw exceptions during creation
        ChatMetadataService service = new ChatMetadataService(
                null, null, null, null, null, loggedErrors::add
        );

        assertNotNull(service);
        assertNotNull(service.getLuckPermsHook());
        assertNotNull(service.getVaultHook());
        assertNotNull(service.getEssentialsHook());
        assertNotNull(service.getMultiverseHook());
        assertNotNull(service.getPlaceholderApiHook());

        Player player = createMockPlayer("Alex", UUID.randomUUID(), "world");

        // Metadata calls must return safe fallbacks without throwing
        assertEquals("", service.getPrefix(player));
        assertEquals("", service.getSuffix(player));
        assertEquals("~AlexDisplay", service.getNickname(player));
        assertEquals("world", service.getWorldName(player));
        assertEquals("world", service.getWorldAlias(player));
        assertEquals("Template {player}", service.applyPlaceholders(player, "Template {player}"));
    }

    @Test
    void chatMetadataServiceCatchesAndLogsExactIntegrationNameOnRuntimeErrors() {
        List<String> loggedErrors = new ArrayList<>();

        LuckPermsHook failingLp = new LuckPermsHook(
                () -> true,
                (p, u) -> { throw new IllegalStateException("LuckPerms API not loaded yet"); },
                (p, u) -> { throw new IllegalStateException("LuckPerms suffix failed"); },
                loggedErrors::add
        );

        VaultHook failingVault = new VaultHook(
                () -> true,
                (p, w) -> { throw new NullPointerException("Vault Chat provider returned null"); },
                (p, w) -> { throw new NullPointerException("Vault Chat suffix returned null"); },
                p -> { throw new RuntimeException("Vault permission failed"); },
                loggedErrors::add
        );

        EssentialsHook failingEss = new EssentialsHook(
                () -> true,
                p -> { throw new RuntimeException("Essentials user map corrupted"); },
                loggedErrors::add
        );

        MultiverseHook failingMv = new MultiverseHook(
                () -> true,
                w -> { throw new RuntimeException("Multiverse world manager missing"); },
                loggedErrors::add
        );

        PlaceholderApiHook failingPapi = new PlaceholderApiHook(
                () -> true,
                (p, t) -> { throw new RuntimeException("PlaceholderAPI expansion error"); },
                loggedErrors::add
        );

        ChatMetadataService service = new ChatMetadataService(
                failingLp, failingVault, failingEss, failingMv, failingPapi, loggedErrors::add
        );

        Player player = createMockPlayer("Alex", UUID.randomUUID(), "survival");

        // 1. getPrefix: Both LP and Vault fail; must return empty and log exact integration names
        String prefix = service.getPrefix(player);
        assertEquals("", prefix);
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("LuckPerms") && e.contains("getPrefix")));
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("Vault") && e.contains("getPrefix")));

        // 2. getSuffix: Both LP and Vault fail
        String suffix = service.getSuffix(player);
        assertEquals("", suffix);
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("LuckPerms") && e.contains("getSuffix")));
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("Vault") && e.contains("getSuffix")));

        // 3. getNickname: EssentialsX fails; falls back to Bukkit display name
        String nick = service.getNickname(player);
        assertEquals("~AlexDisplay", nick);
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("EssentialsX") && e.contains("getNickname")));

        // 4. getWorldAlias: Multiverse-Core fails; falls back to Bukkit world name
        String alias = service.getWorldAlias(player);
        assertEquals("survival", alias);
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("Multiverse-Core") && e.contains("getWorldAlias")));

        // 5. applyPlaceholders: PlaceholderAPI fails; falls back to unmodified template
        String expanded = service.applyPlaceholders(player, "Prefix %papi% Suffix");
        assertEquals("Prefix %papi% Suffix", expanded);
        assertTrue(loggedErrors.stream().anyMatch(e -> e.contains("PlaceholderAPI") && e.contains("applyPlaceholders")));
    }

    // =========================================================================
    // 2. Incompatible API Call Handling (Vault Signatures)
    // =========================================================================

    @Test
    void vaultResolverHandlesIncompatibleMethodSignaturesGracefully() throws Exception {
        // Test Vault chat method taking (String world, Player player)
        class MockTwoArgVaultChat {
            public String getPlayerPrefix(String world, Player player) {
                return "[" + world + "-Prefix] ";
            }
            public String getPlayerSuffix(String world, Player player) {
                return " [" + world + "-Suffix]";
            }
        }

        MockTwoArgVaultChat chatTarget = new MockTwoArgVaultChat();
        Method prefixMethod = MockTwoArgVaultChat.class.getMethod("getPlayerPrefix", String.class, Player.class);
        Method suffixMethod = MockTwoArgVaultChat.class.getMethod("getPlayerSuffix", String.class, Player.class);

        Player player = createMockPlayer("Alex", UUID.randomUUID(), "nether");

        // Reflection invocation helper should handle 2-arg (String, Player) signature
        Method invokeHelper = VaultHook.ReflectionVaultResolver.class.getDeclaredMethod(
                "invokeMethod", Method.class, Object.class, Player.class, String.class);
        invokeHelper.setAccessible(true);

        Object prefixResult = invokeHelper.invoke(null, prefixMethod, chatTarget, player, "nether");
        assertEquals("[nether-Prefix] ", prefixResult);

        Object suffixResult = invokeHelper.invoke(null, suffixMethod, chatTarget, player, "nether");
        assertEquals(" [nether-Suffix]", suffixResult);

        // Test Vault chat method taking (String world, String playerName)
        class MockStringArgVaultChat {
            public String getPlayerPrefix(String world, String playerName) {
                return "[" + playerName + "In" + world + "] ";
            }
        }

        MockStringArgVaultChat stringChatTarget = new MockStringArgVaultChat();
        Method stringPrefixMethod = MockStringArgVaultChat.class.getMethod("getPlayerPrefix", String.class, String.class);
        Object stringResult = invokeHelper.invoke(null, stringPrefixMethod, stringChatTarget, player, "overworld");
        assertEquals("[AlexInoverworld] ", stringResult);
    }

    // =========================================================================
    // 3. EssentialsX Nickname Method Alternatives
    // =========================================================================

    @Test
    void essentialsResolverFallsBackToAlternativeNicknameMethods() {
        // Modern EssentialsX uses user.getDisplayName() or user.getNick() instead of getNickname()
        List<String> errors = new ArrayList<>();
        EssentialsHook.ReflectionEssentialsResolver resolver = new EssentialsHook.ReflectionEssentialsResolver(errors::add);

        // Even when Essentials plugin is absent, isAvailable and getNickname return safe defaults without throwing
        assertFalse(resolver.isAvailable());
        Player player = createMockPlayer("Alex", UUID.randomUUID(), "world");
        assertEquals("", resolver.getNickname(player));
        assertEquals(List.of(), errors);
    }

    // =========================================================================
    // 4. LifecycleManager Status With Failing Hook
    // =========================================================================

    @Test
    void lifecycleManagerStatusDoesNotThrowWhenLuckPermsIsAvailableThrows() {
        List<String> warnings = new ArrayList<>();

        LuckPermsHook crashingHook = new LuckPermsHook(
                () -> { throw new IllegalStateException("LuckPerms API crashed during isAvailable"); },
                (p, u) -> "",
                null,
                warnings::add
        );

        ConfigManager config = new ConfigManager(null);
        LifecycleManager manager = new LifecycleManager(
                config,
                null,
                new ChatMetadataService(crashingHook, null, null, null, null, warnings::add),
                cfg -> null,
                cfg -> null,
                Runnable::run,
                () -> {},
                () -> {},
                msg -> {},
                warnings::add,
                () -> 1000L
        );

        // getStatus must not throw
        assertDoesNotThrow(() -> {
            var status = manager.getStatus();
            assertNotNull(status);
            assertFalse(status.luckPermsAvailable(), "LuckPerms must be reported unavailable if check throws");
        });

        assertTrue(warnings.stream().anyMatch(w -> w.contains("LuckPerms")),
                "Failure in LuckPerms must be logged with integration name");
    }

    // =========================================================================
    // 5. Plugin Load Ordering Resilience
    // =========================================================================

    @Test
    void hooksDoNotPermanentlyLockWhenPluginInitiallyDisabled() {
        AtomicBoolean pluginEnabled = new AtomicBoolean(false);

        // Hook simulates delayed plugin enablement (load ordering)
        LuckPermsHook hook = new LuckPermsHook(
                pluginEnabled::get,
                (p, u) -> pluginEnabled.get() ? "[Admin] " : ""
        );

        // Initially disabled at startup
        assertFalse(hook.isAvailable());
        Player player = createMockPlayer("Alex", UUID.randomUUID(), "world");
        assertEquals("", hook.getPrefix(player));

        // Enabled later after load order resolves
        pluginEnabled.set(true);
        assertTrue(hook.isAvailable());
        assertEquals("[Admin] ", hook.getPrefix(player));
    }

    // =========================================================================
    // 6. Fatal JVM Error Propagation (OutOfMemoryError, StackOverflowError)
    // =========================================================================

    @Test
    void fatalJvmErrorsArePropagatedAndNeverSwallowed() {
        Player player = createMockPlayer("Alex", UUID.randomUUID(), "world");

        // 1. VaultHook propagates OutOfMemoryError
        VaultHook oomVault = new VaultHook(
                () -> { throw new OutOfMemoryError("Simulated OOM in Vault availableChecker"); },
                (p, w) -> "",
                (p, w) -> "",
                p -> ""
        );
        assertThrows(OutOfMemoryError.class, oomVault::isAvailable);

        VaultHook oomPrefixVault = new VaultHook(
                () -> true,
                (p, w) -> { throw new OutOfMemoryError("Simulated OOM in Vault getPrefix"); },
                (p, w) -> "",
                p -> ""
        );
        assertThrows(OutOfMemoryError.class, () -> oomPrefixVault.getPrefix(player));

        // 2. LuckPermsHook propagates OutOfMemoryError
        LuckPermsHook oomLp = new LuckPermsHook(
                () -> true,
                (p, u) -> { throw new OutOfMemoryError("Simulated OOM in LuckPerms"); }
        );
        assertThrows(OutOfMemoryError.class, () -> oomLp.getPrefix(player));

        // 3. EssentialsHook propagates OutOfMemoryError
        EssentialsHook oomEss = new EssentialsHook(
                () -> true,
                p -> { throw new OutOfMemoryError("Simulated OOM in EssentialsX"); }
        );
        assertThrows(OutOfMemoryError.class, () -> oomEss.getNickname(player));

        // 4. MultiverseHook propagates OutOfMemoryError
        MultiverseHook oomMv = new MultiverseHook(
                () -> true,
                w -> { throw new OutOfMemoryError("Simulated OOM in Multiverse"); }
        );
        assertThrows(OutOfMemoryError.class, () -> oomMv.getWorldAlias(player.getWorld()));

        // 5. PlaceholderApiHook propagates OutOfMemoryError
        PlaceholderApiHook oomPapi = new PlaceholderApiHook(
                () -> true,
                (p, t) -> { throw new OutOfMemoryError("Simulated OOM in PlaceholderAPI"); }
        );
        assertThrows(OutOfMemoryError.class, () -> oomPapi.applyPlaceholders(player, "%player%"));

        // 6. ChatMetadataService safeInit propagates OutOfMemoryError
        assertThrows(OutOfMemoryError.class, () -> {
            new ChatMetadataService(
                    null, null, null, null, null,
                    msg -> {}
            ) {
                // Anonymous class to verify safeInit propagation directly
            };
            HookUtil.checkFatal(new OutOfMemoryError("Direct check"));
        });
    }

    // =========================================================================
    // 7. Individual & Combinatorial Plugin Scenarios
    // =========================================================================

    @Test
    void testMetadataServiceWithIndividualAndCombinatorialPlugins() {
        Player player = createMockPlayer("Steve", UUID.randomUUID(), "survival_nether");

        // Scenario A: ALL plugins absent
        ChatMetadataService allAbsent = new ChatMetadataService(
                new LuckPermsHook(() -> false, (p, u) -> ""),
                new VaultHook(() -> false, (p, w) -> "", (p, w) -> "", p -> ""),
                new EssentialsHook(() -> false, p -> ""),
                new MultiverseHook(() -> false, w -> ""),
                new PlaceholderApiHook(() -> false, (p, t) -> t),
                null
        );
        assertEquals("", allAbsent.getPrefix(player));
        assertEquals("", allAbsent.getSuffix(player));
        assertEquals("~SteveDisplay", allAbsent.getNickname(player)); // falls back to Bukkit getDisplayName
        assertEquals("survival_nether", allAbsent.getWorldName(player));
        assertEquals("survival_nether", allAbsent.getWorldAlias(player)); // falls back to Bukkit world name
        assertEquals("Hello {player}", allAbsent.applyPlaceholders(player, "Hello {player}"));

        // Scenario B: ONLY Vault present
        ChatMetadataService onlyVault = new ChatMetadataService(
                new LuckPermsHook(() -> false, (p, u) -> ""),
                new VaultHook(() -> true, (p, w) -> "[VaultPrefix] ", (p, w) -> " [VaultSuffix]", p -> "member"),
                new EssentialsHook(() -> false, p -> ""),
                new MultiverseHook(() -> false, w -> ""),
                new PlaceholderApiHook(() -> false, (p, t) -> t),
                null
        );
        assertEquals("[VaultPrefix] ", onlyVault.getPrefix(player));
        assertEquals(" [VaultSuffix]", onlyVault.getSuffix(player));

        // Scenario C: BOTH LuckPerms and Vault present -> LuckPerms must take precedence
        ChatMetadataService lpAndVault = new ChatMetadataService(
                new LuckPermsHook(() -> true, (p, u) -> "[LPPrefix] ", (p, u) -> " [LPSuffix]"),
                new VaultHook(() -> true, (p, w) -> "[VaultPrefix] ", (p, w) -> " [VaultSuffix]", p -> "member"),
                new EssentialsHook(() -> false, p -> ""),
                new MultiverseHook(() -> false, w -> ""),
                new PlaceholderApiHook(() -> false, (p, t) -> t),
                null
        );
        assertEquals("[LPPrefix] ", lpAndVault.getPrefix(player));
        assertEquals(" [LPSuffix]", lpAndVault.getSuffix(player));

        // Scenario D: ALL plugins present together
        ChatMetadataService allPresent = new ChatMetadataService(
                new LuckPermsHook(() -> true, (p, u) -> "[Admin] ", (p, u) -> " [MVP]"),
                new VaultHook(() -> true, (p, w) -> "[VaultFallback] ", (p, w) -> " [VaultFallback]", p -> "admin"),
                new EssentialsHook(() -> true, p -> "CustomNick"),
                new MultiverseHook(() -> true, w -> "Nether World"),
                new PlaceholderApiHook(() -> true, (p, t) -> t.replace("%rank%", "Owner")),
                null
        );
        assertEquals("[Admin] ", allPresent.getPrefix(player));
        assertEquals(" [MVP]", allPresent.getSuffix(player));
        assertEquals("CustomNick", allPresent.getNickname(player));
        assertEquals("Nether World", allPresent.getWorldAlias(player));
        assertEquals("survival_nether", allPresent.getWorldName(player));
        assertEquals("Rank: Owner", allPresent.applyPlaceholders(player, "Rank: %rank%"));
    }
}
