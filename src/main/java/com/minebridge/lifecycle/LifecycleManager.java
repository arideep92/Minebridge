package com.minebridge.lifecycle;

import com.minebridge.command.MineBridgeStatus;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.IntegrationMode;
import com.minebridge.discord.ReloadResult;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.ChatMetadataService;
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
    private final ChatMetadataService chatMetadataService;
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
    private final AtomicBoolean serverStartedNotificationSent = new AtomicBoolean(false);
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
                new ChatMetadataService(luckPermsHook, null, null, null, null),
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
            ChatMetadataService chatMetadataService,
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
                chatMetadataService,
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
        this(
                configManager,
                candidateSupplier,
                new ChatMetadataService(luckPermsHook, null, null, null, null),
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
            ChatMetadataService chatMetadataService,
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
                        } catch (Throwable t) {
                            com.minebridge.hook.HookUtil.checkFatal(t);
                            warningLogger.accept("Error loading configuration candidate: "
                                    + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
                            return new ConfigManager.Candidate(configManager.currentSettings(), List.of("Failed to load configuration: " + t.getMessage()));
                        }
                    }
                    return null;
                };
        this.warningLogger = warningLogger != null ? warningLogger : msg -> {};
        this.uptimeSupplier = uptimeSupplier != null ? uptimeSupplier : () -> 0L;
        this.chatMetadataService = chatMetadataService != null ? chatMetadataService : new ChatMetadataService(this.warningLogger);
        this.luckPermsHook = this.chatMetadataService.getLuckPermsHook();
        this.discordServiceFactory = discordServiceFactory;
        this.webhookServiceFactory = webhookServiceFactory;
        this.asyncRunner = asyncRunner != null ? asyncRunner : Runnable::run;
        this.botSchedulerStarter = botSchedulerStarter != null ? botSchedulerStarter : () -> {};
        this.botSchedulerCanceler = botSchedulerCanceler != null ? botSchedulerCanceler : () -> {};
        this.infoLogger = infoLogger != null ? infoLogger : msg -> {};
    }

    public synchronized void start() {
        if (stopping.get()) {
            return;
        }

        ConfigManager.Candidate initial = candidateSupplier != null ? candidateSupplier.get() : null;
        if (initial == null) {
            warningLogger.accept("MineBridge configuration is null.");
            if (eventNotifier == null) {
                eventNotifier = new DiscordEventNotifier(discordService, webhookService, configManager);
            }
            if (chatListener == null) {
                chatListener = new ChatListener(null, discordService, webhookService, configManager, chatMetadataService);
            }
            return;
        }
        if (initial.valid() && initial.settings() != null) {
            if (configManager != null) {
                configManager.commit(initial);
            }
            if (initial.settings().mode() == IntegrationMode.WEBHOOK) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.start();
                discordService = null;
                infoLogger.accept("MineBridge started in WEBHOOK mode.");
            } else if (initial.settings().mode() == IntegrationMode.HYBRID) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.start();
                discordService = discordServiceFactory.apply(configManager);
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (result != null && result.successful()) {
                        if (initial.settings().hybrid().eventDelivery() == IntegrationMode.BOT) {
                            if (serverStartedNotificationSent.compareAndSet(false, true)) {
                                if (eventNotifier != null) {
                                    eventNotifier.sendServerStart();
                                }
                            }
                        }
                    }
                }));
                botSchedulerStarter.run();
                infoLogger.accept("MineBridge started in HYBRID mode.");
            } else {
                discordService = discordServiceFactory.apply(configManager);
                webhookService = null;
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (result != null && result.successful()) {
                        if (serverStartedNotificationSent.compareAndSet(false, true)) {
                            if (eventNotifier != null) {
                                eventNotifier.sendServerStart();
                            }
                        }
                    }
                }));
                botSchedulerStarter.run();
                infoLogger.accept("MineBridge started in BOT mode.");
            }
        } else {
            initial.errors().forEach(err -> warningLogger.accept("MineBridge configuration: " + err));
            warningLogger.accept("MineBridge started without a valid Discord configuration (e.g. missing Discord token). Discord integration is disabled until a valid configuration is provided via /mb reload.");
            IntegrationMode mode = initial.settings() != null ? initial.settings().mode()
                    : (configManager != null && configManager.currentSettings() != null ? configManager.currentSettings().mode() : IntegrationMode.BOT);
            if (mode == IntegrationMode.WEBHOOK) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.markConfigurationInvalid();
            } else if (mode == IntegrationMode.HYBRID) {
                webhookService = webhookServiceFactory.apply(configManager);
                webhookService.markConfigurationInvalid();
                discordService = discordServiceFactory.apply(configManager);
                discordService.markConfigurationInvalid();
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
            chatListener = new ChatListener(null, discordService, webhookService, configManager, chatMetadataService);
        } else {
            chatListener.updateServices(discordService, webhookService);
        }

        if (initial.valid() && initial.settings() != null) {
            if (initial.settings().mode() == IntegrationMode.WEBHOOK
                    || (initial.settings().mode() == IntegrationMode.HYBRID
                            && initial.settings().hybrid().eventDelivery() == IntegrationMode.WEBHOOK)) {
                if (serverStartedNotificationSent.compareAndSet(false, true)) {
                    if (eventNotifier != null) {
                        eventNotifier.sendServerStart();
                    }
                }
            }
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
        if (candidate == null || !candidate.valid() || candidate.settings() == null) {
            List<String> errors = candidate != null && !candidate.errors().isEmpty()
                    ? candidate.errors()
                    : List.of("Configuration is null");
            errors.forEach(err -> warningLogger.accept("MineBridge configuration: " + err));
            if (discordService != null) {
                long req = discordService.beginReloadRequest();
                discordService.finishReloadRequest(req, "INVALID_CONFIGURATION");
            }
            if (webhookService != null) {
                webhookService.markConfigurationInvalid();
            }
            if (callback != null) {
                callback.accept(new ReloadResult(false, String.join(" ", errors)));
            }
            return;
        }

        IntegrationMode currentMode;
        if (webhookService != null && discordService != null) {
            currentMode = IntegrationMode.HYBRID;
        } else if (webhookService != null && discordService == null) {
            currentMode = IntegrationMode.WEBHOOK;
        } else if (discordService != null && webhookService == null) {
            currentMode = IntegrationMode.BOT;
        } else if (configManager != null && configManager.currentSettings() != null) {
            currentMode = configManager.currentSettings().mode();
        } else {
            currentMode = IntegrationMode.BOT;
        }
        IntegrationMode targetMode = candidate.settings().mode();

        if (currentMode == IntegrationMode.BOT && targetMode == IntegrationMode.BOT) {
            botSchedulerStarter.run();
            if (discordService != null && !discordService.isStopping()) {
                long requestId = discordService.beginReloadRequest();
                asyncRunner.accept(() -> discordService.reload(requestId, candidate, result -> {
                    if (callback != null) {
                        callback.accept(result);
                    }
                }));
            } else {
                if (discordService != null) {
                    discordService.stop();
                }
                if (configManager != null) {
                    configManager.commit(candidate);
                }
                discordService = discordServiceFactory.apply(configManager);
                eventNotifier.updateServices(discordService, null);
                chatListener.updateServices(discordService, null);
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (callback != null) {
                        callback.accept(result);
                    }
                }));
            }
            return;
        }

        if (currentMode == IntegrationMode.WEBHOOK && targetMode == IntegrationMode.WEBHOOK) {
            if (configManager != null) {
                configManager.commit(candidate);
            }
            if (webhookService == null || webhookService.isStopping()) {
                webhookService = webhookServiceFactory.apply(configManager);
            }
            webhookService.start();
            eventNotifier.updateServices(null, webhookService);
            chatListener.updateServices(null, webhookService);
            if (callback != null) {
                callback.accept(new ReloadResult(true, "Discord Webhook configuration reloaded successfully."));
            }
            return;
        }

        if (currentMode == IntegrationMode.HYBRID && targetMode == IntegrationMode.HYBRID) {
            if (configManager != null) {
                configManager.commit(candidate);
            }
            if (webhookService == null || webhookService.isStopping()) {
                webhookService = webhookServiceFactory.apply(configManager);
            }
            webhookService.start();
            botSchedulerStarter.run();
            if (discordService != null && !discordService.isStopping()) {
                long requestId = discordService.beginReloadRequest();
                asyncRunner.accept(() -> discordService.reload(requestId, candidate, result -> {
                    eventNotifier.updateServices(discordService, webhookService);
                    chatListener.updateServices(discordService, webhookService);
                    if (callback != null) {
                        callback.accept(result);
                    }
                }));
            } else {
                if (discordService != null) {
                    discordService.stop();
                }
                discordService = discordServiceFactory.apply(configManager);
                eventNotifier.updateServices(discordService, webhookService);
                chatListener.updateServices(discordService, webhookService);
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (callback != null) {
                        callback.accept(result);
                    }
                }));
            }
            return;
        }

        if (currentMode == IntegrationMode.BOT && targetMode == IntegrationMode.HYBRID) {
            if (configManager != null) {
                configManager.commit(candidate);
            }
            if (webhookService == null || webhookService.isStopping()) {
                webhookService = webhookServiceFactory.apply(configManager);
            }
            webhookService.start();
            botSchedulerStarter.run();
            if (discordService != null && !discordService.isStopping()) {
                long requestId = discordService.beginReloadRequest();
                asyncRunner.accept(() -> discordService.reload(requestId, candidate, result -> {
                    eventNotifier.updateServices(discordService, webhookService);
                    chatListener.updateServices(discordService, webhookService);
                    infoLogger.accept("Switched integration mode from BOT to HYBRID.");
                    if (callback != null) {
                        callback.accept(new ReloadResult(result != null && result.successful(), "Switched integration mode from BOT to HYBRID."));
                    }
                }));
            } else {
                if (discordService != null) {
                    discordService.stop();
                }
                discordService = discordServiceFactory.apply(configManager);
                eventNotifier.updateServices(discordService, webhookService);
                chatListener.updateServices(discordService, webhookService);
                infoLogger.accept("Switched integration mode from BOT to HYBRID.");
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (callback != null) {
                        callback.accept(new ReloadResult(result != null && result.successful(), "Switched integration mode from BOT to HYBRID."));
                    }
                }));
            }
            return;
        }

        if (currentMode == IntegrationMode.WEBHOOK && targetMode == IntegrationMode.HYBRID) {
            if (configManager != null) {
                configManager.commit(candidate);
            }
            if (webhookService == null || webhookService.isStopping()) {
                webhookService = webhookServiceFactory.apply(configManager);
            }
            webhookService.start();
            if (discordService != null) {
                discordService.stop();
                discordService = null;
            }
            discordService = discordServiceFactory.apply(configManager);
            eventNotifier.updateServices(discordService, webhookService);
            chatListener.updateServices(discordService, webhookService);
            botSchedulerStarter.run();
            infoLogger.accept("Switched integration mode from WEBHOOK to HYBRID.");
            asyncRunner.accept(() -> discordService.start(result -> {
                if (callback != null) {
                    callback.accept(new ReloadResult(result != null && result.successful(), "Switched integration mode from WEBHOOK to HYBRID."));
                }
            }));
            return;
        }

        if (currentMode == IntegrationMode.HYBRID && targetMode == IntegrationMode.BOT) {
            if (webhookService != null) {
                webhookService.stop();
                webhookService = null;
            }
            if (configManager != null) {
                configManager.commit(candidate);
            }
            botSchedulerStarter.run();
            if (discordService != null && !discordService.isStopping()) {
                long requestId = discordService.beginReloadRequest();
                asyncRunner.accept(() -> discordService.reload(requestId, candidate, result -> {
                    eventNotifier.updateServices(discordService, null);
                    chatListener.updateServices(discordService, null);
                    infoLogger.accept("Switched integration mode from HYBRID to BOT.");
                    if (callback != null) {
                        callback.accept(new ReloadResult(result != null && result.successful(), "Switched integration mode from HYBRID to BOT."));
                    }
                }));
            } else {
                if (discordService != null) {
                    discordService.stop();
                }
                discordService = discordServiceFactory.apply(configManager);
                eventNotifier.updateServices(discordService, null);
                chatListener.updateServices(discordService, null);
                infoLogger.accept("Switched integration mode from HYBRID to BOT.");
                asyncRunner.accept(() -> discordService.start(result -> {
                    if (callback != null) {
                        callback.accept(new ReloadResult(result != null && result.successful(), "Switched integration mode from HYBRID to BOT."));
                    }
                }));
            }
            return;
        }

        if (currentMode == IntegrationMode.HYBRID && targetMode == IntegrationMode.WEBHOOK) {
            botSchedulerCanceler.run();
            if (discordService != null) {
                discordService.stop();
                discordService = null;
            }
            if (configManager != null) {
                configManager.commit(candidate);
            }
            if (webhookService == null || webhookService.isStopping()) {
                webhookService = webhookServiceFactory.apply(configManager);
            }
            webhookService.start();
            eventNotifier.updateServices(null, webhookService);
            chatListener.updateServices(null, webhookService);
            infoLogger.accept("Switched integration mode from HYBRID to WEBHOOK.");
            if (callback != null) {
                callback.accept(new ReloadResult(true, "Switched integration mode from HYBRID to WEBHOOK."));
            }
            return;
        }

        if (currentMode == IntegrationMode.BOT && targetMode == IntegrationMode.WEBHOOK) {
            botSchedulerCanceler.run();
            if (discordService != null) {
                discordService.stop();
                discordService = null;
            }
            if (webhookService != null) {
                webhookService.stop();
                webhookService = null;
            }
            if (configManager != null) {
                configManager.commit(candidate);
            }
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
            if (discordService != null) {
                discordService.stop();
                discordService = null;
            }
            if (configManager != null) {
                configManager.commit(candidate);
            }
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

        if (eventNotifier != null) {
            eventNotifier.sendServerStop();
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
            if (chatListener.getSkinAvatarService() != null) {
                chatListener.getSkinAvatarService().clear();
            }
        }

        infoLogger.accept("MineBridge integration stopped.");
    }

    public MineBridgeStatus getStatus() {
        ConfigManager.Settings settings = configManager != null ? configManager.currentSettings() : null;
        IntegrationMode mode = settings != null ? settings.mode() : IntegrationMode.BOT;
        boolean luckPermsAvailable = false;
        try {
            luckPermsAvailable = luckPermsHook != null && luckPermsHook.isAvailable();
        } catch (Throwable t) {
            com.minebridge.hook.HookUtil.checkFatal(t);
            warningLogger.accept("Optional integration LuckPerms failed during isAvailable: "
                    + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
        }
        long uptime = uptimeSupplier.getAsLong();
        boolean chatEnabled = configManager != null && configManager.chatEnabled();
        boolean eventsEnabled = configManager != null && configManager.eventsEnabled();

        if (mode == IntegrationMode.HYBRID) {
            MineBridgeStatus botStatus = discordService != null ? discordService.getStatus(uptime) : null;
            MineBridgeStatus webStatus = webhookService != null ? webhookService.getStatus(uptime, chatEnabled, eventsEnabled, luckPermsAvailable) : null;

            String botState = botStatus != null ? botStatus.discordState() : "NOT_STARTED";
            boolean channelAvailable = botStatus != null && botStatus.channelAvailable();
            String webhookState = webStatus != null ? webStatus.discordState() : "NOT_STARTED";
            boolean webhookValid = webStatus != null && webStatus.channelAvailable();

            int queueSize = (webStatus != null ? webStatus.outgoingQueueSize() : 0) + (botStatus != null ? botStatus.outgoingQueueSize() : 0);
            int queueCap = webStatus != null ? webStatus.outgoingQueueCapacity() : (botStatus != null ? botStatus.outgoingQueueCapacity() : 100);
            long queueDropped = (webStatus != null ? webStatus.outgoingDropped() : 0L) + (botStatus != null ? botStatus.outgoingDropped() : 0L);

            return new MineBridgeStatus(
                    IntegrationMode.HYBRID,
                    botState,
                    channelAvailable,
                    queueSize,
                    queueCap,
                    uptime,
                    chatEnabled,
                    eventsEnabled,
                    queueDropped,
                    luckPermsAvailable,
                    webhookState,
                    webhookValid);
        } else if (mode == IntegrationMode.WEBHOOK) {
            if (webhookService != null) {
                return webhookService.getStatus(
                        uptime,
                        chatEnabled,
                        eventsEnabled,
                        luckPermsAvailable);
            }
            return new MineBridgeStatus(
                    IntegrationMode.WEBHOOK,
                    "NOT_STARTED",
                    false,
                    0,
                    100,
                    uptime,
                    chatEnabled,
                    eventsEnabled,
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
                    chatEnabled,
                    eventsEnabled,
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

    public ChatMetadataService getChatMetadataService() {
        return chatMetadataService;
    }

    public boolean isStopping() {
        return stopping.get();
    }
}
