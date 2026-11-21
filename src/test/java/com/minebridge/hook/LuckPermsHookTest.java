package com.minebridge.hook;

import com.minebridge.discord.RelayMessageFormatter;
import com.minebridge.listener.ChatListener;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LuckPermsHookTest {

    private static final UUID TEST_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void luckPermsAvailableWithPrefix() {
        LuckPermsHook hook = new LuckPermsHook(() -> true, uuid -> "[Admin] ");

        assertTrue(hook.isAvailable());
        assertEquals("[Admin] ", hook.getPrefix(TEST_UUID));
    }

    @Test
    void luckPermsAvailableWithoutPrefix() {
        LuckPermsHook hookNull = new LuckPermsHook(() -> true, uuid -> null);
        assertTrue(hookNull.isAvailable());
        assertEquals("", hookNull.getPrefix(TEST_UUID));

        LuckPermsHook hookEmpty = new LuckPermsHook(() -> true, uuid -> "");
        assertTrue(hookEmpty.isAvailable());
        assertEquals("", hookEmpty.getPrefix(TEST_UUID));

        LuckPermsHook hookWhitespace = new LuckPermsHook(() -> true, uuid -> "   ");
        assertTrue(hookWhitespace.isAvailable());
        // Whitespace-only prefix handled gracefully
        assertEquals("   ", hookWhitespace.getPrefix(TEST_UUID));
    }

    @Test
    void luckPermsAbsent() {
        LuckPermsHook hook = new LuckPermsHook(() -> false, uuid -> "[Admin] ");

        assertFalse(hook.isAvailable());
        assertEquals("", hook.getPrefix(TEST_UUID));
    }

    @Test
    void luckPermsDisabled() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        LuckPermsHook hook = new LuckPermsHook(enabled::get, uuid -> "[Admin] ");

        assertTrue(hook.isAvailable());
        assertEquals("[Admin] ", hook.getPrefix(TEST_UUID));

        // Plugin disabled at runtime (e.g. reload or uninstalled)
        enabled.set(false);
        assertFalse(hook.isAvailable());
        assertEquals("", hook.getPrefix(TEST_UUID));
    }

    @Test
    void nullOrUnavailableUserData() {
        LuckPermsHook hook = new LuckPermsHook(() -> true, uuid -> "[VIP] ");

        assertEquals("", hook.getPrefix((UUID) null));
        assertEquals("", hook.getPrefix((org.bukkit.entity.Player) null));

        // When resolver throws an exception (simulating API failure or unloaded metadata)
        LuckPermsHook errorHook = new LuckPermsHook(() -> true, uuid -> {
            throw new IllegalStateException("LuckPerms internal error");
        });
        assertTrue(errorHook.isAvailable());
        assertEquals("", errorHook.getPrefix(TEST_UUID));
    }

    @Test
    void prefixFormattingInWebhookChat() {
        LuckPermsHook hook = new LuckPermsHook(() -> true, uuid -> "[VIP] ");
        String prefix = hook.getPrefix(TEST_UUID);

        // 1. Webhook username expansion with {prefix}
        String usernameTemplate = "{prefix}{player}";
        String resolvedUsername = usernameTemplate
                .replace("{prefix}", prefix)
                .replace("{player}", "Alex");
        assertEquals("[VIP] Alex", resolvedUsername);

        // 2. Chat message payload formatting with {prefix}
        List<String> sent = new ArrayList<>();
        boolean relayed = ChatListener.relayIfNonBlank(
                "[{server}] {prefix}{player}: {message}",
                "Alex",
                "Hello Webhook!",
                Instant.now().toString(),
                "SMP",
                prefix,
                sent::add);

        assertTrue(relayed);
        assertEquals(1, sent.size());
        assertEquals("[SMP] \\[VIP\\] Alex: Hello Webhook!", sent.get(0));
    }

    @Test
    void emptyPrefixFallback() {
        LuckPermsHook hook = new LuckPermsHook(() -> false, uuid -> null);
        String prefix = hook.getPrefix(TEST_UUID);
        assertEquals("", prefix);

        // Webhook username fallback
        String usernameTemplate = "{prefix}{player}";
        String resolvedUsername = usernameTemplate
                .replace("{prefix}", prefix)
                .replace("{player}", "Alex");
        assertEquals("Alex", resolvedUsername);

        // Chat formatting fallback
        List<String> sent = new ArrayList<>();
        boolean relayed = ChatListener.relayIfNonBlank(
                "[{server}] {prefix}{player}: {message}",
                "Alex",
                "Hello without prefix!",
                Instant.now().toString(),
                "SMP",
                prefix,
                sent::add);

        assertTrue(relayed);
        assertEquals(1, sent.size());
        assertEquals("[SMP] Alex: Hello without prefix!", sent.get(0));
    }

    @Test
    void noBlockingOperationsOnMainThread() {
        LuckPermsHook hook = new LuckPermsHook(() -> true, uuid -> "[Fast] ");

        long startNanos = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            String prefix = hook.getPrefix(TEST_UUID);
            assertEquals("[Fast] ", prefix);
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        // 10,000 in-memory lookups should complete in well under 50 milliseconds
        assertTrue(elapsedNanos < 50_000_000L, "Cached lookup took too long: " + elapsedNanos + " ns");
    }

    @Test
    void noRegressionInBotFormatting() {
        LuckPermsHook hook = new LuckPermsHook(() -> true, uuid -> "[VIP] ");
        String prefix = hook.getPrefix(TEST_UUID);

        // BOT mode with default template does not leak prefix into output
        List<String> botSent = new ArrayList<>();
        boolean relayedDefault = ChatListener.relayIfNonBlank(
                "**{player}**: {message}",
                "Alex",
                "Standard bot message",
                Instant.now().toString(),
                "Minecraft Server",
                prefix,
                botSent::add);

        assertTrue(relayedDefault);
        assertEquals(List.of("**Alex**: Standard bot message"), botSent);

        // BOT mode with explicit prefix template expands correctly
        List<String> botCustomSent = new ArrayList<>();
        boolean relayedCustom = ChatListener.relayIfNonBlank(
                "**{prefix}{player}**: {message}",
                "Alex",
                "Custom bot message",
                Instant.now().toString(),
                "Minecraft Server",
                prefix,
                botCustomSent::add);

        assertTrue(relayedCustom);
        assertEquals(List.of("**\\[VIP\\] Alex**: Custom bot message"), botCustomSent);
    }

    @Test
    void reflectionPrefixResolverFailsGracefullyWhenLuckPermsMissing() {
        LuckPermsHook.ReflectionPrefixResolver resolver = new LuckPermsHook.ReflectionPrefixResolver();
        // Since net.luckperms.api is not in runtime classpath, resolver must return "" safely
        String result = resolver.apply(TEST_UUID);
        assertEquals("", result);

        // Default LuckPermsHook constructor returns false and "" safely
        LuckPermsHook defaultHook = new LuckPermsHook();
        assertFalse(defaultHook.isAvailable());
        assertEquals("", defaultHook.getPrefix(TEST_UUID));
    }

    @Test
    void playerWithPrefixFormattingInBotAndWebhook() {
        Player mockPlayer = createMockPlayer("Alex", TEST_UUID, "world");
        LuckPermsHook hook = new LuckPermsHook(() -> true, (player, uuid) -> "[VIP] ");

        assertEquals("[VIP] ", hook.getPrefix(mockPlayer));

        // BOT mode with default template {prefix}**{player}**: {message}
        List<String> botSent = new ArrayList<>();
        ChatListener.relayIfNonBlank(
                "{prefix}**{player}**: {message}",
                mockPlayer.getName(),
                "Hello!",
                Instant.now().toString(),
                "Survival",
                hook.getPrefix(mockPlayer),
                botSent::add);
        assertEquals(List.of("\\[VIP\\] **Alex**: Hello!"), botSent);

        // Webhook username formatting
        String webhookUsername = RelayMessageFormatter.formatWebhookUsername(
                "{prefix}{player}", mockPlayer.getName(), "Survival", hook.getPrefix(mockPlayer));
        assertEquals("[VIP] Alex", webhookUsername);
    }

    @Test
    void playerWithoutPrefixHasNoEmptyPrefixOrExtraSpaces() {
        Player mockPlayer = createMockPlayer("Alex", TEST_UUID, "world");
        LuckPermsHook hook = new LuckPermsHook(() -> true, (player, uuid) -> "");

        assertEquals("", hook.getPrefix(mockPlayer));

        // BOT mode default template must NOT have leading or extra space
        List<String> botSent = new ArrayList<>();
        ChatListener.relayIfNonBlank(
                "{prefix}**{player}**: {message}",
                mockPlayer.getName(),
                "Hello!",
                Instant.now().toString(),
                "Survival",
                hook.getPrefix(mockPlayer),
                botSent::add);
        assertEquals(List.of("**Alex**: Hello!"), botSent);

        // Webhook username formatting must NOT have leading space
        String webhookUsername = RelayMessageFormatter.formatWebhookUsername(
                "{prefix}{player}", mockPlayer.getName(), "Survival", hook.getPrefix(mockPlayer));
        assertEquals("Alex", webhookUsername);
    }

    @Test
    void playerWithContextualPrefixResolvesAccordingToCurrentContext() {
        Player overworldPlayer = createMockPlayer("Alex", TEST_UUID, "world");
        Player netherPlayer = createMockPlayer("Alex", TEST_UUID, "world_nether");

        LuckPermsHook hook = new LuckPermsHook(() -> true, (player, uuid) -> {
            if (player != null && player.getWorld() != null) {
                if ("world_nether".equals(player.getWorld().getName())) {
                    return "[Nether Lord] ";
                } else if ("world".equals(player.getWorld().getName())) {
                    return "[Overworld] ";
                }
            }
            return "[Default] ";
        });

        assertEquals("[Overworld] ", hook.getPrefix(overworldPlayer));
        assertEquals("[Nether Lord] ", hook.getPrefix(netherPlayer));

        List<String> netherSent = new ArrayList<>();
        ChatListener.relayIfNonBlank(
                "{prefix}**{player}**: {message}",
                netherPlayer.getName(),
                "Nether chat",
                Instant.now().toString(),
                "SMP",
                hook.getPrefix(netherPlayer),
                netherSent::add);
        assertEquals(List.of("\\[Nether Lord\\] **Alex**: Nether chat"), netherSent);
    }

    @Test
    void playerWithAmpersandColorCodesInPrefixIsSanitized() {
        Player mockPlayer = createMockPlayer("Alex", TEST_UUID, "world");
        LuckPermsHook hook = new LuckPermsHook(() -> true, (player, uuid) -> "&c[Admin]&r ");

        assertEquals("&c[Admin]&r ", hook.getPrefix(mockPlayer));

        List<String> botSent = new ArrayList<>();
        ChatListener.relayIfNonBlank(
                "{prefix}**{player}**: {message}",
                mockPlayer.getName(),
                "I am admin",
                Instant.now().toString(),
                "SMP",
                hook.getPrefix(mockPlayer),
                botSent::add);
        assertEquals(List.of("\\[Admin\\] **Alex**: I am admin"), botSent);

        String webhookUser = RelayMessageFormatter.formatWebhookUsername(
                "{prefix}{player}", mockPlayer.getName(), "SMP", hook.getPrefix(mockPlayer));
        assertEquals("[Admin] Alex", webhookUser);
    }

    @Test
    void luckPermsAvailableWithSuffix() {
        LuckPermsHook hook = new LuckPermsHook(
                () -> true,
                (player, uuid) -> "[VIP] ",
                (player, uuid) -> " [Master]");

        assertTrue(hook.isAvailable());
        assertEquals("[VIP] ", hook.getPrefix(TEST_UUID));
        assertEquals(" [Master]", hook.getSuffix(TEST_UUID));
    }

    @Test
    void luckPermsAvailableWithoutSuffix() {
        LuckPermsHook hookNull = new LuckPermsHook(
                () -> true,
                (player, uuid) -> "[VIP] ",
                (player, uuid) -> null);
        assertTrue(hookNull.isAvailable());
        assertEquals("", hookNull.getSuffix(TEST_UUID));

        LuckPermsHook hookEmpty = new LuckPermsHook(
                () -> true,
                (player, uuid) -> "[VIP] ",
                (player, uuid) -> "");
        assertTrue(hookEmpty.isAvailable());
        assertEquals("", hookEmpty.getSuffix(TEST_UUID));
    }

    @Test
    void playerWithContextualSuffix() {
        Player mockPlayer = createMockPlayer("Alex", TEST_UUID, "world_nether");
        LuckPermsHook hook = new LuckPermsHook(
                () -> true,
                (player, uuid) -> "",
                (player, uuid) -> {
                    if (player != null && player.getWorld() != null && "world_nether".equals(player.getWorld().getName())) {
                        return " [Hellwalker]";
                    }
                    return " [Mortal]";
                });

        assertEquals(" [Hellwalker]", hook.getSuffix(mockPlayer));
    }

    @Test
    void nullOrUnavailableSuffixData() {
        LuckPermsHook hook = new LuckPermsHook(
                () -> true,
                (player, uuid) -> "",
                (player, uuid) -> {
                    throw new RuntimeException("API error");
                });

        assertEquals("", hook.getSuffix((Player) null));
        assertEquals("", hook.getSuffix((UUID) null));
        assertEquals("", hook.getSuffix(TEST_UUID));
    }

    private static Player createMockPlayer(String name, UUID uuid, String worldName) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getName".equals(methodName)) return name;
                    if ("getUniqueId".equals(methodName)) return uuid;
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
}
