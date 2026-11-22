package com.minebridge.discord;

import java.util.UUID;

public final class DiscordEventDeduplicator {

    private static final long DEFAULT_WINDOW_MILLIS = 10_000L;
    private static final int DEFAULT_CAPACITY = 4096;

    private final SlidingWindowDeduplicator<Key> delegate;

    public DiscordEventDeduplicator() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_CAPACITY);
    }

    DiscordEventDeduplicator(long windowMillis, int capacity) {
        this.delegate = new SlidingWindowDeduplicator<>(windowMillis, capacity);
    }

    public boolean alreadySent(UUID playerId, String eventType) {
        if (playerId == null || eventType == null) {
            return false;
        }
        return delegate.isDuplicate(new Key(playerId, eventType), System.currentTimeMillis());
    }

    private record Key(UUID playerId, String eventType) {
    }
}
