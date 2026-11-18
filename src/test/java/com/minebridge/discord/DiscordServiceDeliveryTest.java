package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordServiceDeliveryTest {

    @Test
    void reportsSynchronousDiscordSubmissionFailuresWithoutThrowing() {
        AtomicBoolean reported = new AtomicBoolean();

        boolean submitted = DiscordService.submitSafely(
                () -> {
                    throw new IllegalStateException("test failure");
                },
                exception -> reported.set(exception instanceof IllegalStateException));

        assertFalse(submitted);
        assertTrue(reported.get());
    }

    @Test
    void acceptsSuccessfulAsynchronousRequestSubmission() {
        AtomicBoolean submitted = new AtomicBoolean();

        boolean accepted = DiscordService.submitSafely(
                () -> submitted.set(true),
                exception -> {
                    throw new AssertionError("Unexpected submission failure", exception);
                });

        assertTrue(accepted);
        assertTrue(submitted.get());
    }

    @Test
    void incrementsOutgoingDroppedCounterWhenQueueIsFullAndExposesInStatus() {
        List<String> warnings = new ArrayList<>();
        ConfigManager config = new ConfigManager(null);
        DiscordService service = new DiscordService(
                null, config, null, ignored -> { }, warnings::add);

        for (int index = 0; index < 100; index++) {
            service.send("queued-message-" + index);
        }
        assertEquals(0L, service.getOutgoingDropped());
        assertEquals(0L, service.getStatus(1_000L).outgoingDropped());

        service.send("overflow-1");
        service.send("overflow-2");
        service.send("overflow-3");

        assertEquals(3L, service.getOutgoingDropped());
        assertEquals(3L, service.getStatus(1_000L).outgoingDropped());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("full or closed"));
    }

    @Test
    void incrementsOutgoingDroppedCounterWhenQueueIsClosed() {
        List<String> warnings = new ArrayList<>();
        ConfigManager config = new ConfigManager(null);
        DiscordService service = new DiscordService(
                null, config, null, ignored -> { }, warnings::add);

        service.stop();
        service.send("after-close-1");
        service.send("after-close-2");

        assertEquals(2L, service.getOutgoingDropped());
        assertEquals(2L, service.getStatus(1_000L).outgoingDropped());
        assertEquals(1, warnings.size());
    }

    @Test
    void doesNotIncrementQueueDroppedCounterForBlankOrOversizedMessages() {
        List<String> warnings = new ArrayList<>();
        ConfigManager config = new ConfigManager(null);
        DiscordService service = new DiscordService(
                null, config, null, ignored -> { }, warnings::add);

        service.send(null);
        service.send("   ");
        service.send("x".repeat(2001));

        assertEquals(0L, service.getOutgoingDropped());
        assertEquals(0L, service.getStatus(1_000L).outgoingDropped());
    }

    @Test
    void startsAndReportsStatusSafelyWhenDiscordFeaturesDisabledAndChannelBlank() {
        ConfigManager config = new ConfigManager(null);
        ConfigManager.Settings disabledSettings = new ConfigManager.Settings(
                "valid-test-token",
                "",
                false,
                false,
                Map.of(),
                true,
                true,
                new ConfigManager.DeathSettings(
                        true, true, true, false, false, "", "t", "d", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(
                        true, true, false, true, "", "n", "c", "t", "d", "ct", "cd", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(
                        false, false, "f", false, "", false, false, false,
                        false, "", Map.of(), false, false, "", false, false, false));
        config.commit(new ConfigManager.Candidate(disabledSettings, List.of()));

        JDA stubJda = connectedStubJda();
        DiscordService service = new DiscordService(
                null, config, settings -> stubJda, ignored -> { }, ignored -> { });

        assertTrue(service.start());
        var status = service.getStatus(5_000L);
        assertEquals("CONNECTED", status.discordState());
        assertFalse(status.channelAvailable());
        assertEquals(0L, status.outgoingDropped());
    }

    @Test
    void formatsOutgoingFailureDiagnosticsWithTypeAndSafeContextWithoutLeakingSecrets() {
        Response forbiddenResponse = new Response(null, 403, "Forbidden", -1L, Set.of());
        ErrorResponseException apiError = ErrorResponseException.create(
                ErrorResponse.MISSING_PERMISSIONS, forbiddenResponse);
        String apiDiagnostic = DiscordService.formatFailureDiagnostic(
                "Discord rejected an outgoing message; it was dropped", apiError);
        assertTrue(apiDiagnostic.contains("ErrorResponseException"));
        assertTrue(apiDiagnostic.contains("code=" + apiError.getErrorCode()));
        assertTrue(apiDiagnostic.contains("HTTP 403"));
        assertTrue(apiDiagnostic.contains(ErrorResponse.MISSING_PERMISSIONS.getMeaning()));

        String secretToken = "Bot.super-secret-token.value";
        String secretMessage = "private player chat secret";
        PermissionException permissionError = new TestPermissionException(
                Permission.MESSAGE_SEND,
                "Failed with " + secretToken + " on " + secretMessage);
        String permDiagnostic = DiscordService.formatFailureDiagnostic(
                "Could not submit an announcement to Discord.", permissionError);
        assertTrue(permDiagnostic.contains("TestPermissionException"));
        assertTrue(permDiagnostic.contains("missing permission=" + Permission.MESSAGE_SEND.getName()));
        assertFalse(permDiagnostic.contains(secretToken));
        assertFalse(permDiagnostic.contains(secretMessage));

        RateLimitedException rateLimited = new RateLimitedException(
                "channels/" + secretToken, 1500L);
        String rateDiagnostic = DiscordService.formatFailureDiagnostic(
                "Discord rejected an announcement embed", rateLimited);
        assertTrue(rateDiagnostic.contains("RateLimitedException"));
        assertTrue(rateDiagnostic.contains("retryAfterMs=1500"));
        assertFalse(rateDiagnostic.contains(secretToken));

        RuntimeException nested = new IllegalStateException(
                "Header Authorization: " + secretToken,
                new ConnectException("Payload: " + secretMessage));
        String nestedDiagnostic = DiscordService.formatFailureDiagnostic(
                "Could not submit an outgoing message to Discord; it was dropped", nested);
        assertTrue(nestedDiagnostic.contains("IllegalStateException"));
        assertTrue(nestedDiagnostic.contains("cause=ConnectException"));
        assertFalse(nestedDiagnostic.contains(secretToken));
        assertFalse(nestedDiagnostic.contains(secretMessage));

        String nullDiagnostic = DiscordService.formatFailureDiagnostic(
                "Discord rejected an outgoing message; it was dropped", null);
        assertTrue(nullDiagnostic.contains("UnknownError"));
    }

    private static JDA connectedStubJda() {
        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class<?>[] { JDA.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> JDA.Status.CONNECTED;
                    case "getTextChannelById" ->
                            throw new AssertionError("Should not look up a blank channel ID");
                    case "shutdownNow" -> null;
                    default -> null;
                });
    }

    private static final class TestPermissionException extends PermissionException {
        private TestPermissionException(Permission permission, String reason) {
            super(permission, reason);
        }
    }
}
