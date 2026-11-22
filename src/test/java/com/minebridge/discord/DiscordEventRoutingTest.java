package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.config.ConfigManager;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import com.minebridge.listener.ChatListener;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.*;
import okio.Buffer;
import okio.Timeout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEventRoutingTest {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final String WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/test-token";

    private static class RecordingCallFactory implements Call.Factory {
        final List<Request> recordedRequests = new ArrayList<>();
        final List<String> recordedBodies = new ArrayList<>();

        @Override
        public Call newCall(Request request) {
            recordedRequests.add(request);
            try {
                if (request.body() != null) {
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    recordedBodies.add(buffer.readUtf8());
                } else {
                    recordedBodies.add("");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return new Call() {
                @Override
                public Request request() {
                    return request;
                }

                @Override
                public Response execute() {
                    return new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(204)
                            .message("No Content")
                            .body(ResponseBody.create("", JSON_MEDIA))
                            .build();
                }

                @Override
                public void enqueue(Callback responseCallback) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void cancel() {}

                @Override
                public boolean isExecuted() {
                    return true;
                }

                @Override
                public boolean isCanceled() {
                    return false;
                }

                @Override
                public Timeout timeout() {
                    return Timeout.NONE;
                }

                @Override
                public Call clone() {
                    return this;
                }
            };
        }
    }

    private ConfigManager createConfig(
            String mode,
            String webhookUrl,
            String webhookUsername,
            String webhookAvatar,
            String serverName,
            String footerText,
            String footerIcon) {
        String yaml = """
                discord:
                  mode: "%s"
                  bot-token: "synthetic-test-bot-token-12345"
                  channel-id: "123456789012345678"
                  webhook:
                    url: "%s"
                    username: "%s"
                    avatar-url: "%s"
                  embed:
                    footer:
                      text: "%s"
                      icon-url: "%s"
                server:
                  name: "%s"
                """.formatted(
                mode,
                webhookUrl,
                webhookUsername,
                webhookAvatar,
                footerText,
                footerIcon,
                serverName);

        YamlConfiguration config = new YamlConfiguration();
        try {
            try (var stream = getClass().getResourceAsStream("/config.yml")) {
                if (stream != null) {
                    YamlConfiguration defaults = new YamlConfiguration();
                    defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    config.setDefaults(defaults);
                }
            }
            config.load(new StringReader(yaml));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Candidate candidate = manager.candidateFrom(config, null);
        assertTrue(candidate.valid(), "Expected valid configuration: " + candidate.errors());
        manager.commit(candidate);
        return manager;
    }

    @Test
    void routesAllFiveEventCategoriesToWebhookInWebhookMode() {
        RecordingCallFactory recordingFactory = new RecordingCallFactory();
        ConfigManager config = createConfig(
                "WEBHOOK",
                WEBHOOK_URL,
                "{player}",
                "https://mc-heads.net/avatar/{player}/100",
                "SurvivalSMP",
                "Playing on {server}",
                "https://example.com/icon.png");

        WebhookService webhook = new WebhookService(
                config, recordingFactory, ignored -> {}, ignored -> {});
        DiscordEventNotifier notifier = new DiscordEventNotifier(webhook, config);

        // 1. Player Join
        MessageEmbed joinEmbed = DiscordEmbedFactory.playerJoined(
                "Steve", "Steve joined the server.", false,
                notifier.footer(), notifier.footerIconUrl());
        notifier.send("channel-123", joinEmbed);
        webhook.processOutgoing();

        assertEquals(1, recordingFactory.recordedBodies.size());
        DataObject joinJson = DataObject.fromJson(recordingFactory.recordedBodies.get(0));
        DataArray joinEmbeds = joinJson.getArray("embeds");
        assertEquals(1, joinEmbeds.length());
        DataObject joinFirst = joinEmbeds.getObject(0);
        assertEquals("JOIN", joinFirst.getString("title"));
        assertEquals("Steve", joinFirst.getObject("author").getString("name"));
        assertEquals("Playing on SurvivalSMP", joinFirst.getObject("footer").getString("text"));
        assertEquals("https://example.com/icon.png", joinFirst.getObject("footer").getString("icon_url"));

        // 2. Player Leave
        MessageEmbed leaveEmbed = DiscordEmbedFactory.playerLeft(
                "Steve", "Steve left the server.",
                notifier.footer(), notifier.footerIconUrl());
        notifier.send("channel-123", leaveEmbed);
        webhook.processOutgoing();

        assertEquals(2, recordingFactory.recordedBodies.size());
        DataObject leaveJson = DataObject.fromJson(recordingFactory.recordedBodies.get(1));
        DataObject leaveFirst = leaveJson.getArray("embeds").getObject(0);
        assertEquals("LEAVE", leaveFirst.getString("title"));
        assertEquals("Steve", leaveFirst.getObject("author").getString("name"));
        assertEquals("Playing on SurvivalSMP", leaveFirst.getObject("footer").getString("text"));

        // 3. Player Death
        DeathContext deathContext = new DeathContext(
                "Steve", "Skeleton", "shot by arrow", DeathCategory.MOB_KILL);
        MessageEmbed deathEmbed = DiscordEmbedFactory.death(
                "DEATH", "Steve was shot by Skeleton.",
                deathContext, DiscordEmbedFactory.DEATH_COLOR,
                notifier.footer(), notifier.footerIconUrl());
        notifier.send("channel-123", deathEmbed);
        webhook.processOutgoing();

        assertEquals(3, recordingFactory.recordedBodies.size());
        DataObject deathJson = DataObject.fromJson(recordingFactory.recordedBodies.get(2));
        DataObject deathFirst = deathJson.getArray("embeds").getObject(0);
        assertEquals("DEATH", deathFirst.getString("title"));
        assertEquals("Steve", deathFirst.getObject("author").getString("name"));
        assertEquals("Playing on SurvivalSMP", deathFirst.getObject("footer").getString("text"));

        // 4. Advancement
        AdvancementContext advContext = new AdvancementContext(
                "Steve", "Suit Up", "Protect yourself with a piece of iron armor",
                AdvancementCategory.STORY, false);
        MessageEmbed advEmbed = DiscordEmbedFactory.advancement(
                advContext, "ADVANCEMENT",
                "Steve has made the advancement Suit Up",
                DiscordEmbedFactory.ADVANCEMENT_COLOR,
                notifier.footer(), notifier.footerIconUrl());
        notifier.send("channel-123", advEmbed);
        webhook.processOutgoing();

        assertEquals(4, recordingFactory.recordedBodies.size());
        DataObject advJson = DataObject.fromJson(recordingFactory.recordedBodies.get(3));
        DataObject advFirst = advJson.getArray("embeds").getObject(0);
        assertEquals("ADVANCEMENT", advFirst.getString("title"));
        assertEquals("Steve", advFirst.getObject("author").getString("name"));
        assertEquals("Playing on SurvivalSMP", advFirst.getObject("footer").getString("text"));

        // 5. Challenge
        AdvancementContext challengeContext = new AdvancementContext(
                "Steve", "How Did We Get Here?", "Have every effect applied at the same time",
                AdvancementCategory.NETHER, true);
        MessageEmbed challengeEmbed = DiscordEmbedFactory.advancement(
                "CHALLENGE",
                "Steve completed How Did We Get Here!",
                challengeContext.player(),
                challengeContext.title(),
                challengeContext.category().displayName(),
                challengeContext.challenge(),
                DiscordEmbedFactory.CHALLENGE_COLOR,
                notifier.footer(),
                notifier.footerIconUrl(),
                Instant.now());
        notifier.send("channel-123", challengeEmbed);
        webhook.processOutgoing();

        assertEquals(5, recordingFactory.recordedBodies.size());
        DataObject chalJson = DataObject.fromJson(recordingFactory.recordedBodies.get(4));
        DataObject chalFirst = chalJson.getArray("embeds").getObject(0);
        assertEquals("CHALLENGE", chalFirst.getString("title"));
        assertEquals("Steve", chalFirst.getObject("author").getString("name"));
        assertEquals("Playing on SurvivalSMP", chalFirst.getObject("footer").getString("text"));
        assertEquals("https://example.com/icon.png", chalFirst.getObject("footer").getString("icon_url"));
    }

    @Test
    void routesChatToWebhookWithCustomUsernameAndAvatarInWebhookMode() {
        RecordingCallFactory recordingFactory = new RecordingCallFactory();
        ConfigManager config = createConfig(
                "WEBHOOK",
                WEBHOOK_URL,
                "{player} [{server}]",
                "https://mc-heads.net/avatar/{player}/100",
                "SMP",
                "footer",
                "");

        WebhookService webhook = new WebhookService(
                config, recordingFactory, ignored -> {}, ignored -> {});

        // Test ChatListener formatting and webhook delivery
        String template = config.message("messages.minecraft-to-discord", "**{player}**: {message}");
        ConfigManager.Settings current = config.currentSettings();
        String server = current.serverName();
        String prefix = "";
        ConfigManager.WebhookSettings webhookSettings = current.webhook();
        String resolvedUsername = webhookSettings.username()
                .replace("{player}", "Alex")
                .replace("{server}", server)
                .replace("{prefix}", prefix);
        String resolvedAvatar = webhookSettings.avatarUrl()
                .replace("{player}", "Alex")
                .replace("{server}", server)
                .replace("{prefix}", prefix);

        boolean sent = ChatListener.relayIfNonBlank(
                template,
                "Alex",
                "Hello Webhook World!",
                Instant.now().toString(),
                server,
                prefix,
                formatted -> webhook.send(resolvedUsername, resolvedAvatar, formatted));

        assertTrue(sent);
        webhook.processOutgoing();

        assertEquals(1, recordingFactory.recordedBodies.size());
        DataObject chatJson = DataObject.fromJson(recordingFactory.recordedBodies.get(0));
        assertEquals("**Alex**: Hello Webhook World!", chatJson.getString("content"));
        assertEquals("Alex [SMP]", chatJson.getString("username"));
        assertEquals("https://mc-heads.net/avatar/Alex/100", chatJson.getString("avatar_url"));
    }

    @Test
    void doesNotInvokeWebhookServiceInBotMode() {
        RecordingCallFactory recordingFactory = new RecordingCallFactory();
        ConfigManager config = createConfig(
                "BOT",
                WEBHOOK_URL,
                "{player}",
                "",
                "SMP",
                "footer",
                "");

        WebhookService webhook = new WebhookService(
                config, recordingFactory, ignored -> {}, ignored -> {});
        // In BOT mode, webhook should never receive embeds from notifier
        DiscordEventNotifier notifier = new DiscordEventNotifier(null, webhook, config);

        MessageEmbed embed = DiscordEmbedFactory.playerJoined(
                "Steve", "joined", false, "footer");
        notifier.send("channel-123", embed);
        webhook.processOutgoing();

        // 0 requests sent through webhook
        assertEquals(0, recordingFactory.recordedRequests.size());
        assertEquals(0, recordingFactory.recordedBodies.size());
    }

    @Test
    void footerExpandsServerAndFallsBackWhenBlank() {
        ConfigManager configWithServer = createConfig(
                "WEBHOOK",
                WEBHOOK_URL,
                "{player}",
                "",
                "MyServer",
                "Powered by {server}",
                "https://example.com/icon.png");

        DiscordEventNotifier notifier1 = new DiscordEventNotifier(null, null, configWithServer);
        assertEquals("Powered by MyServer", notifier1.footer());
        assertEquals("https://example.com/icon.png", notifier1.footerIconUrl());

        ConfigManager configBlankFooter = createConfig(
                "WEBHOOK",
                WEBHOOK_URL,
                "{player}",
                "",
                "MyServer",
                "",
                "");
        DiscordEventNotifier notifier2 = new DiscordEventNotifier(null, null, configBlankFooter);
        // Falls back to discord-events default or legacy footer
        assertTrue(notifier2.footer().contains("MineBridge"));
        assertEquals("", notifier2.footerIconUrl());
    }
}
