package com.minebridge.death;

import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.WebhookService;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordDeathNotifierTest {

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
                public void enqueue(okhttp3.Callback responseCallback) {
                    try {
                        responseCallback.onResponse(this, execute());
                    } catch (IOException e) {
                        responseCallback.onFailure(this, e);
                    }
                }

                @Override
                public void cancel() {
                }

                @Override
                public boolean isExecuted() {
                    return true;
                }

                @Override
                public boolean isCanceled() {
                    return false;
                }

                @Override
                public okio.Timeout timeout() {
                    return okio.Timeout.NONE;
                }

                @Override
                @SuppressWarnings({"MethodDoesntCallSuperMethod", "CloneDoesntCallSuperClone"})
                public Call clone() {
                    return this;
                }
            };
        }
    }

    private ConfigManager createConfig() {
        String yaml = """
                discord:
                  mode: "WEBHOOK"
                  bot-token: "synthetic-test-bot-token-12345"
                  channel-id: "123456789012345678"
                  webhook:
                    url: "%s"
                    username: "{player}"
                    avatar-url: "https://mc-heads.net/avatar/{player}/100"
                events:
                  discord:
                    enabled: true
                    embeds: true
                    footer: "MineBridge | Minecraft Events"
                    footer-icon-url: ""
                    deaths:
                      channel-id: ""
                death-messages:
                  enabled: true
                  discord:
                    enabled: true
                    embed: true
                    title: "☠ Player death"
                    description: "{message}\\nCategory: {category}\\nCause: {cause}"
                    color: "#D32F2F"
                """.formatted(WEBHOOK_URL);

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
    void honorsConfiguredEmbedTitleAndFormatsPlaceholders() {
        RecordingCallFactory recordingFactory = new RecordingCallFactory();
        ConfigManager config = createConfig();
        WebhookService webhook = new WebhookService(
                config, recordingFactory, ignored -> {}, ignored -> {});
        DiscordEventNotifier eventNotifier = new DiscordEventNotifier(webhook, config);
        DiscordDeathNotifier deathNotifier = new DiscordDeathNotifier(eventNotifier);

        ConfigManager.DeathSettings settings = new ConfigManager.DeathSettings(
                true,
                true,
                true,
                true,
                true,
                "",
                "☠ {victim} was slain by {killer}",
                "{message}",
                0xD32F2F,
                Map.of("mob-kill", true),
                Map.of("mob-kill", "&c☠ {victim} was slain by {killer}"));

        DeathContext context = new DeathContext(
                "Alex", "Zombie", "entity attack", DeathCategory.MOB_KILL);

        deathNotifier.notify(settings, "", context, "Alex was slain by Zombie");
        webhook.processOutgoing();

        assertEquals(1, recordingFactory.recordedBodies.size());
        DataObject json = DataObject.fromJson(recordingFactory.recordedBodies.get(0));
        DataArray embeds = json.getArray("embeds");
        assertEquals(1, embeds.length());

        DataObject embed = embeds.getObject(0);
        assertEquals("☠ Alex was slain by Zombie", embed.getString("title"));
        assertEquals("Alex was slain by Zombie", embed.getString("description"));
    }

    @Test
    void fallsBackToDefaultTitleWhenTitleIsNullOrBlank() {
        RecordingCallFactory recordingFactory = new RecordingCallFactory();
        ConfigManager config = createConfig();
        WebhookService webhook = new WebhookService(
                config, recordingFactory, ignored -> {}, ignored -> {});
        DiscordEventNotifier eventNotifier = new DiscordEventNotifier(webhook, config);
        DiscordDeathNotifier deathNotifier = new DiscordDeathNotifier(eventNotifier);

        ConfigManager.DeathSettings settingsNullTitle = new ConfigManager.DeathSettings(
                true,
                true,
                true,
                true,
                true,
                "",
                null,
                "Cause: {cause}",
                0xD32F2F,
                Map.of("fall", true),
                Map.of("fall", "&c☠ {victim} fell to their death"));

        DeathContext context = new DeathContext(
                "Steve", "", "hit the ground too hard", DeathCategory.FALL);

        deathNotifier.notify(settingsNullTitle, "", context, "Steve fell to their death");
        webhook.processOutgoing();

        assertEquals(1, recordingFactory.recordedBodies.size());
        DataObject json = DataObject.fromJson(recordingFactory.recordedBodies.get(0));
        DataObject embed = json.getArray("embeds").getObject(0);
        org.junit.jupiter.api.Assertions.assertFalse(embed.hasKey("title"));
        assertEquals("Steve", embed.getObject("author").getString("name"));
        assertEquals("Cause: hit the ground too hard", embed.getString("description"));
    }
}
