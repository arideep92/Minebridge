package com.minebridge.update;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Checks for newer MineBridge releases via the public Modrinth API.
 * Designed for asynchronous, one-shot execution during plugin startup.
 * Never performs HTTP requests on the Bukkit main thread.
 *
 * <p>Endpoint: {@code GET https://api.modrinth.com/v2/project/minebridge/version}
 *
 * <p>Only published, listed release versions are considered. Alpha and beta
 * versions are ignored unless explicitly configured. An update-check failure never
 * prevents MineBridge from starting.
 */
public final class UpdateChecker {

    public static final String MODRINTH_API_URL =
            "https://api.modrinth.com/v2/project/minebridge/version";
    public static final String PROJECT_SLUG = "minebridge";
    public static final String ADMIN_PERMISSION = "minebridge.admin";
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 10;

    /**
     * Result of checking for an available update.
     */
    public record UpdateResult(
            boolean updateAvailable,
            String installedVersion,
            String latestVersion,
            String releaseUrl,
            boolean enabled,
            String error
    ) {
        public static UpdateResult disabled() {
            return new UpdateResult(false, null, null, null, false, null);
        }

        public static UpdateResult failed(String error) {
            return new UpdateResult(false, null, null, null, true, error);
        }

        public static UpdateResult upToDate(String installedVersion) {
            return new UpdateResult(false, installedVersion, installedVersion, null, true, null);
        }

        public static UpdateResult available(
                String installedVersion,
                String latestVersion,
                String releaseUrl) {
            return new UpdateResult(true, installedVersion, latestVersion, releaseUrl, true, null);
        }
    }

    /**
     * Represents a single version entry from the Modrinth API.
     */
    public record ModrinthVersion(
            String versionNumber,
            String name,
            String status,
            String versionType,
            String releaseUrl
    ) {}

    /**
     * Semantic version representation supporting major.minor.patch[.build] comparison.
     * Missing segments default to 0. Leading "v" and build metadata / pre-release suffixes
     * are safely parsed.
     */
    public static final class SemanticVersion implements Comparable<SemanticVersion> {
        private final int major;
        private final int minor;
        private final int patch;
        private final int build;
        private final String preRelease;
        private final String buildMetadata;

        public SemanticVersion(int major, int minor, int patch) {
            this(major, minor, patch, 0, null, null);
        }

        public SemanticVersion(int major, int minor, int patch, int build) {
            this(major, minor, patch, build, null, null);
        }

        public SemanticVersion(
                int major,
                int minor,
                int patch,
                int build,
                String preRelease,
                String buildMetadata) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.build = build;
            this.preRelease = (preRelease != null && !preRelease.isBlank()) ? preRelease.trim() : null;
            this.buildMetadata = (buildMetadata != null && !buildMetadata.isBlank()) ? buildMetadata.trim() : null;
        }

        /**
         * Parses a version string like "1.3.0", "1.3", "1", "v1.3.0", or "1.3.0-SNAPSHOT".
         * Returns {@code null} if parsing fails entirely or yields negative segments.
         */
        public static SemanticVersion parse(String versionString) {
            if (versionString == null || versionString.isBlank()) {
                return null;
            }
            String v = versionString.trim();
            if (v.startsWith("v") || v.startsWith("V")) {
                v = v.substring(1).trim();
            }
            String buildMeta = null;
            int plusIndex = v.indexOf('+');
            if (plusIndex >= 0) {
                buildMeta = v.substring(plusIndex + 1).trim();
                v = v.substring(0, plusIndex);
            }
            String preRel = null;
            int dashIndex = v.indexOf('-');
            if (dashIndex >= 0) {
                preRel = v.substring(dashIndex + 1).trim();
                v = v.substring(0, dashIndex);
            }
            String[] parts = v.split("\\.");
            if (parts.length == 0 || parts[0].isBlank()) {
                return null;
            }
            try {
                int major = Integer.parseInt(parts[0].trim());
                int minor = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                int patch = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
                int build = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 0;
                if (major < 0 || minor < 0 || patch < 0 || build < 0) {
                    return null;
                }
                return new SemanticVersion(major, minor, patch, build, preRel, buildMeta);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public int major() { return major; }
        public int minor() { return minor; }
        public int patch() { return patch; }
        public int build() { return build; }
        public String preRelease() { return preRelease; }
        public String buildMetadata() { return buildMetadata; }
        public boolean isPreRelease() { return preRelease != null; }

        public boolean isGreaterThan(SemanticVersion other) {
            return other != null && compareTo(other) > 0;
        }

        @Override
        public int compareTo(SemanticVersion other) {
            if (other == null) {
                return 1;
            }
            if (this.major != other.major) {
                return Integer.compare(this.major, other.major);
            }
            if (this.minor != other.minor) {
                return Integer.compare(this.minor, other.minor);
            }
            if (this.patch != other.patch) {
                return Integer.compare(this.patch, other.patch);
            }
            if (this.build != other.build) {
                return Integer.compare(this.build, other.build);
            }
            // SemVer 2.0.0 Rule 9: Normal version has higher precedence than pre-release
            if (this.preRelease == null && other.preRelease != null) {
                return 1;
            }
            if (this.preRelease != null && other.preRelease == null) {
                return -1;
            }
            if (this.preRelease != null && other.preRelease != null) {
                return comparePreRelease(this.preRelease, other.preRelease);
            }
            // SemVer 2.0.0 Rule 10: Build metadata is ignored for precedence
            return 0;
        }

        private static int comparePreRelease(String pre1, String pre2) {
            String[] parts1 = pre1.split("\\.");
            String[] parts2 = pre2.split("\\.");
            int len = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < len; i++) {
                if (i >= parts1.length) return -1;
                if (i >= parts2.length) return 1;
                String p1 = parts1[i];
                String p2 = parts2[i];
                boolean num1 = p1.matches("\\d+");
                boolean num2 = p2.matches("\\d+");
                if (num1 && num2) {
                    int n1 = Integer.parseInt(p1);
                    int n2 = Integer.parseInt(p2);
                    if (n1 != n2) return Integer.compare(n1, n2);
                } else if (num1) {
                    return -1;
                } else if (num2) {
                    return 1;
                } else {
                    int c = p1.compareTo(p2);
                    if (c != 0) return c;
                }
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SemanticVersion that)) return false;
            return major == that.major && minor == that.minor && patch == that.patch
                    && build == that.build && Objects.equals(preRelease, that.preRelease);
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, patch, build, preRelease);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(major).append('.').append(minor).append('.').append(patch);
            if (preRelease != null) {
                sb.append('-').append(preRelease);
            }
            if (buildMetadata != null) {
                sb.append('+').append(buildMetadata);
            }
            return sb.toString();
        }
    }

    private final String installedVersion;
    private final boolean enabled;
    private final Call.Factory callFactory;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final java.util.Set<String> notifiedRecipients = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Call activeCall;
    private volatile UpdateResult cachedResult;

    /**
     * Creates a production {@code UpdateChecker}.
     *
     * @param installedVersion the currently installed MineBridge plugin version
     * @param enabled          whether the update checker is enabled in configuration
     */
    public UpdateChecker(String installedVersion, boolean enabled) {
        this(installedVersion, enabled, null, null, null, null);
    }

    public UpdateChecker(
            String installedVersion,
            boolean enabled,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        this(installedVersion, enabled, null, null, infoLogger, warningLogger);
    }

    public UpdateChecker(
            String installedVersion,
            boolean enabled,
            Call.Factory callFactory,
            ExecutorService executor,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        this.installedVersion = installedVersion != null ? installedVersion.trim() : "1.3.0";
        this.enabled = enabled;
        this.infoLogger = infoLogger != null ? infoLogger : msg -> {};
        this.warningLogger = warningLogger != null ? warningLogger : msg -> {};

        if (callFactory == null) {
            this.callFactory = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .readTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                    .build();
        } else {
            this.callFactory = callFactory;
        }

        if (executor == null) {
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MineBridge-UpdateChecker");
                thread.setDaemon(true);
                return thread;
            });
            this.ownsExecutor = true;
        } else {
            this.executor = executor;
            this.ownsExecutor = false;
        }
    }

    /**
     * Performs an asynchronous update check on the background executor.
     *
     * @return a {@link CompletableFuture} completed with the {@link UpdateResult}
     */
    public CompletableFuture<UpdateResult> checkForUpdateAsync() {
        if (!enabled) {
            UpdateResult res = UpdateResult.disabled();
            this.cachedResult = res;
            return CompletableFuture.completedFuture(res);
        }
        infoLogger.accept("Checking for updates...");
        return CompletableFuture.supplyAsync(this::checkForUpdate, executor)
                .thenApply(res -> {
                    this.cachedResult = res;
                    return res;
                });
    }

    /**
     * Performs a synchronous update check against the Modrinth API.
     * Must be called on a background thread (never on Bukkit's main thread).
     *
     * @return an {@link UpdateResult} describing the outcome
     */
    public UpdateResult checkForUpdate() {
        if (!enabled) {
            return UpdateResult.disabled();
        }

        Request request = new Request.Builder()
                .url(MODRINTH_API_URL)
                .get()
                .header("User-Agent", "MineBridge/" + installedVersion + " (https://github.com/Aridro/MineBridge)")
                .build();

        Call call = callFactory.newCall(request);
        this.activeCall = call;
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                return UpdateResult.failed("HTTP " + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return UpdateResult.failed("Empty response body");
            }

            String body = responseBody.string();
            if (body.isBlank()) {
                return UpdateResult.failed("Empty response body");
            }

            DataArray array;
            try {
                array = DataArray.fromJson(body);
            } catch (Exception e) {
                return UpdateResult.failed("Malformed API response: " + e.getMessage());
            }

            List<ModrinthVersion> versions = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                DataObject obj = array.getObject(i);
                String versionNumber = obj.getString("version_number", null);
                String versionType = obj.getString("version_type", null);
                String status = obj.getString("status", "listed");
                String name = obj.getString("name", versionNumber != null ? versionNumber : "");

                // Only consider listed releases. Ignore alphas, betas, and unlisted/archived versions.
                if (!"listed".equalsIgnoreCase(status) || !"release".equalsIgnoreCase(versionType)) {
                    continue;
                }
                if (versionNumber == null || versionNumber.isBlank()) {
                    continue;
                }
                SemanticVersion parsed = SemanticVersion.parse(versionNumber);
                if (parsed == null || parsed.isPreRelease()) {
                    continue;
                }

                String releaseUrl = "https://modrinth.com/plugin/minebridge/version/" + versionNumber;
                versions.add(new ModrinthVersion(versionNumber, name, status, versionType, releaseUrl));
            }

            if (versions.isEmpty()) {
                return UpdateResult.failed("No listed release versions found");
            }

            ModrinthVersion latest = findLatestRelease(versions);
            if (latest == null) {
                return UpdateResult.failed("No listed release versions found");
            }

            String latestVersion = latest.versionNumber();
            if (isNewerThan(latestVersion, installedVersion)) {
                return UpdateResult.available(installedVersion, latestVersion, latest.releaseUrl());
            }

            return UpdateResult.upToDate(installedVersion);
        } catch (Exception exception) {
            return UpdateResult.failed(exception.getClass().getSimpleName());
        } finally {
            this.activeCall = null;
        }
    }

    /**
     * Displays update notification in the server console.
     */
    public void notifyConsole(
            UpdateResult result,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        if (!enabled || result == null || !result.enabled()) {
            return;
        }
        if (result.updateAvailable()) {
            if (infoLogger != null) {
                infoLogger.accept(String.format(
                        "A new version of MineBridge is available: %s (installed: %s)",
                        result.latestVersion(), result.installedVersion()));
                infoLogger.accept(String.format(
                        "Modrinth download URL: %s",
                        result.releaseUrl()));
            }
        } else if (result.error() != null) {
            if (warningLogger != null) {
                warningLogger.accept("Could not check for updates: " + result.error());
            }
        } else {
            if (infoLogger != null) {
                String ver = result.installedVersion();
                if (ver != null && (ver.startsWith("v") || ver.startsWith("V"))) {
                    ver = ver.substring(1);
                }
                infoLogger.accept(String.format("MineBridge is up to date (v%s).", ver));
            }
        }
    }

    public void notifyConsole(UpdateResult result) {
        notifyConsole(result, this.infoLogger, this.warningLogger);
    }

    public void notifyConsole(UpdateResult result, Consumer<String> consoleLogger) {
        notifyConsole(result, consoleLogger, consoleLogger);
    }

    /**
     * Notifies all online administrators with the {@code minebridge.admin} permission.
     * Ordinary players without this permission are strictly excluded.
     */
    public void notifyAdmins(UpdateResult result, Iterable<? extends CommandSender> recipients) {
        if (!result.updateAvailable() || recipients == null) {
            return;
        }
        for (CommandSender recipient : recipients) {
            notifyIfAdmin(recipient, result);
        }
    }

    /**
     * Delivers an update notification to a single recipient if and only if they hold
     * the {@code minebridge.admin} permission.
     */
    public void notifyIfAdmin(CommandSender recipient, UpdateResult result) {
        if (!result.updateAvailable() || recipient == null) {
            return;
        }
        if (recipient.hasPermission(ADMIN_PERMISSION)) {
            String recipientId;
            if (recipient instanceof org.bukkit.entity.Player player && player.getUniqueId() != null) {
                recipientId = player.getUniqueId().toString();
            } else if (recipient.getName() != null && !recipient.getName().isBlank()) {
                recipientId = recipient.getName();
            } else {
                recipientId = String.valueOf(System.identityHashCode(recipient));
            }
            if (!notifiedRecipients.add(recipientId)) {
                return;
            }
            recipient.sendMessage("§b[MineBridge] §eA newer release is available: §a"
                    + result.latestVersion() + " §7(installed: §f" + result.installedVersion() + "§7)");
            recipient.sendMessage("§b[MineBridge] §eDownload on Modrinth: §f" + result.releaseUrl());
        }
    }

    /**
     * Cleans up the background executor service on plugin shutdown.
     */
    public void shutdown() {
        Call call = this.activeCall;
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Checks whether the background executor has been shut down.
     */
    public boolean isShutdown() {
        return executor == null || executor.isShutdown();
    }

    /**
     * Returns the cached result of the last update check, or {@code null} if no check has completed.
     */
    public UpdateResult getCachedResult() {
        return cachedResult;
    }

    /**
     * Returns true if a newer update is available based on cached results.
     */
    public boolean hasCachedUpdate() {
        return cachedResult != null && cachedResult.updateAvailable();
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Finds the latest release version using semantic version ordering.
     */
    private static ModrinthVersion findLatestRelease(List<ModrinthVersion> versions) {
        if (versions.isEmpty()) {
            return null;
        }

        ModrinthVersion latest = versions.get(0);
        SemanticVersion latestParsed = SemanticVersion.parse(latest.versionNumber());

        for (int i = 1; i < versions.size(); i++) {
            ModrinthVersion candidate = versions.get(i);
            SemanticVersion candidateParsed = SemanticVersion.parse(candidate.versionNumber());
            if (candidateParsed != null && (latestParsed == null || candidateParsed.compareTo(latestParsed) > 0)) {
                latest = candidate;
                latestParsed = candidateParsed;
            }
        }
        return latest;
    }

    /**
     * Returns true if {@code remote} is strictly greater than {@code installed} according to semver.
     */
    public static boolean isNewerThan(String remote, String installed) {
        SemanticVersion remoteVer = SemanticVersion.parse(remote);
        SemanticVersion installedVer = SemanticVersion.parse(installed);
        if (remoteVer == null || installedVer == null) {
            return false;
        }
        return remoteVer.isGreaterThan(installedVer);
    }
}
