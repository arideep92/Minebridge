package com.minebridge.discord;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for resolving player skin head avatar URLs with an in-memory,
 * bounded, thread-safe TTL cache. Strictly non-blocking.
 */
public final class SkinAvatarService {

    public static final String DEFAULT_AVATAR_TEMPLATE =
            "https://mc-heads.net/avatar/{uuid}/100";
    public static final String DEFAULT_STEVE_FALLBACK =
            "https://mc-heads.net/avatar/Steve/100";
    public static final long DEFAULT_CACHE_TTL_NANOS =
            TimeUnit.MINUTES.toNanos(60);
    public static final int MAX_CACHE_ENTRIES = 2000;

    public enum Provider {
        MC_HEADS("https://mc-heads.net/avatar/{uuid}/100"),
        CRAFATAR("https://crafatar.com/avatars/{uuid}?size=100&overlay"),
        MINOTAR("https://minotar.net/avatar/{player}/100"),
        CUSTOM("");

        private final String template;

        Provider(String template) {
            this.template = template;
        }

        public String getTemplate() {
            return template;
        }
    }

    private static final class CacheEntry {
        final String avatarUrl;
        final long expiresAtNanos;

        CacheEntry(String avatarUrl, long expiresAtNanos) {
            this.avatarUrl = avatarUrl;
            this.expiresAtNanos = expiresAtNanos;
        }
    }

    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final int maxEntries;

    public SkinAvatarService() {
        this(DEFAULT_CACHE_TTL_NANOS, MAX_CACHE_ENTRIES);
    }

    public SkinAvatarService(long ttlNanos, int maxEntries) {
        this.ttlNanos = ttlNanos > 0 ? ttlNanos : DEFAULT_CACHE_TTL_NANOS;
        this.maxEntries = maxEntries > 0 ? maxEntries : MAX_CACHE_ENTRIES;
    }

    /**
     * Resolves avatar URL for a player using template and server name.
     * Non-blocking and cached.
     */
    public String getAvatarUrl(UUID uuid, String playerName, String template, String serverName) {
        if (template != null) {
            String trimmed = template.trim();
            if ("none".equalsIgnoreCase(trimmed) || "disabled".equalsIgnoreCase(trimmed)) {
                return "";
            }
        }

        long now = System.nanoTime();

        if (uuid != null) {
            CacheEntry existing = cache.get(uuid);
            if (existing != null) {
                if (now < existing.expiresAtNanos) {
                    return existing.avatarUrl;
                } else {
                    cache.remove(uuid, existing);
                }
            }
        }

        String resolved = resolveUrl(uuid, playerName, template, serverName);

        if (uuid != null && !resolved.isEmpty()) {
            if (cache.size() >= maxEntries) {
                pruneExpiredOrOldest(now);
            }
            if (cache.size() < maxEntries) {
                cache.put(uuid, new CacheEntry(resolved, now + ttlNanos));
            }
        }

        return resolved;
    }

    public static String resolveUrl(UUID uuid, String playerName, String template, String serverName) {
        String effectiveTemplate = template != null && !template.isBlank()
                ? template.trim() : DEFAULT_AVATAR_TEMPLATE;

        String safePlayer = (playerName != null && !playerName.isBlank()) ? playerName : "Steve";
        String safeUuid = uuid != null ? uuid.toString() : safePlayer;
        String safeServer = serverName != null ? serverName : "";

        return effectiveTemplate
                .replace("{uuid}", safeUuid)
                .replace("{player}", safePlayer)
                .replace("{server}", safeServer);
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
        }
    }

    public void clear() {
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }

    private void pruneExpiredOrOldest(long now) {
        Iterator<Map.Entry<UUID, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CacheEntry> entry = it.next();
            if (now >= entry.getValue().expiresAtNanos) {
                it.remove();
            }
        }
        if (cache.size() >= maxEntries) {
            // Remove one arbitrary entry to prevent unbound growth
            Iterator<UUID> keyIt = cache.keySet().iterator();
            if (keyIt.hasNext()) {
                keyIt.next();
                keyIt.remove();
            }
        }
    }
}
