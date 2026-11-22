package com.minebridge.achievement;

import com.minebridge.discord.SlidingWindowDeduplicator;

import java.util.UUID;

public final class AdvancementAnnouncementDeduplicator {

    private static final long DEFAULT_WINDOW_MILLIS = 10_000L;
    private static final int DEFAULT_CAPACITY = 4096;

    private final SlidingWindowDeduplicator<Key> delegate;

    public AdvancementAnnouncementDeduplicator() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_CAPACITY);
    }

    AdvancementAnnouncementDeduplicator(long windowMillis, int capacity) {
        this.delegate = new SlidingWindowDeduplicator<>(windowMillis, capacity);
    }

    public boolean alreadyAnnounced(UUID playerId, String advancementKey) {
        if (playerId == null || advancementKey == null) {
            return false;
        }
        return delegate.isDuplicate(new Key(playerId, advancementKey), System.currentTimeMillis());
    }

    private record Key(UUID playerId, String advancementKey) {
    }
}
