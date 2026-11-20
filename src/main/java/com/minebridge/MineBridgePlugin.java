package com.minebridge;

import com.minebridge.command.MineBridgeCommand;
import com.minebridge.command.MineBridgeStatus;
import com.minebridge.achievement.AdvancementAnnouncementManager;
import com.minebridge.achievement.AdvancementListener;
import com.minebridge.config.ConfigManager;
import com.minebridge.death.DeathListener;
import com.minebridge.death.DeathMessageManager;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import com.minebridge.lifecycle.LifecycleManager;
import com.minebridge.listener.ChatListener;
import com.minebridge.listener.PlayerEventListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MineBridgePlugin extends JavaPlugin {

    private final long enabledAtNanos = System.nanoTime();
    private ConfigManager configManager;
    private LuckPermsHook luckPermsHook;
    private LifecycleManager lifecycleManager;
    private BukkitTask outgoingTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        luckPermsHook = new LuckPermsHook();

        lifecycleManager = new LifecycleManager(
                configManager,
                luckPermsHook,
                cfg -> new DiscordService(this, cfg),
                cfg -> new WebhookService(cfg),
                task -> {
                    try {
                        getServer().getScheduler().runTaskAsynchronously(this, task);
                    } catch (IllegalPluginAccessException ignored) {}
                },
                () -> {
                    if (outgoingTask != null) {
                        outgoingTask.cancel();
                    }
                    try {
                        outgoingTask = getServer().getScheduler().runTaskTimerAsynchronously(
                                this, () -> {
                                    DiscordService svc = lifecycleManager != null
                                            ? lifecycleManager.getDiscordService() : null;
                                    if (svc != null) {
                                        svc.processOutgoing();
                                    }
                                }, 20L, 5L);
                    } catch (IllegalPluginAccessException ignored) {}
                },
                () -> {
                    if (outgoingTask != null) {
                        outgoingTask.cancel();
                        outgoingTask = null;
                    }
                },
                msg -> getLogger().info(msg),
                msg -> getLogger().warning(msg),
                this::getUptimeMillis
        );

        lifecycleManager.start();

        ChatListener chatListener = lifecycleManager.getChatListener();
        DiscordEventNotifier eventNotifier = lifecycleManager.getEventNotifier();

        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(
                new PlayerEventListener(eventNotifier, configManager), this);
        getServer().getPluginManager().registerEvents(
                new DeathListener(new DeathMessageManager(this, configManager, eventNotifier)),
                this);
        getServer().getPluginManager().registerEvents(
                new AdvancementListener(new AdvancementAnnouncementManager(
                        this, configManager, eventNotifier)),
                this);

        PluginCommand command = getCommand("minebridge");
        if (command != null) {
            MineBridgeCommand handler = new MineBridgeCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("Command 'minebridge' is missing from plugin.yml!");
        }

        getLogger().info("MineBridge enabled.");
    }

    @Override
    public void onDisable() {
        if (lifecycleManager != null) {
            lifecycleManager.shutdown();
        }
    }

    public void reloadMineBridge(Consumer<ReloadResult> callback) {
        if (lifecycleManager != null) {
            lifecycleManager.reload(result -> deliverReloadResult(callback, result));
        } else if (callback != null) {
            deliverReloadResult(callback, new ReloadResult(false, "LifecycleManager is not initialized."));
        }
    }

    private void deliverReloadResult(
            Consumer<ReloadResult> callback, ReloadResult result) {
        if (!isEnabled() || callback == null) {
            return;
        }
        if (getServer() == null || getServer().getScheduler() == null) {
            callback.accept(result);
            return;
        }
        try {
            getServer().getScheduler().runTask(this, () -> {
                if (isEnabled()) {
                    callback.accept(result);
                }
            });
        } catch (IllegalPluginAccessException exception) {
            if (isEnabled()) {
                getLogger().warning(
                        "Could not deliver the Discord reload result because the server scheduler is unavailable.");
            }
        }
    }

    public MineBridgeStatus getStatus() {
        return lifecycleManager != null ? lifecycleManager.getStatus() : null;
    }

    public long getUptimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - enabledAtNanos);
    }

    public DiscordService getDiscordService() {
        return lifecycleManager != null ? lifecycleManager.getDiscordService() : null;
    }

    public WebhookService getWebhookService() {
        return lifecycleManager != null ? lifecycleManager.getWebhookService() : null;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
}
