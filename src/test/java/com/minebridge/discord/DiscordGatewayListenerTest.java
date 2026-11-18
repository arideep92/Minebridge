package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
import com.neovisionaries.ws.client.WebSocketFrame;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionInvalidateEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.requests.CloseCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordGatewayListenerTest {

    @Test
    void handlesNullServiceNullEventsAndNullJdaSafely() {
        DiscordGatewayListener nullServiceListener = new DiscordGatewayListener(null);
        assertDoesNotThrow(() -> nullServiceListener.onReady(null));
        assertDoesNotThrow(() -> nullServiceListener.onSessionDisconnect(null));
        assertDoesNotThrow(() -> nullServiceListener.onSessionResume(null));
        assertDoesNotThrow(() -> nullServiceListener.onSessionInvalidate(null));
        assertDoesNotThrow(() -> nullServiceListener.onSessionRecreate(null));
        assertDoesNotThrow(() -> nullServiceListener.onShutdown(null));
        assertDoesNotThrow(() -> nullServiceListener.onException(null));

        List<String> infos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DiscordService service = new DiscordService(
                null, new ConfigManager(null), null, infos::add, warnings::add);
        DiscordGatewayListener listener = new DiscordGatewayListener(service);

        assertDoesNotThrow(() -> listener.onReady(null));
        assertDoesNotThrow(() -> listener.onSessionDisconnect(null));
        assertDoesNotThrow(() -> listener.onSessionResume(null));
        assertDoesNotThrow(() -> listener.onSessionInvalidate(null));
        assertDoesNotThrow(() -> listener.onSessionRecreate(null));
        assertDoesNotThrow(() -> listener.onShutdown(null));
        assertDoesNotThrow(() -> listener.onException(null));

        assertDoesNotThrow(() -> service.onGatewayReady(null));
        assertDoesNotThrow(() -> service.onGatewayDisconnect(null, CloseCode.RECONNECT, true));
        assertDoesNotThrow(() -> service.onGatewayResume(null));
        assertDoesNotThrow(() -> service.onGatewaySessionInvalidate(null));
        assertDoesNotThrow(() -> service.onGatewaySessionRecreate(null));
        assertDoesNotThrow(() -> service.onGatewayShutdown(null, CloseCode.GRACEFUL_CLOSE, 1000));
        assertDoesNotThrow(() -> service.onGatewayException(null, new IllegalStateException("x")));

        assertTrue(infos.isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void emitsGatewayDiagnosticsForActiveClientWithoutDuplicatingReconnectLogic() {
        List<String> infos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AtomicInteger shutdownCalls = new AtomicInteger();
        JDA activeClient = stubJda(JDA.Status.CONNECTING_TO_WEBSOCKET, shutdownCalls);
        JDA unrelatedClient = stubJda(JDA.Status.CONNECTED, new AtomicInteger());

        ConfigManager config = new ConfigManager(null);
        config.commit(new ConfigManager.Candidate(
                new ConfigManager.Settings(
                        "valid-test-token", "12345678901234567", true, true, Map.of()),
                List.of()));

        DiscordService service = new DiscordService(
                null, config, settings -> activeClient, infos::add, warnings::add);
        assertTrue(service.start());

        DiscordGatewayListener listener = new DiscordGatewayListener(service);

        // Ready callback for active client logs gateway connection.
        service.onGatewayReady(activeClient);
        assertTrue(infos.stream().anyMatch(message -> message.contains("gateway connected")));

        // Stale/unrelated client events are ignored.
        listener.onSessionInvalidate(new SessionInvalidateEvent(unrelatedClient));
        listener.onException(new ExceptionEvent(
                unrelatedClient, new IllegalStateException("ignored"), false));
        assertTrue(warnings.isEmpty());

        // Active client disconnect with fatal close code.
        WebSocketFrame closeFrame = WebSocketFrame.createCloseFrame(
                CloseCode.DISALLOWED_INTENTS.getCode(), "Disallowed intents");
        listener.onSessionDisconnect(new SessionDisconnectEvent(
                activeClient, closeFrame, null, true, OffsetDateTime.now()));
        assertTrue(warnings.stream().anyMatch(
                message -> message.contains("4014")
                        && message.contains("non-reconnectable")));

        // Active client session invalidation.
        listener.onSessionInvalidate(new SessionInvalidateEvent(activeClient));
        assertTrue(warnings.stream().anyMatch(
                message -> message.contains("invalidated")));

        // Active client session recreation and resume.
        listener.onSessionRecreate(new SessionRecreateEvent(activeClient));
        listener.onSessionResume(new SessionResumeEvent(activeClient));
        assertTrue(infos.stream().anyMatch(message -> message.contains("recreated")));
        assertTrue(infos.stream().anyMatch(message -> message.contains("resumed")));

        // Active client exception logs type without exposing sensitive message content.
        String secretToken = "secret-bot-token-value";
        listener.onException(new ExceptionEvent(
                activeClient,
                new IllegalStateException("Authorization: " + secretToken),
                false));
        assertTrue(warnings.stream().anyMatch(
                message -> message.contains("IllegalStateException")));
        assertFalse(warnings.stream().anyMatch(
                message -> message.contains(secretToken)));

        // Unexpected shutdown while not stopping logs close code.
        listener.onShutdown(new ShutdownEvent(
                activeClient, OffsetDateTime.now(), CloseCode.AUTHENTICATION_FAILED.getCode()));
        assertTrue(warnings.stream().anyMatch(
                message -> message.contains("4004")
                        && message.contains("shut down")));

        // Diagnostic callbacks never triggered manual client shutdown/reconnect.
        assertEquals(0, shutdownCalls.get());

        // Intentional stop suppresses subsequent gateway shutdown warnings.
        int warningsBeforeStop = warnings.size();
        service.stop();
        assertEquals(1, shutdownCalls.get());
        listener.onShutdown(new ShutdownEvent(activeClient, OffsetDateTime.now(), 1000));
        assertEquals(warningsBeforeStop, warnings.size());
    }

    @Test
    void formatsDisconnectAndShutdownDiagnosticsForReconnectableAndNullCloseCodes() {
        String reconnectable = DiscordService.formatGatewayDisconnectDiagnostic(
                CloseCode.RECONNECT, true);
        assertTrue(reconnectable.contains("4000") || reconnectable.contains(
                String.valueOf(CloseCode.RECONNECT.getCode())));
        assertTrue(reconnectable.contains("resume or reconnect"));

        String nullCodeDisconnect = DiscordService.formatGatewayDisconnectDiagnostic(null, false);
        assertTrue(nullCodeDisconnect.contains("resume or reconnect"));

        String unknownShutdown = DiscordService.formatGatewayShutdownDiagnostic(null, 4999);
        assertTrue(unknownShutdown.contains("4999"));
    }

    private static JDA stubJda(JDA.Status status, AtomicInteger shutdownCalls) {
        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class<?>[] { JDA.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> status;
                    case "getResponseTotal" -> 0L;
                    case "shutdownNow" -> {
                        shutdownCalls.incrementAndGet();
                        yield null;
                    }
                    default -> defaultReturnValue(method.getReturnType());
                });
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
