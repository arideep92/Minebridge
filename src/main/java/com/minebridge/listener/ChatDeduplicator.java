package com.minebridge.listener;

import com.minebridge.discord.SlidingWindowDeduplicator;

import java.util.UUID;

/**
 * Deduplicates chat messages across multiple chat listeners, integration modes,
 * and Paper/Spigot dual-event dispatch pipelines.
 */
public final class ChatDeduplicator {

    private static final long DEFAULT_WINDOW_MILLIS = 2_000L;
    private static final int DEFAULT_CAPACITY = 4096;

    private final SlidingWindowDeduplicator<Key> delegate;

    public ChatDeduplicator() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_CAPACITY);
    }

    public ChatDeduplicator(long windowMillis, int capacity) {
        this.delegate = new SlidingWindowDeduplicator<>(windowMillis, capacity);
    }

    public boolean isDuplicate(UUID playerId, String message) {
        return isDuplicate(playerId, message, System.currentTimeMillis());
    }

    public boolean isDuplicate(UUID playerId, String message, long now) {
        if (playerId == null || message == null) {
            return false;
        }
        return delegate.isDuplicate(new Key(playerId, message), now);
    }

    public void reset() {
        delegate.reset();
    }

    private record Key(UUID playerId, String message) {
    }
}
