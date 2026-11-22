package com.minebridge.update;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {

    private static Call.Factory mockCallFactory(int statusCode, String responseBody) {
        return request -> new Call() {
            @Override public Request request() { return request; }
            @Override public Response execute() {
                return new Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message(statusCode == 200 ? "OK" : "Error")
                        .body(ResponseBody.create(responseBody, MediaType.get("application/json; charset=utf-8")))
                        .build();
            }
            @Override public void enqueue(Callback responseCallback) {}
            @Override public void cancel() {}
            @Override public boolean isExecuted() { return true; }
            @Override public boolean isCanceled() { return false; }
            @Override public Timeout timeout() { return Timeout.NONE; }
            @Override public Call clone() { return this; }
        };
    }

    private static Call.Factory throwingCallFactory(IOException exception) {
        return request -> new Call() {
            @Override public Request request() { return request; }
            @Override public Response execute() throws IOException {
                throw exception;
            }
            @Override public void enqueue(Callback responseCallback) {}
            @Override public void cancel() {}
            @Override public boolean isExecuted() { return true; }
            @Override public boolean isCanceled() { return false; }
            @Override public Timeout timeout() { return Timeout.NONE; }
            @Override public Call clone() { return this; }
        };
    }

    private static String createModrinthVersionsJson(String versionNumber, String versionType, String status) {
        return """
                [
                  {
                    "id": "v123",
                    "project_id": "minebridge",
                    "version_number": "%s",
                    "version_type": "%s",
                    "status": "%s",
                    "name": "MineBridge %s",
                    "files": []
                  }
                ]
                """.formatted(versionNumber, versionType, status, versionNumber);
    }

    private static CommandSender createSender(boolean hasAdminPermission, List<String> receivedMessages) {
        String senderName = "Sender-" + java.util.UUID.randomUUID();
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        String perm = (String) args[0];
                        return UpdateChecker.ADMIN_PERMISSION.equals(perm) && hasAdminPermission;
                    }
                    if ("sendMessage".equals(method.getName())) {
                        receivedMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    if ("getName".equals(method.getName())) {
                        return senderName;
                    }
                    return null;
                });
    }

    @Test
    void currentVersionIsUpToDate() {
        String json = createModrinthVersionsJson("1.3.0", "release", "listed");
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json), null, null, null);

        UpdateChecker.UpdateResult result = checker.checkForUpdate();

        assertFalse(result.updateAvailable());
        assertEquals("1.3.0", result.installedVersion());
        assertEquals("1.3.0", result.latestVersion());
        assertTrue(result.enabled());
        assertNull(result.error());
    }

    @Test
    void newerReleaseIsAvailable() {
        String json = createModrinthVersionsJson("1.3.1", "release", "listed");
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json), null, null, null);

        UpdateChecker.UpdateResult result = checker.checkForUpdate();

        assertTrue(result.updateAvailable());
        assertEquals("1.3.0", result.installedVersion());
        assertEquals("1.3.1", result.latestVersion());
        assertEquals("https://modrinth.com/plugin/minebridge/version/1.3.1", result.releaseUrl());
        assertTrue(result.enabled());
        assertNull(result.error());
    }

    @Test
    void olderRemoteVersion() {
        String json = createModrinthVersionsJson("1.2.9", "release", "listed");
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json), null, null, null);

        UpdateChecker.UpdateResult result = checker.checkForUpdate();

        assertFalse(result.updateAvailable());
        assertEquals("1.3.0", result.installedVersion());
        assertEquals("1.3.0", result.latestVersion());
        assertTrue(result.enabled());
        assertNull(result.error());
    }

    @Test
    void invalidVersionStrings() {
        String json = createModrinthVersionsJson("invalid-version", "release", "listed");
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json), null, null, null);

        UpdateChecker.UpdateResult result = checker.checkForUpdate();

        assertFalse(result.updateAvailable());
        assertNotNull(result.error());

        // Test with invalid installed version
        UpdateChecker invalidInstalled = new UpdateChecker(
                "not.a.version", true, mockCallFactory(200, json), null, null, null);
        assertFalse(invalidInstalled.checkForUpdate().updateAvailable());

        // SemanticVersion parsing edge cases
        assertNull(UpdateChecker.SemanticVersion.parse(null));
        assertNull(UpdateChecker.SemanticVersion.parse(""));
        assertNull(UpdateChecker.SemanticVersion.parse("   "));
        assertNull(UpdateChecker.SemanticVersion.parse("abc"));
        assertNull(UpdateChecker.SemanticVersion.parse("1.a.0"));
        assertNull(UpdateChecker.SemanticVersion.parse("-1.0.0"));
        assertFalse(UpdateChecker.isNewerThan("invalid", "1.3.0"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0", "invalid"));
        assertFalse(UpdateChecker.isNewerThan(null, "1.3.0"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0", null));
    }

    @Test
    void apiUnavailable() {
        // Network failure (IOException / timeout)
        UpdateChecker timeoutChecker = new UpdateChecker(
                "1.3.0", true, throwingCallFactory(new IOException("Connection timed out")), null, null, null);

        UpdateChecker.UpdateResult timeoutResult = timeoutChecker.checkForUpdate();
        assertFalse(timeoutResult.updateAvailable());
        assertEquals("IOException", timeoutResult.error());

        // HTTP 503 Service Unavailable
        UpdateChecker serverErrorChecker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(503, "Service Unavailable"), null, null, null);

        UpdateChecker.UpdateResult errorResult = serverErrorChecker.checkForUpdate();
        assertFalse(errorResult.updateAvailable());
        assertEquals("HTTP 503", errorResult.error());
    }

    @Test
    void malformedApiResponse() {
        // Completely invalid JSON
        UpdateChecker brokenJsonChecker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, "{not valid json at all"), null, null, null);

        UpdateChecker.UpdateResult result1 = brokenJsonChecker.checkForUpdate();
        assertFalse(result1.updateAvailable());
        assertTrue(result1.error().contains("Malformed API response"));

        // JSON object instead of array
        UpdateChecker objectJsonChecker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, "{\"error\": \"not found\"}"), null, null, null);

        UpdateChecker.UpdateResult result2 = objectJsonChecker.checkForUpdate();
        assertFalse(result2.updateAvailable());
        assertTrue(result2.error().contains("Malformed API response"));

        // Empty body
        UpdateChecker emptyBodyChecker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, ""), null, null, null);

        UpdateChecker.UpdateResult result3 = emptyBodyChecker.checkForUpdate();
        assertFalse(result3.updateAvailable());
        assertEquals("Empty response body", result3.error());
    }

    @Test
    void nonReleaseVersionsAreIgnored() {
        // Multi-version JSON with a newer beta and alpha, but older release
        String json = """
                [
                  {
                    "id": "b1",
                    "project_id": "minebridge",
                    "version_number": "1.4.0-beta.1",
                    "version_type": "beta",
                    "status": "listed",
                    "name": "MineBridge 1.4.0 Beta 1"
                  },
                  {
                    "id": "a1",
                    "project_id": "minebridge",
                    "version_number": "2.0.0-alpha.1",
                    "version_type": "alpha",
                    "status": "listed",
                    "name": "MineBridge 2.0.0 Alpha 1"
                  },
                  {
                    "id": "r1",
                    "project_id": "minebridge",
                    "version_number": "1.3.0",
                    "version_type": "release",
                    "status": "listed",
                    "name": "MineBridge 1.3.0"
                  }
                ]
                """;

        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json), null, null, null);

        UpdateChecker.UpdateResult result = checker.checkForUpdate();

        // The beta and alpha must be ignored; the highest release is 1.3.0 which matches installed
        assertFalse(result.updateAvailable());
        assertEquals("1.3.0", result.latestVersion());

        // Unlisted release should also be ignored
        String unlistedJson = """
                [
                  {
                    "id": "u1",
                    "project_id": "minebridge",
                    "version_number": "1.4.0",
                    "version_type": "release",
                    "status": "unlisted",
                    "name": "MineBridge 1.4.0 Unlisted"
                  }
                ]
                """;
        UpdateChecker unlistedChecker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, unlistedJson), null, null, null);
        assertFalse(unlistedChecker.checkForUpdate().updateAvailable());
    }

    @Test
    void updateCheckerDisabled() {
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", false, mockCallFactory(200, "should not be called"), null, null, null);

        UpdateChecker.UpdateResult syncResult = checker.checkForUpdate();
        assertFalse(syncResult.updateAvailable());
        assertFalse(syncResult.enabled());

        try {
            UpdateChecker.UpdateResult asyncResult = checker.checkForUpdateAsync().get();
            assertFalse(asyncResult.updateAvailable());
            assertFalse(asyncResult.enabled());
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        }
    }

    @Test
    void noNotificationIsSentToOrdinaryPlayersAndNoDuplicateNotifications() {
        UpdateChecker checker = new UpdateChecker("1.3.0", true);
        UpdateChecker.UpdateResult updateAvailable = UpdateChecker.UpdateResult.available(
                "1.3.0", "1.3.1", "https://modrinth.com/plugin/minebridge/version/1.3.1");

        List<String> ordinaryMessages = new ArrayList<>();
        CommandSender ordinaryPlayer = createSender(false, ordinaryMessages);

        List<String> adminMessages = new ArrayList<>();
        CommandSender adminPlayer = createSender(true, adminMessages);

        // Notify ordinary sender -> 0 messages
        checker.notifyIfAdmin(ordinaryPlayer, updateAvailable);
        assertEquals(0, ordinaryMessages.size(), "Ordinary player must not receive update notifications");

        // Notify admin sender -> 3 messages (header, version details, download link)
        checker.notifyIfAdmin(adminPlayer, updateAvailable);
        assertEquals(3, adminMessages.size(), "Admin player must receive update notifications");
        assertTrue(adminMessages.get(0).contains("update is available"));
        assertTrue(adminMessages.get(1).contains("1.3.1"));
        assertTrue(adminMessages.get(2).contains("https://modrinth.com/plugin/minebridge/version/1.3.1"));

        // Deduplication: notifying the same admin again (e.g. startup check then join) must NOT send duplicate messages
        checker.notifyIfAdmin(adminPlayer, updateAvailable);
        assertEquals(3, adminMessages.size(), "Admin must not receive duplicate update notifications on subsequent calls");

        // Broadcast to group: ordinary receives nothing, already-notified admin receives nothing, new admin receives notification
        List<String> newAdminMessages = new ArrayList<>();
        CommandSender newAdminPlayer = createSender(true, newAdminMessages);

        checker.notifyAdmins(updateAvailable, List.of(ordinaryPlayer, adminPlayer, newAdminPlayer));

        assertEquals(0, ordinaryMessages.size(), "Ordinary player must not receive update notifications from notifyAdmins");
        assertEquals(3, adminMessages.size(), "Already notified admin must not receive duplicate notifications");
        assertEquals(3, newAdminMessages.size(), "New admin player must receive update notifications from notifyAdmins");
    }

    @Test
    void executorCleanup() {
        UpdateChecker checker = new UpdateChecker("1.3.0", true);
        assertFalse(checker.isShutdown());

        checker.shutdown();
        assertTrue(checker.isShutdown());

        // Repeated shutdown is safe and idempotent
        assertDoesNotThrow(checker::shutdown);
    }

    @Test
    void semanticVersionComparisonNotLexicographical() {
        // "1.10.0" is semantically greater than "1.9.0", even though lexicographically "1.10.0" < "1.9.0"
        assertTrue(UpdateChecker.isNewerThan("1.10.0", "1.9.0"));
        assertFalse(UpdateChecker.isNewerThan("1.9.0", "1.10.0"));

        // Multi-digit patch
        assertTrue(UpdateChecker.isNewerThan("1.3.10", "1.3.9"));
        assertFalse(UpdateChecker.isNewerThan("1.3.9", "1.3.10"));

        // Major version increase
        assertTrue(UpdateChecker.isNewerThan("2.0.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewerThan("1.9.9", "2.0.0"));

        // Leading 'v'
        assertTrue(UpdateChecker.isNewerThan("v1.3.1", "1.3.0"));
        assertTrue(UpdateChecker.isNewerThan("1.3.1", "v1.3.0"));

        // Same version is never an update
        assertFalse(UpdateChecker.isNewerThan("1.3.0", "1.3.0"));
        assertFalse(UpdateChecker.isNewerThan("v1.3.0", "1.3.0"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0", "v1.3.0"));

        // 4th segment build support
        assertTrue(UpdateChecker.isNewerThan("1.3.0.1", "1.3.0"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0", "1.3.0.1"));

        // Pre-release versions have lower precedence than normal releases (SemVer 2.0.0 Rule 9)
        assertTrue(UpdateChecker.isNewerThan("1.3.0", "1.3.0-rc.1"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0-rc.1", "1.3.0"));
        assertTrue(UpdateChecker.isNewerThan("1.3.0-beta.2", "1.3.0-beta.1"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0-beta.1", "1.3.0-beta.2"));

        // Build metadata is ignored for precedence (SemVer 2.0.0 Rule 10)
        assertFalse(UpdateChecker.isNewerThan("1.3.0+build.1", "1.3.0+build.2"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0", "1.3.0+build.1"));
        assertFalse(UpdateChecker.isNewerThan("1.3.0+build.1", "1.3.0"));
    }

    @Test
    void modrinthReleasesWithPreReleaseVersionNumbersAreIgnored() {
        // Even if marked version_type="release", a pre-release version string must not be treated as a stable release
        String json = createModrinthVersionsJson("1.4.0-rc.1", "release", "listed");
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json), null, null, null);

        UpdateChecker.UpdateResult result = checker.checkForUpdate();
        assertFalse(result.updateAvailable());
        assertEquals("No listed release versions found", result.error());
    }

    @Test
    void shutdownCancelsInFlightHttpCall() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishBlock = new java.util.concurrent.CountDownLatch(1);

        Call.Factory blockingCallFactory = request -> new Call() {
            @Override public Request request() { return request; }
            @Override public Response execute() throws IOException {
                started.countDown();
                try {
                    finishBlock.await(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (canceled.get()) {
                    throw new IOException("Canceled");
                }
                return new Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create("[]", MediaType.get("application/json")))
                        .build();
            }
            @Override public void enqueue(Callback responseCallback) {}
            @Override public void cancel() {
                canceled.set(true);
                finishBlock.countDown();
            }
            @Override public boolean isExecuted() { return true; }
            @Override public boolean isCanceled() { return canceled.get(); }
            @Override public Timeout timeout() { return Timeout.NONE; }
            @Override public Call clone() { return this; }
        };

        UpdateChecker checker = new UpdateChecker("1.3.0", true, blockingCallFactory, null, null, null);
        java.util.concurrent.CompletableFuture<UpdateChecker.UpdateResult> future = checker.checkForUpdateAsync();

        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        checker.shutdown();

        assertTrue(canceled.get(), "Shutdown must cancel any in-flight HTTP call");
        UpdateChecker.UpdateResult res = future.get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(res.updateAvailable());
    }

    @Test
    void consoleNotificationFormat() {
        UpdateChecker checker = new UpdateChecker("1.3.0", true);
        UpdateChecker.UpdateResult updateAvailable = UpdateChecker.UpdateResult.available(
                "1.3.0", "1.4.0", "https://modrinth.com/plugin/minebridge/version/1.4.0");

        List<String> consoleLogs = new ArrayList<>();
        checker.notifyConsole(updateAvailable, consoleLogs::add);

        assertEquals(3, consoleLogs.size());
        assertTrue(consoleLogs.get(0).contains("1.3.0"));
        assertTrue(consoleLogs.get(1).contains("1.4.0"));
        assertTrue(consoleLogs.get(2).contains("Update available"));

        // When up to date, console receives up to date message
        consoleLogs.clear();
        checker.notifyConsole(UpdateChecker.UpdateResult.upToDate("1.3.0"), consoleLogs::add);
        assertEquals(2, consoleLogs.size());
        assertTrue(consoleLogs.get(0).contains("1.3.0"));
        assertTrue(consoleLogs.get(1).contains("Up to date"));
    }

    @Test
    void upToDateLogging() {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        UpdateChecker checker = new UpdateChecker("1.3.0", true, infoLogs::add, warnLogs::add);

        checker.notifyConsole(UpdateChecker.UpdateResult.upToDate("1.3.0"));

        assertEquals(2, infoLogs.size());
        assertTrue(infoLogs.get(0).contains("1.3.0"));
        assertTrue(infoLogs.get(1).contains("Up to date"));
        assertTrue(warnLogs.isEmpty());
    }

    @Test
    void updateAvailableLogging() {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        UpdateChecker checker = new UpdateChecker("1.3.0", true, infoLogs::add, warnLogs::add);

        checker.notifyConsole(UpdateChecker.UpdateResult.available(
                "1.3.0", "1.4.0", "https://modrinth.com/plugin/minebridge/version/1.4.0"));

        assertEquals(3, infoLogs.size());
        assertTrue(infoLogs.get(0).contains("1.3.0"));
        assertTrue(infoLogs.get(1).contains("1.4.0"));
        assertTrue(infoLogs.get(2).contains("Update available"));
        assertTrue(warnLogs.isEmpty());
    }

    @Test
    void failedCheckLogging() {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        UpdateChecker checker = new UpdateChecker("1.3.0", true, infoLogs::add, warnLogs::add);

        checker.notifyConsole(UpdateChecker.UpdateResult.failed("HTTP 503"));

        assertEquals(2, infoLogs.size());
        assertTrue(infoLogs.get(0).contains("1.3.0"));
        assertTrue(infoLogs.get(1).contains("HTTP 503"));
        assertTrue(warnLogs.isEmpty());
    }

    @Test
    void disabledCheckingProducesNoMessages() throws Exception {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", false, mockCallFactory(200, "[]"), null, infoLogs::add, warnLogs::add);

        UpdateChecker.UpdateResult result = checker.checkForUpdateAsync().get();
        checker.notifyConsole(result);

        assertFalse(result.enabled());
        assertTrue(infoLogs.isEmpty(), "Disabled checker must not log checking or info messages");
        assertTrue(warnLogs.isEmpty(), "Disabled checker must not log warnings");
    }

    @Test
    void checkingForUpdatesLoggedOnAsyncStart() throws Exception {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, "[]"), null, infoLogs::add, warnLogs::add);

        checker.checkForUpdateAsync().get();

        assertTrue(infoLogs.contains("Checking for updates..."));
    }

    @Test
    void asyncCheckCachesResult() throws Exception {
        String json = createModrinthVersionsJson("1.3.5", "release", "listed");
        UpdateChecker checker = new UpdateChecker(
                "1.3.0", true, mockCallFactory(200, json),
                Executors.newSingleThreadExecutor(), null, null);

        assertNull(checker.getCachedResult());
        assertFalse(checker.hasCachedUpdate());

        UpdateChecker.UpdateResult result = checker.checkForUpdateAsync().get();
        assertTrue(result.updateAvailable());
        assertTrue(checker.hasCachedUpdate());
        assertEquals(result, checker.getCachedResult());

        checker.shutdown();
    }
}
