package com.minebridge.hook;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatMetadataServiceTest {

    private static Player createMockPlayer(String name, String displayName, String worldName) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
                    if ("getDisplayName".equals(methodName)) return displayName;
                    if ("getUniqueId".equals(methodName)) return UUID.randomUUID();
                    if ("getWorld".equals(methodName)) {
                        return (World) Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[]{World.class},
                                (wProxy, wMethod, wArgs) -> {
                                    if ("getName".equals(wMethod.getName())) return worldName;
                                    return null;
                                });
                    }
                    return null;
                });
    }

    @Test
    void luckPermsTakesPrecedenceOverVaultToPreventDuplicatePrefixes() {
        Player player = createMockPlayer("Alex", "Alex", "world");

        LuckPermsHook luckPerms = new LuckPermsHook(
                () -> true,
                (p, uuid) -> "[LP-Admin] ",
                (p, uuid) -> " [LP-Suffix]"
        );
        VaultHook vault = new VaultHook(
                () -> true,
                (p, w) -> "[Vault-Admin] ",
                (p, w) -> " [Vault-Suffix]",
                p -> "admin"
        );

        ChatMetadataService service = new ChatMetadataService(luckPerms, vault, null, null, null);

        // LuckPerms must take precedence; Vault must NOT duplicate
        assertEquals("[LP-Admin] ", service.getPrefix(player));
        assertEquals(" [LP-Suffix]", service.getSuffix(player));
    }

    @Test
    void vaultUsedAsFallbackWhenLuckPermsIsAbsent() {
        Player player = createMockPlayer("Alex", "Alex", "world");

        LuckPermsHook luckPermsAbsent = new LuckPermsHook(() -> false, (p, uuid) -> "[LP]");
        VaultHook vault = new VaultHook(
                () -> true,
                (p, w) -> "[Vault-Mod] ",
                (p, w) -> " [Vault-Helper]",
                p -> "mod"
        );

        ChatMetadataService service = new ChatMetadataService(luckPermsAbsent, vault, null, null, null);

        assertEquals("[Vault-Mod] ", service.getPrefix(player));
        assertEquals(" [Vault-Helper]", service.getSuffix(player));
    }

    @Test
    void vaultUsedAsFallbackWhenLuckPermsReturnsEmpty() {
        Player player = createMockPlayer("Alex", "Alex", "world");

        LuckPermsHook luckPermsEmpty = new LuckPermsHook(() -> true, (p, uuid) -> "", (p, uuid) -> "");
        VaultHook vault = new VaultHook(
                () -> true,
                (p, w) -> "[Vault-VIP] ",
                (p, w) -> " [VIP-Star]",
                p -> "vip"
        );

        ChatMetadataService service = new ChatMetadataService(luckPermsEmpty, vault, null, null, null);

        assertEquals("[Vault-VIP] ", service.getPrefix(player));
        assertEquals(" [VIP-Star]", service.getSuffix(player));
    }

    @Test
    void emptyPrefixAndSuffixWhenBothAbsent() {
        Player player = createMockPlayer("Alex", "Alex", "world");

        LuckPermsHook lpAbsent = new LuckPermsHook(() -> false, (p, uuid) -> null);
        VaultHook vaultAbsent = new VaultHook(() -> false, null, null, null);

        ChatMetadataService service = new ChatMetadataService(lpAbsent, vaultAbsent, null, null, null);

        assertEquals("", service.getPrefix(player));
        assertEquals("", service.getSuffix(player));
    }

    @Test
    void nicknameResolutionPrecedence() {
        Player playerWithNick = createMockPlayer("Alex", "AlexDisplay", "world");

        // 1. Essentials available -> returns Essentials nick
        EssentialsHook essActive = new EssentialsHook(() -> true, p -> "SuperAlex");
        ChatMetadataService service1 = new ChatMetadataService(null, null, essActive, null, null);
        assertEquals("SuperAlex", service1.getNickname(playerWithNick));

        // 2. Essentials absent -> falls back to Bukkit display name
        EssentialsHook essAbsent = new EssentialsHook(() -> false, p -> "SuperAlex");
        ChatMetadataService service2 = new ChatMetadataService(null, null, essAbsent, null, null);
        assertEquals("AlexDisplay", service2.getNickname(playerWithNick));

        // 3. Bukkit display name blank -> falls back to real player name
        Player playerNoDisplay = createMockPlayer("Alex", "", "world");
        ChatMetadataService service3 = new ChatMetadataService(null, null, essAbsent, null, null);
        assertEquals("Alex", service3.getNickname(playerNoDisplay));
    }

    @Test
    void worldIdentificationAndMultiverseAlias() {
        Player player = createMockPlayer("Alex", "Alex", "custom_nether");

        MultiverseHook mvActive = new MultiverseHook(() -> true, w -> "Infernal Abyss");
        ChatMetadataService serviceActive = new ChatMetadataService(null, null, null, mvActive, null);

        assertEquals("custom_nether", serviceActive.getWorldName(player));
        assertEquals("Infernal Abyss", serviceActive.getWorldAlias(player));

        MultiverseHook mvAbsent = new MultiverseHook(() -> false, w -> "Infernal Abyss");
        ChatMetadataService serviceAbsent = new ChatMetadataService(null, null, null, mvAbsent, null);

        assertEquals("custom_nether", serviceAbsent.getWorldName(player));
        assertEquals("custom_nether", serviceAbsent.getWorldAlias(player));
    }

    @Test
    void placeholderApiAppliedOnTemplates() {
        Player player = createMockPlayer("Alex", "Alex", "world");

        PlaceholderApiHook papi = new PlaceholderApiHook(
                () -> true,
                (p, template) -> template.replace("%server_online%", "42")
        );
        ChatMetadataService service = new ChatMetadataService(null, null, null, null, papi);

        String template = "{prefix}**{player}** (Online: %server_online%): {message}";
        assertEquals("{prefix}**{player}** (Online: 42): {message}", service.applyPlaceholders(player, template));
    }

    @Test
    void simultaneousPresenceOfAllFiveIntegrations() {
        Player player = createMockPlayer("Alex", "AlexDisplayName", "world_nether");

        LuckPermsHook lp = new LuckPermsHook(() -> true, (p, u) -> "[Admin] ", (p, u) -> " [Staff]");
        VaultHook vault = new VaultHook(() -> true, (p, w) -> "[Vault]", (p, w) -> "[VSuffix]", p -> "admin");
        EssentialsHook ess = new EssentialsHook(() -> true, p -> "TheBoss");
        MultiverseHook mv = new MultiverseHook(() -> true, w -> "NetherSMP");
        PlaceholderApiHook papi = new PlaceholderApiHook(() -> true, (p, t) -> t.replace("%rank%", "Owner"));

        ChatMetadataService service = new ChatMetadataService(lp, vault, ess, mv, papi);

        assertEquals("[Admin] ", service.getPrefix(player));
        assertEquals(" [Staff]", service.getSuffix(player));
        assertEquals("TheBoss", service.getNickname(player));
        assertEquals("world_nether", service.getWorldName(player));
        assertEquals("NetherSMP", service.getWorldAlias(player));
        assertEquals("Title: Owner", service.applyPlaceholders(player, "Title: %rank%"));
    }
}
