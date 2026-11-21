package com.minebridge.hook;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class MultiverseHookTest {

    private static World createMockWorld(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return name;
                    return null;
                });
    }

    @Test
    void multiverseAvailableResolvesAlias() {
        World world = createMockWorld("world_nether");
        MultiverseHook hook = new MultiverseHook(() -> true, w -> "Nether Realm");

        assertTrue(hook.isAvailable());
        assertEquals("Nether Realm", hook.getWorldAlias(world));
    }

    @Test
    void multiverseAbsentFallsBackToWorldName() {
        World world = createMockWorld("world_the_end");
        MultiverseHook hook = new MultiverseHook(() -> false, w -> "End City");

        assertFalse(hook.isAvailable());
        assertEquals("world_the_end", hook.getWorldAlias(world));
    }

    @Test
    void multiverseEmptyAliasFallsBackToWorldName() {
        World world = createMockWorld("survival_spawn");
        MultiverseHook hookEmpty = new MultiverseHook(() -> true, w -> "");
        assertEquals("survival_spawn", hookEmpty.getWorldAlias(world));

        MultiverseHook hookNull = new MultiverseHook(() -> true, w -> null);
        assertEquals("survival_spawn", hookNull.getWorldAlias(world));
    }

    @Test
    void nullWorldReturnsEmptySafely() {
        MultiverseHook hook = new MultiverseHook(() -> true, w -> "Alias");
        assertEquals("", hook.getWorldAlias(null));
    }

    @Test
    void reflectionResolverFailsGracefullyWhenMultiverseMissing() {
        MultiverseHook.ReflectionMultiverseResolver resolver = new MultiverseHook.ReflectionMultiverseResolver();
        assertFalse(resolver.isAvailable());
        assertEquals("", resolver.getWorldAlias(null));

        MultiverseHook defaultHook = new MultiverseHook();
        assertFalse(defaultHook.isAvailable());
        World world = createMockWorld("overworld");
        assertEquals("overworld", defaultHook.getWorldAlias(world));
    }
}
