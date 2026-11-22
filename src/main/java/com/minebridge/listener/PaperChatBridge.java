package com.minebridge.listener;

import com.minebridge.MineBridgePlugin;
import com.minebridge.hook.HookUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

/**
 * Dynamically binds to Paper's modern asynchronous chat event (io.papermc.paper.event.player.AsyncChatEvent)
 * when running on a Paper server, falling back gracefully on Spigot servers without requiring Paper at compile time.
 */
public final class PaperChatBridge {

    private static final String PAPER_ASYNC_CHAT_EVENT = "io.papermc.paper.event.player.AsyncChatEvent";
    private static volatile Boolean paperAvailable = null;
    private static final Object SERIALIZER_LOCK = new Object();
    private static volatile boolean serializerResolved = false;
    private static Object cachedSerializer = null;
    private static Method cachedSerializeMethod = null;

    private PaperChatBridge() {
    }

    public static boolean isPaperAvailable() {
        if (paperAvailable == null) {
            try {
                Class.forName(PAPER_ASYNC_CHAT_EVENT);
                paperAvailable = true;
            } catch (Throwable t) {
                HookUtil.checkFatal(t);
                paperAvailable = false;
            }
        }
        return paperAvailable;
    }

    public static void setPaperAvailableForTesting(Boolean available) {
        paperAvailable = available;
    }

    @SuppressWarnings("unchecked")
    public static boolean registerIfAvailable(MineBridgePlugin plugin, ChatListener chatListener) {
        if (!isPaperAvailable() || plugin == null || chatListener == null) {
            return false;
        }

        try {
            Class<? extends Event> eventClass =
                    (Class<? extends Event>) Class.forName(PAPER_ASYNC_CHAT_EVENT);

            Method getPlayerMethod = eventClass.getMethod("getPlayer");
            Method messageMethod = eventClass.getMethod("message");
            Method viewersMethod = eventClass.getMethod("viewers");
            Method isCancelledMethod = eventClass.getMethod("isCancelled");

            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    boolean cancelled = (boolean) isCancelledMethod.invoke(event);
                    if (cancelled) {
                        return;
                    }

                    Player player = (Player) getPlayerMethod.invoke(event);
                    Object component = messageMethod.invoke(event);
                    Set<?> viewers = (Set<?>) viewersMethod.invoke(event);

                    String message = extractPlainText(component);
                    if (message == null || message.isBlank()) {
                        return;
                    }

                    Collection<? extends Player> onlinePlayers = player != null && player.getServer() != null
                            ? player.getServer().getOnlinePlayers()
                            : Bukkit.getOnlinePlayers();

                    com.minebridge.config.ConfigManager.CrossWorldSettings crossWorld = chatListener.getConfig() != null
                            && chatListener.getConfig().currentSettings() != null
                            ? chatListener.getConfig().currentSettings().crossWorld()
                            : com.minebridge.config.ConfigManager.defaultCrossWorldSettings();

                    if (!ChatClassifier.isPaperGlobalChat(player, message, cancelled, viewers, onlinePlayers, crossWorld)) {
                        return;
                    }

                    chatListener.handlePaperChat(player, message);
                } catch (Throwable t) {
                    HookUtil.checkFatal(t);
                    // Fail closed if classification or extraction fails
                }
            };

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    chatListener,
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true // ignoreCancelled
            );
            return true;
        } catch (Throwable t) {
            HookUtil.checkFatal(t);
            return false;
        }
    }

    public static String extractPlainText(Object component) {
        if (component == null) {
            return null;
        }

        // 1. Try cached PlainTextComponentSerializer.plainText().serialize(...)
        if (!serializerResolved) {
            synchronized (SERIALIZER_LOCK) {
                if (!serializerResolved) {
                    try {
                        Class<?> serializerClass = Class.forName(
                                "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
                        Method plainTextMethod = serializerClass.getMethod("plainText");
                        Object serializer = plainTextMethod.invoke(null);
                        Class<?> compClass = Class.forName("net.kyori.adventure.text.Component");
                        Method serializeMethod = serializerClass.getMethod("serialize", compClass);
                        cachedSerializer = serializer;
                        cachedSerializeMethod = serializeMethod;
                    } catch (Throwable ignored) {
                        HookUtil.checkFatal(ignored);
                    } finally {
                        serializerResolved = true;
                    }
                }
            }
        }

        if (cachedSerializeMethod != null && cachedSerializer != null) {
            try {
                return (String) cachedSerializeMethod.invoke(cachedSerializer, component);
            } catch (Throwable ignored) {
                HookUtil.checkFatal(ignored);
            }
        }

        // 2. Try TextComponent.content()
        try {
            Method contentMethod = component.getClass().getMethod("content");
            return (String) contentMethod.invoke(component);
        } catch (Throwable ignored) {
            HookUtil.checkFatal(ignored);
        }

        return null;
    }

    public static void resetSerializerCacheForTesting() {
        synchronized (SERIALIZER_LOCK) {
            serializerResolved = false;
            cachedSerializer = null;
            cachedSerializeMethod = null;
        }
    }
}
