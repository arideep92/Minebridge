package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.config.ConfigManager;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookPlayerAvatarTest {

    private static final String WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/test-token";
    private static final MediaType JSON_MEDIA =
            MediaType.get("application/json; charset=utf-8");

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
                public void enqueue(Callback callback) {
                    throw new UnsupportedOperationException();
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
                public Call clone() {
                    return this;
                }

                @Override
                public okio.Timeout timeout() {
                    return okio.Timeout.NONE;
                }
            };
        }
    }

    private ConfigManager createConfig(String mode, String avatarUrl, String serverName) {
        IntegrationMode integrationMode = IntegrationMode.valueOf(mode);
        ConfigManager.WebhookSettings webhook =
                new ConfigManager.WebhookSettings(WEBHOOK_URL, "{prefix}{player}", avatarUrl);
        ConfigManager.EmbedFooterSettings footer =
                new ConfigManager.EmbedFooterSettings("", "");
        ConfigManager.Settings settings = new ConfigManager.Settings(
                integrationMode,
                "token", "123456789012345678", true, true,
                Map.of(), true, true,
                new ConfigManager.DeathSettings(false, false, false, false, false, "", "", "", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(false, false, false, false, "", "", "", "", "", "", "", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(
                        true, true, "",
                        true, "", true, true, true,
                        true, "", Map.of(), true,
                        true, "", true, true, true,
                        true, "", true, true),
                webhook,
                serverName,
                footer,
                true,
                ConfigManager.defaultCrossWorldSettings(),
                new ConfigManager.HybridSettings(IntegrationMode.WEBHOOK));

        ConfigManager manager = new ConfigManager(null);
        manager.commit(new ConfigManager.Candidate(settings, List.of()));
        return manager;
    }

    @Test
    void testAvatarUrlGenerationFromUuid() {
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        String generated = SkinAvatarService.getCrafatarAvatarUrl(uuid);
        assertEquals("https://crafatar.com/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?overlay", generated);
        assertEquals("https://crafatar.com/avatars/{uuid}?overlay", SkinAvatarService.CRAFATAR_AVATAR_ENDPOINT);
        assertEquals(SkinAvatarService.CRAFATAR_AVATAR_ENDPOINT, SkinAvatarService.DEFAULT_AVATAR_TEMPLATE);
        assertEquals("https://crafatar.com/avatars/{uuid}?overlay", SkinAvatarService.Provider.CRAFATAR.getTemplate());
    }

    @Test
    void testUuidFormattingCompatibility() {
        // Standard lowercase hyphenated 36-char UUID
        String standard = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        assertEquals("https://crafatar.com/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?overlay",
                SkinAvatarService.getCrafatarAvatarUrl(standard));

        // Uppercase hyphenated UUID string
        String upper = "069A79F4-44E9-4726-A5BE-FCA90E38AAF5";
        assertEquals("https://crafatar.com/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?overlay",
                SkinAvatarService.getCrafatarAvatarUrl(upper));

        // 32-char hex UUID without hyphens
        String unhyphenated = "069a79f444e94726a5befca90e38aaf5";
        assertEquals("https://crafatar.com/avatars/069a79f444e94726a5befca90e38aaf5?overlay",
                SkinAvatarService.getCrafatarAvatarUrl(unhyphenated));

        // Uppercase 32-char hex UUID without hyphens
        String upperUnhyphenated = "069A79F444E94726A5BEFCA90E38AAF5";
        assertEquals("https://crafatar.com/avatars/069a79f444e94726a5befca90e38aaf5?overlay",
                SkinAvatarService.getCrafatarAvatarUrl(upperUnhyphenated));

        // Null and invalid inputs produce empty string safely without relying on usernames
        assertEquals("", SkinAvatarService.getCrafatarAvatarUrl((UUID) null));
        assertEquals("", SkinAvatarService.getCrafatarAvatarUrl((String) null));
        assertEquals("", SkinAvatarService.getCrafatarAvatarUrl(""));
        assertEquals("", SkinAvatarService.getCrafatarAvatarUrl("Steve"));
        assertEquals("", SkinAvatarService.getCrafatarAvatarUrl("invalid-not-a-uuid"));
    }

    @Test
    void testWebhookPayloadsContainPlayerAvatarAcrossAllEventTypes() {
        RecordingCallFactory recording = new RecordingCallFactory();
        ConfigManager config = createConfig("WEBHOOK", "https://crafatar.com/avatars/{uuid}?overlay", "SurvivalSMP");
        WebhookService webhook = new WebhookService(config, recording, ignored -> {}, ignored -> {});
        DiscordEventNotifier notifier = new DiscordEventNotifier(webhook, config);

        UUID aliceUuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        String expectedAliceAvatar = "https://crafatar.com/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?overlay";

        // 1. Chat Message
        boolean chatSent = webhook.sendPlayer(aliceUuid, "Alice", "Hello Discord!");
        assertTrue(chatSent);
        webhook.processOutgoing();
        assertEquals(1, recording.recordedBodies.size());
        DataObject chatJson = DataObject.fromJson(recording.recordedBodies.get(0));
        assertEquals("Hello Discord!", chatJson.getString("content"));
        assertEquals(expectedAliceAvatar, chatJson.getString("avatar_url"));

        // 2. Player Join
        MessageEmbed joinEmbed = DiscordEmbedFactory.playerJoined(
                "Alice", "Alice joined the server.", false, "", "");
        notifier.send("channel-1", joinEmbed, aliceUuid);
        webhook.processOutgoing();
        assertEquals(2, recording.recordedBodies.size());
        DataObject joinJson = DataObject.fromJson(recording.recordedBodies.get(1));
        assertEquals(expectedAliceAvatar, joinJson.getString("avatar_url"));

        // 3. Player Leave
        MessageEmbed leaveEmbed = DiscordEmbedFactory.playerLeft(
                "Alice", "Alice left the server.", "", "");
        notifier.send("channel-1", leaveEmbed, aliceUuid);
        webhook.processOutgoing();
        assertEquals(3, recording.recordedBodies.size());
        DataObject leaveJson = DataObject.fromJson(recording.recordedBodies.get(2));
        assertEquals(expectedAliceAvatar, leaveJson.getString("avatar_url"));

        // 4. Death Notification
        DeathContext deathContext = new DeathContext(
                "Alice", "Zombie", "slain by Zombie", DeathCategory.MOB_KILL, aliceUuid);
        MessageEmbed deathEmbed = DiscordEmbedFactory.death(
                "", "Alice was slain by Zombie", deathContext, DiscordEmbedFactory.DEATH_COLOR, "", "");
        notifier.send("channel-1", deathEmbed, deathContext.victimUuid());
        webhook.processOutgoing();
        assertEquals(4, recording.recordedBodies.size());
        DataObject deathJson = DataObject.fromJson(recording.recordedBodies.get(3));
        assertEquals(expectedAliceAvatar, deathJson.getString("avatar_url"));

        // 5. Advancement Notification
        AdvancementContext advContext = new AdvancementContext(
                "Alice", "Suit Up", "Protect yourself", AdvancementCategory.STORY, false, aliceUuid);
        MessageEmbed advEmbed = DiscordEmbedFactory.advancement(
                advContext, "", "Alice unlocked Suit Up", DiscordEmbedFactory.ADVANCEMENT_COLOR, "", "");
        notifier.send("channel-1", advEmbed, advContext.playerUuid());
        webhook.processOutgoing();
        assertEquals(5, recording.recordedBodies.size());
        DataObject advJson = DataObject.fromJson(recording.recordedBodies.get(4));
        assertEquals(expectedAliceAvatar, advJson.getString("avatar_url"));

        // 6. Challenge Notification
        AdvancementContext chalContext = new AdvancementContext(
                "Alice", "How Did We Get Here?", "Effects", AdvancementCategory.NETHER, true, aliceUuid);
        MessageEmbed chalEmbed = DiscordEmbedFactory.advancement(
                chalContext, "", "Alice completed challenge", DiscordEmbedFactory.CHALLENGE_COLOR, "", "");
        notifier.send("channel-1", chalEmbed, chalContext.playerUuid());
        webhook.processOutgoing();
        assertEquals(6, recording.recordedBodies.size());
        DataObject chalJson = DataObject.fromJson(recording.recordedBodies.get(5));
        assertEquals(expectedAliceAvatar, chalJson.getString("avatar_url"));
    }

    @Test
    void testServerEventsRetainServerAvatar() {
        RecordingCallFactory recording = new RecordingCallFactory();
        String serverAvatar = "https://example.com/branding/server-icon.png";
        ConfigManager config = createConfig("WEBHOOK", serverAvatar, "SurvivalSMP");
        WebhookService webhook = new WebhookService(config, recording, ignored -> {}, ignored -> {});
        DiscordEventNotifier notifier = new DiscordEventNotifier(webhook, config);

        // Server Start
        notifier.sendServerStart();
        webhook.processOutgoing();
        assertEquals(1, recording.recordedBodies.size());
        DataObject startJson = DataObject.fromJson(recording.recordedBodies.get(0));
        assertEquals(serverAvatar, startJson.getString("avatar_url"));
        assertEquals("SurvivalSMP", startJson.getString("username"));

        // Server Stop
        notifier.sendServerStop();
        webhook.processOutgoing();
        assertEquals(2, recording.recordedBodies.size());
        DataObject stopJson = DataObject.fromJson(recording.recordedBodies.get(1));
        assertEquals(serverAvatar, stopJson.getString("avatar_url"));
        assertEquals("SurvivalSMP", stopJson.getString("username"));
    }

    @Test
    void testServerEventsWithoutStaticAvatarDoNotLeakPlaceholders() {
        RecordingCallFactory recording = new RecordingCallFactory();
        // Config with placeholder template: server events must strip it so no broken URL is sent
        ConfigManager config = createConfig("WEBHOOK", "https://crafatar.com/avatars/{uuid}?overlay", "SurvivalSMP");
        WebhookService webhook = new WebhookService(config, recording, ignored -> {}, ignored -> {});
        DiscordEventNotifier notifier = new DiscordEventNotifier(webhook, config);

        notifier.sendServerStart();
        webhook.processOutgoing();
        assertEquals(1, recording.recordedBodies.size());
        DataObject startJson = DataObject.fromJson(recording.recordedBodies.get(0));
        assertFalse(startJson.hasKey("avatar_url"), "Server events without static avatar must not send unresolved template");
    }

    @Test
    void testMissingUuidFallsBackToConfiguredWebhookAvatar() {
        RecordingCallFactory recording = new RecordingCallFactory();
        String fallbackAvatar = "https://example.com/default-webhook-avatar.png";
        ConfigManager config = createConfig("WEBHOOK", fallbackAvatar, "SurvivalSMP");
        WebhookService webhook = new WebhookService(config, recording, ignored -> {}, ignored -> {});

        // Resolve with null UUID -> returns fallback avatar
        String resolved = webhook.resolvePlayerAvatarUrl(null);
        assertEquals(fallbackAvatar, resolved);

        // Send player embed with null UUID
        MessageEmbed embed = DiscordEmbedFactory.playerJoined("Unknown", "Joined", false, "", "");
        boolean sent = webhook.sendPlayerEmbed(embed, null);
        assertTrue(sent);
        webhook.processOutgoing();

        assertEquals(1, recording.recordedBodies.size());
        DataObject json = DataObject.fromJson(recording.recordedBodies.get(0));
        assertEquals(fallbackAvatar, json.getString("avatar_url"));
        assertEquals(0, webhook.getOutgoingDropped());
    }

    @Test
    void testMissingUuidWithPlaceholderConfigFallsBackToEmptyStringWithoutError() {
        RecordingCallFactory recording = new RecordingCallFactory();
        ConfigManager config = createConfig("WEBHOOK", "https://crafatar.com/avatars/{uuid}?overlay", "SurvivalSMP");
        WebhookService webhook = new WebhookService(config, recording, ignored -> {}, ignored -> {});

        // Fallback for null UUID when template has {uuid}
        String resolved = webhook.resolvePlayerAvatarUrl(null);
        assertEquals("", resolved);

        MessageEmbed embed = DiscordEmbedFactory.playerJoined("Unknown", "Joined", false, "", "");
        boolean sent = webhook.sendPlayerEmbed(embed, null);
        assertTrue(sent);
        webhook.processOutgoing();

        assertEquals(1, recording.recordedBodies.size());
        DataObject json = DataObject.fromJson(recording.recordedBodies.get(0));
        assertFalse(json.hasKey("avatar_url"), "Null UUID with placeholder config must omit avatar_url");
        assertEquals(0, webhook.getOutgoingDropped());
    }

    @Test
    void testAvatarResolutionExceptionDoesNotPreventDelivery() {
        RecordingCallFactory recording = new RecordingCallFactory();
        ConfigManager config = createConfig("WEBHOOK", "https://example.com/fallback.png", "SurvivalSMP");

        // Faulty avatar service that throws an unchecked exception during resolution
        SkinAvatarService faultyAvatarService = new SkinAvatarService() {
            @Override
            public String getPlayerAvatar(UUID uuid, String fallbackAvatarUrl) {
                throw new RuntimeException("Simulated avatar resolution failure");
            }
        };

        WebhookService webhook = new WebhookService(
                config, recording, ignored -> {}, ignored -> {}, faultyAvatarService);

        UUID uuid = UUID.randomUUID();
        // Method must catch exception and gracefully return fallback avatar
        String resolved = webhook.resolvePlayerAvatarUrl(uuid);
        assertEquals("https://example.com/fallback.png", resolved);

        // Sending player embed must succeed despite avatar resolution exception
        MessageEmbed embed = DiscordEmbedFactory.playerJoined("Player", "Joined", false, "", "");
        boolean sent = webhook.sendPlayerEmbed(embed, uuid);
        assertTrue(sent, "Message delivery must not be prevented by avatar lookup failure");

        webhook.processOutgoing();
        assertEquals(1, recording.recordedBodies.size());
        DataObject json = DataObject.fromJson(recording.recordedBodies.get(0));
        assertEquals("https://example.com/fallback.png", json.getString("avatar_url"));
        assertEquals(0, webhook.getOutgoingDropped());
    }

    @Test
    void testDifferentConsecutivePlayerMessagesHaveDistinctAvatarsWithoutLeakingState() {
        RecordingCallFactory recording = new RecordingCallFactory();
        String serverAvatar = "https://example.com/server.png";
        ConfigManager config = createConfig("WEBHOOK", serverAvatar, "SurvivalSMP");
        WebhookService webhook = new WebhookService(config, recording, ignored -> {}, ignored -> {});

        UUID aliceUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID bobUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String expectedAlice = "https://crafatar.com/avatars/11111111-1111-1111-1111-111111111111?overlay";
        String expectedBob = "https://crafatar.com/avatars/22222222-2222-2222-2222-222222222222?overlay";

        // 1. Alice message
        webhook.sendPlayer(aliceUuid, "Alice", "Message 1 from Alice");
        // 2. Bob message
        webhook.sendPlayer(bobUuid, "Bob", "Message 2 from Bob");
        // 3. Server broadcast
        webhook.send("Broadcast from server");
        // 4. Alice message again (should hit cache)
        webhook.sendPlayer(aliceUuid, "Alice", "Message 3 from Alice");

        // Flush all 4 messages
        for (int i = 0; i < 4; i++) {
            webhook.processOutgoing();
        }

        assertEquals(4, recording.recordedBodies.size());

        DataObject body0 = DataObject.fromJson(recording.recordedBodies.get(0));
        DataObject body1 = DataObject.fromJson(recording.recordedBodies.get(1));
        DataObject body2 = DataObject.fromJson(recording.recordedBodies.get(2));
        DataObject body3 = DataObject.fromJson(recording.recordedBodies.get(3));

        assertEquals(expectedAlice, body0.getString("avatar_url"));
        assertEquals("Alice", body0.getString("username"));

        assertEquals(expectedBob, body1.getString("avatar_url"));
        assertEquals("Bob", body1.getString("username"));

        assertEquals(serverAvatar, body2.getString("avatar_url"));
        assertEquals("SurvivalSMP", body2.getString("username"));

        assertEquals(expectedAlice, body3.getString("avatar_url"));
        assertEquals("Alice", body3.getString("username"));
    }


}
