package com.minebridge.hook;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class VaultHookTest {

    private static Player createMockPlayer(String name, String worldName) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
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
    void vaultAvailableWithPrefixAndSuffix() {
        Player player = createMockPlayer("Alex", "world");
        VaultHook hook = new VaultHook(
                () -> true,
                (p, w) -> "[VaultVIP] ",
                (p, w) -> " [Supporter]",
                p -> "vip"
        );

        assertTrue(hook.isAvailable());
        assertEquals("[VaultVIP] ", hook.getPrefix(player));
        assertEquals(" [Supporter]", hook.getSuffix(player));
        assertEquals("vip", hook.getPrimaryGroup(player));
    }

    @Test
    void vaultAbsentOrDisabledReturnsEmptyGracefully() {
        Player player = createMockPlayer("Alex", "world");
        VaultHook hook = new VaultHook(
                () -> false,
                (p, w) -> "[ShouldNotAppear]",
                (p, w) -> "[ShouldNotAppear]",
                p -> "admin"
        );

        assertFalse(hook.isAvailable());
        assertEquals("", hook.getPrefix(player));
        assertEquals("", hook.getSuffix(player));
        assertEquals("", hook.getPrimaryGroup(player));
    }

    @Test
    void vaultHandlesMissingOrNullProvidersGracefully() {
        Player player = createMockPlayer("Alex", "world");
        VaultHook hookNullChat = new VaultHook(
                () -> true,
                null,
                null,
                p -> "default"
        );

        assertTrue(hookNullChat.isAvailable());
        assertEquals("", hookNullChat.getPrefix(player));
        assertEquals("", hookNullChat.getSuffix(player));
        assertEquals("default", hookNullChat.getPrimaryGroup(player));

        // When resolver throws exception
        VaultHook hookThrowing = new VaultHook(
                () -> true,
                (p, w) -> { throw new RuntimeException("Vault Chat error"); },
                (p, w) -> { throw new RuntimeException("Vault Chat error"); },
                p -> { throw new RuntimeException("Vault Perm error"); }
        );
        assertEquals("", hookThrowing.getPrefix(player));
        assertEquals("", hookThrowing.getSuffix(player));
        assertEquals("", hookThrowing.getPrimaryGroup(player));
    }

    @Test
    void reflectionResolverFailsGracefullyWhenVaultMissing() {
        VaultHook.ReflectionVaultResolver resolver = new VaultHook.ReflectionVaultResolver();
        assertFalse(resolver.isAvailable());
        assertEquals("", resolver.getPrefix(null, ""));
        assertEquals("", resolver.getSuffix(null, ""));
        assertEquals("", resolver.getPrimaryGroup(null));

        VaultHook defaultHook = new VaultHook();
        assertFalse(defaultHook.isAvailable());
        assertEquals("", defaultHook.getPrefix(null));
    }
}
