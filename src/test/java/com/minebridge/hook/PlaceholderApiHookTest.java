package com.minebridge.hook;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderApiHookTest {

    private static Player createMockPlayer(String name) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return name;
                    if ("getUniqueId".equals(method.getName())) return UUID.randomUUID();
                    return null;
                });
    }

    @Test
    void placeholderApiAvailableReplacesPlaceholders() {
        Player player = createMockPlayer("Alex");
        PlaceholderApiHook hook = new PlaceholderApiHook(
                () -> true,
                (p, template) -> template.replace("%vault_eco_balance%", "$1000")
        );

        assertTrue(hook.isAvailable());
        String result = hook.applyPlaceholders(player, "{prefix}**{player}** (%vault_eco_balance%): {message}");
        assertEquals("{prefix}**{player}** ($1000): {message}", result);
    }

    @Test
    void placeholderApiSkipsExecutionWhenNoPercentSymbolPresent() {
        Player player = createMockPlayer("Alex");
        // Throw if called to prove the fast-path skip works
        PlaceholderApiHook hook = new PlaceholderApiHook(
                () -> true,
                (p, template) -> { throw new AssertionError("Should not be called when '%' is absent"); }
        );

        String template = "{prefix}**{player}**: {message}";
        assertEquals(template, hook.applyPlaceholders(player, template));
    }

    @Test
    void placeholderApiAbsentReturnsTemplateUntouched() {
        Player player = createMockPlayer("Alex");
        PlaceholderApiHook hook = new PlaceholderApiHook(
                () -> false,
                (p, template) -> template.replace("%test%", "expanded")
        );

        assertFalse(hook.isAvailable());
        String template = "Level: %player_level%";
        assertEquals(template, hook.applyPlaceholders(player, template));
    }

    @Test
    void nullPlayerOrNullTemplateHandledSafely() {
        PlaceholderApiHook hook = new PlaceholderApiHook(() -> true, (p, t) -> "expanded");

        assertEquals("", hook.applyPlaceholders(null, ""));
        assertEquals("", hook.applyPlaceholders(null, null));
        assertEquals("%test%", hook.applyPlaceholders(null, "%test%"));
    }

    @Test
    void throwingReplacerFailsGracefullyReturningOriginalTemplate() {
        Player player = createMockPlayer("Alex");
        PlaceholderApiHook hook = new PlaceholderApiHook(
                () -> true,
                (p, t) -> { throw new RuntimeException("PAPI internal failure"); }
        );

        String template = "Rank: %rank%";
        assertEquals(template, hook.applyPlaceholders(player, template));
    }

    @Test
    void reflectionResolverFailsGracefullyWhenPapiMissing() {
        PlaceholderApiHook.ReflectionPlaceholderApiResolver resolver =
                new PlaceholderApiHook.ReflectionPlaceholderApiResolver();
        assertFalse(resolver.isAvailable());
        assertEquals("test", resolver.setPlaceholders(null, "test"));

        PlaceholderApiHook defaultHook = new PlaceholderApiHook();
        assertFalse(defaultHook.isAvailable());
    }
}
