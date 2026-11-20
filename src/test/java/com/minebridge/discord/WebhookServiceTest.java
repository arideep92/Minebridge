package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
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
import okio.Timeout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookServiceTest {

    private static final String VALID_WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/test-secret-token_ABC123";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

    private ConfigManager createConfig(String webhookUrl, String serverName) {
        ConfigManager.WebhookSettings webhook =
                new ConfigManager.WebhookSettings(webhookUrl, "{player}", "https://avatar.invalid");
        ConfigManager.EmbedFooterSettings footer =
                new ConfigManager.EmbedFooterSettings("MineBridge | {server}", "https://footer.icon");
        ConfigManager.Settings settings = new ConfigManager.Settings(
                IntegrationMode.WEBHOOK,
                "", "", true, true,
                Map.of(), true, true,
                new ConfigManager.DeathSettings(false, false, false, false, false, "", "", "", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(false, false, false, false, "", "", "", "", "", "", "", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(true, true, "MineBridge | Minecraft Events",
                        true, "", true, true, true, false, "", Map.of(), false, false, "", false, false, false),
                webhook,
                serverName,
                footer);
        ConfigManager manager = new ConfigManager(null);
        manager.commit(new ConfigManager.Candidate(settings, List.of()));
        return manager;
    }

    private static Response createResponse(Request request, int code, String body, Map<String, String> headers) {
        Response.Builder builder = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 || code == 204 ? "OK" : "Status " + code);
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        if (body != null) {
            builder.body(ResponseBody.create(body, JSON_MEDIA));
        } else {
            builder.body(ResponseBody.create("", JSON_MEDIA));
        }
        return builder.build();
    }

    static class StubCall implements Call {
        private final Request request;
        private final Response response;
        private final IOException exception;
        private boolean executed;

        public StubCall(Request request, Response response) {
            this.request = request;
            this.response = response;
            this.exception = null;
        }

        public StubCall(Request request, IOException exception) {
            this.request = request;
            this.response = null;
            this.exception = exception;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response execute() throws IOException {
            if (executed) {
                throw new IllegalStateException("Already executed");
            }
            executed = true;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        @Override
        public void enqueue(Callback responseCallback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean isExecuted() {
            return executed;
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
    }

    @Test
    void serializesPlainTextPayloadCorrectly() {
        String json = WebhookService.buildPayload(
                "Hello world!",
                "Alex",
                "https://example.com/avatar.png",
                List.of(),
                "Survival",
                null);

        DataObject root = DataObject.fromJson(json);

        assertEquals("Hello world!", root.getString("content"));
        assertEquals("Alex", root.getString("username"));
        assertEquals("https://example.com/avatar.png", root.getString("avatar_url"));
        assertFalse(root.hasKey("embeds"));
    }

    @Test
    void serializesRichEmbedPayloadCorrectly() {
        MessageEmbed embed = DiscordEmbedFactory.playerJoined(
                "Alex",
                "Alex joined the server.",
                false,
                "MineBridge | {server}");

        ConfigManager.EmbedFooterSettings footerSettings =
                new ConfigManager.EmbedFooterSettings("MineBridge | {server}", "https://icon.invalid");

        String json = WebhookService.buildPayload(
                null,
                "ServerBot",
                null,
                List.of(embed),
                "SMP",
                footerSettings);

        DataObject root = DataObject.fromJson(json);

        assertEquals("ServerBot", root.getString("username"));
        assertTrue(root.hasKey("embeds"));
        DataArray embeds = root.getArray("embeds");
        assertEquals(1, embeds.length());

        DataObject embedNode = embeds.getObject(0);
        assertEquals("PLAYER JOINED", embedNode.getString("title"));
        assertEquals("Alex joined the server.", embedNode.getString("description"));
        assertEquals(DiscordEmbedFactory.JOIN_COLOR, embedNode.getInt("color"));

        DataObject footer = embedNode.getObject("footer");
        assertEquals("MineBridge | SMP", footer.getString("text"));
        assertEquals("https://icon.invalid", footer.getString("icon_url"));

        DataArray fields = embedNode.getArray("fields");
        assertEquals(2, fields.length());
        DataObject field1 = fields.getObject(0);
        assertEquals("Player", field1.getString("name"));
        assertEquals("Alex", field1.getString("value"));
        assertTrue(field1.getBoolean("inline"));

        DataObject field2 = fields.getObject(1);
        assertEquals("Status", field2.getString("name"));
        assertEquals("Online", field2.getString("value"));
    }

    @Test
    void replacesServerPlaceholderInUsernameAndFooter() {
        MessageEmbed embed = DiscordEmbedFactory.base(
                "Title", "Desc", 0x123456, "Footer: {server}", null).build();
        String json = WebhookService.buildPayload(
                "Test",
                "{server} Relay",
                null,
                List.of(embed),
                "SurvivalWorld",
                null);

        DataObject root = DataObject.fromJson(json);

        assertEquals("SurvivalWorld Relay", root.getString("username"));
        DataObject embedNode = root.getArray("embeds").getObject(0);
        assertEquals("Footer: SurvivalWorld", embedNode.getObject("footer").getString("text"));
    }

    @Test
    void rejectsInvalidOrUnapprovedWebhookUrls() {
        List<String> warnings = new ArrayList<>();
        ConfigManager config = createConfig("https://unapproved-host.com/api/webhooks/123/token", "Server");
        WebhookService service = new WebhookService(config, null, warnings::add);

        boolean sent = service.send("Alex", "", "Hello!");

        assertFalse(sent);
        assertEquals(1, service.getOutgoingDropped());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("invalid or unapproved")));
        assertTrue(warnings.stream().noneMatch(w -> w.contains("unapproved-host.com")));
    }

    @Test
    void queueOverflowIncrementsOutgoingDropped() {
        List<String> warnings = new ArrayList<>();
        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, null, warnings::add);

        // Capacity is 100
        for (int i = 0; i < 100; i++) {
            assertTrue(service.send("Alex", "", "Message " + i));
        }
        assertEquals(100, service.getQueueSize());
        assertEquals(0, service.getOutgoingDropped());

        // 101st message should overflow
        assertFalse(service.send("Alex", "", "Overflow message"));
        assertEquals(1, service.getOutgoingDropped());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("queue is full")));
    }

    @Test
    void dispatchesSuccessfulHttpResponse() {
        AtomicInteger executedCount = new AtomicInteger(0);
        Call.Factory callFactory = request -> {
            executedCount.incrementAndGet();
            return new StubCall(request, createResponse(request, 204, null, Map.of()));
        };

        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, callFactory, null, null);

        assertTrue(service.send("Alex", "", "Test message"));
        assertEquals(1, service.getQueueSize());

        service.processOutgoing();

        assertEquals(1, executedCount.get());
        assertEquals(0, service.getQueueSize());
        assertEquals(0, service.getOutgoingDropped());
        assertEquals("READY", service.getState());
    }

    @Test
    void handlesHttp429AndParsesRetryAfterHeaderAndBody() {
        assertEquals(1500L, WebhookService.parseRetryAfterMs(
                createResponse(new Request.Builder().url(VALID_WEBHOOK_URL).build(),
                        429, "", Map.of("Retry-After", "1.5")), ""));

        assertEquals(2500L, WebhookService.parseRetryAfterMs(
                createResponse(new Request.Builder().url(VALID_WEBHOOK_URL).build(),
                        429, "", Map.of()), "{\"retry_after\": 2.5}"));

        List<String> warnings = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        Call.Factory callFactory = request -> {
            int attempt = callCount.incrementAndGet();
            if (attempt == 1) {
                return new StubCall(request, createResponse(request, 429, "{\"retry_after\": 0.05}", Map.of()));
            }
            return new StubCall(request, createResponse(request, 204, null, Map.of()));
        };

        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, callFactory, null, warnings::add);

        service.send("Alex", "", "Rate-limited message");

        // Attempt 1 -> 429
        service.processOutgoing();
        assertEquals(1, callCount.get());
        assertEquals(0, service.getOutgoingDropped()); // not dropped yet, queued for retry!

        // Immediate retry is rate-limited
        service.processOutgoing();
        assertEquals(1, callCount.get()); // did not attempt send because rate limit window is active

        // Wait for rate limit window (50ms)
        try {
            Thread.sleep(60);
        } catch (InterruptedException ignored) {
        }

        // Attempt 2 -> 204 Success
        service.processOutgoing();
        assertEquals(2, callCount.get());
        assertEquals(0, service.getQueueSize());
        assertEquals(0, service.getOutgoingDropped());
    }

    @Test
    void handlesHttp400BadRequestWithoutRetrying() {
        List<String> warnings = new ArrayList<>();
        Call.Factory callFactory = request ->
                new StubCall(request, createResponse(request, 400, "Bad Request", Map.of()));

        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, callFactory, null, warnings::add);

        service.send("Alex", "", "Malformed content");
        service.processOutgoing();

        assertEquals(1, service.getOutgoingDropped());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("HTTP 400 Bad Request")));
    }

    @Test
    void handlesHttp401And404InvalidWebhookWithoutRetrying() {
        List<String> warnings = new ArrayList<>();
        Call.Factory callFactory = request ->
                new StubCall(request, createResponse(request, 404, "Not Found", Map.of()));

        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, callFactory, null, warnings::add);

        service.send("Alex", "", "Content");
        service.processOutgoing();

        assertEquals(1, service.getOutgoingDropped());
        assertEquals("INVALID_WEBHOOK", service.getState());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("HTTP 404")));
    }

    @Test
    void handlesHttp5xxWithControlledRetries() {
        List<String> warnings = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        Call.Factory callFactory = request -> {
            callCount.incrementAndGet();
            return new StubCall(request, createResponse(request, 500, "Internal Server Error", Map.of()));
        };

        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, callFactory, null, warnings::add);

        service.send("Alex", "", "Content");

        // Attempt 1 -> 500
        service.processOutgoing();
        assertEquals(1, callCount.get());
        assertEquals(0, service.getOutgoingDropped()); // scheduled for retry

        assertTrue(warnings.stream().anyMatch(w -> w.contains("HTTP 500")));
    }

    @Test
    void secretTokensAreNeverLogged() {
        List<String> warnings = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        Call.Factory callFactory = request ->
                new StubCall(request, createResponse(request, 401, "Invalid token", Map.of()));

        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, callFactory, infos::add, warnings::add);

        service.send("Alex", "", "Message with secret");
        service.processOutgoing();

        List<String> allLogs = new ArrayList<>(warnings);
        allLogs.addAll(infos);

        for (String log : allLogs) {
            assertFalse(log.contains("test-secret-token_ABC123"),
                    "Log contained unmasked secret token: " + log);
        }
    }

    @Test
    void shutdownClosesQueueAndReleasesResources() {
        List<String> infos = new ArrayList<>();
        ConfigManager config = createConfig(VALID_WEBHOOK_URL, "Server");
        WebhookService service = new WebhookService(config, null, infos::add, null);

        service.start();
        assertEquals("READY", service.getState());

        service.stop();
        assertEquals("STOPPED", service.getState());
        assertTrue(service.isStopping());
        assertFalse(service.send("Alex", "", "Message after stop"));
        assertEquals(1, service.getOutgoingDropped());
        assertTrue(infos.stream().anyMatch(i -> i.contains("service stopped")));
    }
}
