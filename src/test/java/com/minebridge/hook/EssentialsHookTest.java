package com.minebridge.hook;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EssentialsHookTest {

    private static Player createMockPlayer(String name, String displayName) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
                    if ("getDisplayName".equals(methodName)) return displayName;
                    if ("getUniqueId".equals(methodName)) return UUID.randomUUID();
                    return null;
                });
    }

    @Test
    void essentialsAvailableWithNickname() {
        Player player = createMockPlayer("Alex", "AlexDisplay");
        EssentialsHook hook = new EssentialsHook(() -> true, p -> "CoolAlex");

        assertTrue(hook.isAvailable());
        assertEquals("CoolAlex", hook.getNickname(player));
    }

    @Test
    void essentialsNicknameStripsTilde() {
        Player player = createMockPlayer("Alex", "AlexDisplay");
        // ReflectionEssentialsResolver strips leading '~'
        EssentialsHook hook = new EssentialsHook(() -> true, p -> "~NickAlex");

        assertEquals("~NickAlex", hook.getNickname(player));
    }

    @Test
    void essentialsAbsentFallsBackToDisplayNameThenPlayerName() {
        Player playerWithDisplay = createMockPlayer("Alex", "AlexDisplay");
        Player playerWithoutDisplay = createMockPlayer("Bob", "");

        EssentialsHook hookAbsent = new EssentialsHook(() -> false, p -> "FakeNick");

        assertFalse(hookAbsent.isAvailable());
        // Falls back to Bukkit display name
        assertEquals("AlexDisplay", hookAbsent.getNickname(playerWithDisplay));
        // Falls back to player name
        assertEquals("Bob", hookAbsent.getNickname(playerWithoutDisplay));
    }

    @Test
    void essentialsEmptyNicknameFallsBackToDisplayName() {
        Player player = createMockPlayer("Alex", "AlexDisplay");
        EssentialsHook hookEmpty = new EssentialsHook(() -> true, p -> "");

        assertEquals("AlexDisplay", hookEmpty.getNickname(player));

        EssentialsHook hookNull = new EssentialsHook(() -> true, p -> null);
        assertEquals("AlexDisplay", hookNull.getNickname(player));
    }

    @Test
    void reflectionResolverFailsGracefullyWhenEssentialsMissing() {
        EssentialsHook.ReflectionEssentialsResolver resolver = new EssentialsHook.ReflectionEssentialsResolver();
        assertFalse(resolver.isAvailable());
        assertEquals("", resolver.getNickname(null));

        EssentialsHook defaultHook = new EssentialsHook();
        assertFalse(defaultHook.isAvailable());
        Player player = createMockPlayer("Alex", "AlexDisplay");
        assertEquals("AlexDisplay", defaultHook.getNickname(player));
    }
}
