package com.minebridge.lifecycle;

import com.minebridge.command.MineBridgeStatus;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.LuckPermsHook;
import com.minebridge.listener.ChatListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Manages the lifecycle of MineBridge integrations (BOT and WEBHOOK modes).
 * Handles startup, shutdown, mode transitions on reload, and mode-aware status reporting.
 */
public final class LifecycleManager {

    private final ConfigManager configManager;
    private final Supplier<ConfigManager.Candidate> candidateSupplier;
    private final LuckPermsHook luckPermsHook;
    private final Function<ConfigManager, DiscordService> discordServiceFactory;
    private final Function<ConfigManager, WebhookService> webhookServiceFactory;
    private final Consumer<Runnable> asyncRunner;
    private final Runnable botSchedulerStarter;
    private final Runnable botSchedulerCanceler;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final LongSupplier uptimeSupplier;

    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile DiscordService discordService;
    private volatile WebhookService webhookService;
    private volatile DiscordEventNotifier eventNotifier;
    private volatile ChatListener chatListener;

    public LifecycleManager(
            ConfigManager configManager,
            LuckPermsHook luckPermsHook,
            Function<ConfigManager, DiscordService> discordServiceFactory,
            Function<ConfigManager, WebhookService> webhookServiceFactory,
            Consumer<Runnable> asyncRunner,
            Runnable botSchedulerStarter,
            Runnable botSchedulerCanceler,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger,
            LongSupplier uptimeSupplier) {
        this(
                configManager,
                null,
                luckPermsHook,
                discordServiceFactory,
                webhookServiceFactory,
                asyncRunner,
                botSchedulerStarter,
                botSchedulerCanceler,
                infoLogger,
                warningLogger,
                uptimeSupplier);
    }

    public LifecycleManager(
            ConfigManager configManager,
            Supplier<ConfigManager.Candidate> candidateSupplier,
            LuckPermsHook luckPermsHook,
            Function<ConfigManager, DiscordService> discordServiceFactory,
            Function<ConfigManager, WebhookService> webhookServiceFactory,
            Consumer<Runnable> asyncRunner,
            Runnable botSchedulerStarter,
            Runnable botSchedulerCanceler,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger,
            LongSupplier uptimeSupplier) {
        this.configManager = configManager;
        this.candidateSupplier = candidateSupplier != null
                ? candidateSupplier
                : () -> {
                    if (configManager != null) {
                        try {
                            return configManager.loadCandidate();
                        } catch (Throwable ignored) {
                            return new ConfigManager.Candidate(configManager.currentSettings(), List.of());
                        }
                    }
                    return null;
                };
        this.luckPermsHook = luckPermsHook != null ? luckPermsHook : new LuckPermsHook();
        this.discordServiceFactory = discordServiceFactory;
        this.webhookServiceFactory = webhookServiceFactory;
        this.asyncRunner = asyncRunner != null ? asyncRunner : Runnable::run;
        this.botSchedulerStarter = botSchedulerStarter != null ? botSchedulerStarter : () -> {};
        this.botSchedulerCanceler = botSchedulerCanceler != null ? botSchedulerCanceler : () -> {};
        this.infoLogger = infoLogger != null ? infoLogger : msg -> {};
        this.warningLogger = warningLogger != null ? warningLogger : msg -> {};
        this.uptimeSupplier = uptimeSupplier != null ? uptimeSupplier : () -> 0L;
    }

    public synchronized void start() {
        if (stopping.get()) {
            return;
        }

        ConfigManager.Candidate initial = candidateSupplier != null ? candidateSupplier.get() : null;
        if (initial == null) {
            return;
        }
        if (initial.valid()) {
            configManager.commit(initial);
            if (initial.settings().mode() == IntegrationMode.WEBHOOK) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.start();
                discordService = null;
                infoLogger.accept("MineBridge started in WEBHOOK mode.");
            } else {
                discordService = discordServiceFactory.apply(configManager);
                webhookService = null;
                asyncRunner.accept(() -> discordService.start());
                botSchedulerStarter.run();
                infoLogger.accept("MineBridge started in BOT mode.");
            }
        } else {
            initial.errors().forEach(err -> warningLogger.accept("MineBridge configuration: " + err));
            if (initial.settings().mode() == IntegrationMode.WEBHOOK) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.markConfigurationInvalid();
            } else {
                discordService = discordServiceFactory.apply(configManager);
                discordService.markConfigurationInvalid();
            }
        }

        if (eventNotifier == null) {
            eventNotifier = new DiscordEventNotifier(discordService, webhookService, configManager);
        } else {
            eventNotifier.updateServices(discordService, webhookService);
        }

        if (chatListener == null) {
            chatListener = new ChatListener(null, discordService, webhookService, configManager, luckPermsHook);
        } else {
            chatListener.updateServices(discordService, webhookService);
        }
    }

    public synchronized void reload(Consumer<ReloadResult> callback) {
        if (stopping.get()) {
            if (callback != null) {
                callback.accept(new ReloadResult(false, "MineBridge is shutting down."));
            }
            return;
        }

        ConfigManager.Candidate candidate = candidateSupplier != null ? candidateSupplier.get() : null;
        if (candidate == null || !candidate.valid()) {
            List<String> errors = candidate != null ? candidate.errors() : List.of("Configuration is null");
            errors.forEach(err -> warningLogger.accept("MineBridge configuration: " + err));
            if (discordService != null) {
                long req = discordService.beginReloadRequest();
                discordService.finishReloadRequest(req, "INVALID_CONFIGURATION");
            }
            if (callback != null) {
                callback.accept(new ReloadResult(false, String.join(" ", errors)));
            }
            return;
        }

        IntegrationMode currentMode = configManager.currentSettings().mode();
        IntegrationMode targetMode = candidate.settings().mode();

        if (currentMode == IntegrationMode.BOT && targetMode == IntegrationMode.BOT) {
            if (discordService != null) {
                long requestId = discordService.beginReloadRequest();
                asyncRunner.accept(() -> discordService.reload(requestId, candidate, result -> {
                    if (callback != null) {
                        callback.accept(result);
                    }
                }));
            } else {
                configManager.commit(candidate);
                discordService = discordServiceFactory.apply(configManager);
                eventNotifier.updateServices(discordService, null);
                chatListener.updateServices(discordService, null);
                botSchedulerStarter.run();
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (callback != null) {
                        callback.accept(result);
                    }
                }));
            }
            return;
        }

        if (currentMode == IntegrationMode.WEBHOOK && targetMode == IntegrationMode.WEBHOOK) {
            configManager.commit(candidate);
            if (webhookService == null) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.start();
            }
            eventNotifier.updateServices(null, webhookService);
            chatListener.updateServices(null, webhookService);
            if (callback != null) {
                callback.accept(new ReloadResult(true, "Discord Webhook configuration reloaded successfully."));
            }
            return;
        }

        if (currentMode == IntegrationMode.BOT && targetMode == IntegrationMode.WEBHOOK) {
            botSchedulerCanceler.run();
            if (discordService != null) {
                discordService.stop();
                discordService = null;
            }
            configManager.commit(candidate);
            webhookService = webhookServiceFactory.apply(configManager);
            webhookService.start();
            eventNotifier.updateServices(null, webhookService);
            chatListener.updateServices(null, webhookService);
            infoLogger.accept("Switched integration mode from BOT to WEBHOOK.");
            if (callback != null) {
                callback.accept(new ReloadResult(true, "Switched integration mode from BOT to WEBHOOK."));
            }
            return;
        }

        if (currentMode == IntegrationMode.WEBHOOK && targetMode == IntegrationMode.BOT) {
            if (webhookService != null) {
                webhookService.stop();
                webhookService = null;
            }
            configManager.commit(candidate);
            discordService = discordServiceFactory.apply(configManager);
            eventNotifier.updateServices(discordService, null);
            chatListener.updateServices(discordService, null);
            botSchedulerStarter.run();
            infoLogger.accept("Switched integration mode from WEBHOOK to BOT.");
            asyncRunner.accept(() -> discordService.start(result -> {
                if (callback != null) {
                    callback.accept(result);
                }
            }));
        }
    }

    public synchronized void shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        botSchedulerCanceler.run();

        if (discordService != null) {
            discordService.stop();
            discordService = null;
        }
        if (webhookService != null) {
            webhookService.stop();
            webhookService = null;
        }

        if (eventNotifier != null) {
            eventNotifier.updateServices(null, null);
        }
        if (chatListener != null) {
            chatListener.updateServices(null, null);
        }

        infoLogger.accept("MineBridge integration stopped.");
    }

    public MineBridgeStatus getStatus() {
        ConfigManager.Settings settings = configManager.currentSettings();
        IntegrationMode mode = settings.mode();
        boolean luckPermsAvailable = luckPermsHook != null && luckPermsHook.isAvailable();
        long uptime = uptimeSupplier.getAsLong();

        if (mode == IntegrationMode.WEBHOOK) {
            if (webhookService != null) {
                return webhookService.getStatus(
                        uptime,
                        configManager.chatEnabled(),
                        configManager.eventsEnabled(),
                        luckPermsAvailable);
            }
            return new MineBridgeStatus(
                    IntegrationMode.WEBHOOK,
                    "NOT_STARTED",
                    false,
                    0,
                    100,
                    uptime,
                    configManager.chatEnabled(),
                    configManager.eventsEnabled(),
                    0L,
                    luckPermsAvailable);
        } else {
            if (discordService != null) {
                MineBridgeStatus botStatus = discordService.getStatus(uptime);
                return new MineBridgeStatus(
                        IntegrationMode.BOT,
                        botStatus.discordState(),
                        botStatus.channelAvailable(),
                        botStatus.outgoingQueueSize(),
                        botStatus.outgoingQueueCapacity(),
                        uptime,
                        botStatus.chatRelayEnabled(),
                        botStatus.joinLeaveNotificationsEnabled(),
                        botStatus.outgoingDropped(),
                        luckPermsAvailable);
            }
            return new MineBridgeStatus(
                    IntegrationMode.BOT,
                    "NOT_STARTED",
                    false,
                    0,
                    100,
                    uptime,
                    configManager.chatEnabled(),
                    configManager.eventsEnabled(),
                    0L,
                    luckPermsAvailable);
        }
    }

    public DiscordService getDiscordService() {
        return discordService;
    }

    public WebhookService getWebhookService() {
        return webhookService;
    }

    public DiscordEventNotifier getEventNotifier() {
        return eventNotifier;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public boolean isStopping() {
        return stopping.get();
    }
}
