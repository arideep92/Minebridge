package com.minebridge.listener;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Deduplicates chat messages across multiple chat listeners, integration modes,
 * and Paper/Spigot dual-event dispatch pipelines.
 */
public final class ChatDeduplicator {

    private static final long DEFAULT_WINDOW_MILLIS = 2_000L;
    private static final int DEFAULT_CAPACITY = 4096;

    private final long windowMillis;
    private final int capacity;
    private final LinkedHashMap<Key, Long> messages = new LinkedHashMap<>();

    public ChatDeduplicator() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_CAPACITY);
    }

    public ChatDeduplicator(long windowMillis, int capacity) {
        if (windowMillis < 0 || capacity < 1) {
            throw new IllegalArgumentException("Invalid chat deduplication limits.");
        }
        this.windowMillis = windowMillis;
        this.capacity = capacity;
    }

    public synchronized boolean isDuplicate(UUID playerId, String message) {
        return isDuplicate(playerId, message, System.currentTimeMillis());
    }

    public synchronized boolean isDuplicate(UUID playerId, String message, long now) {
        if (playerId == null || message == null) {
            return false;
        }

        messages.entrySet().removeIf(entry -> now - entry.getValue() > windowMillis);

        Key key = new Key(playerId, message);
        Long previous = messages.get(key);
        if (previous != null && now - previous <= windowMillis) {
            return true;
        }

        messages.put(key, now);
        while (messages.size() > capacity) {
            messages.remove(messages.keySet().iterator().next());
        }
        return false;
    }

    public synchronized void reset() {
        messages.clear();
    }

    private record Key(UUID playerId, String message) {
    }
}
