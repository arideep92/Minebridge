package com.minebridge.discord;

import java.util.LinkedHashMap;

/**
 * Thread-safe sliding-window deduplicator with bounded capacity and monotonic time eviction.
 *
 * @param <K> the cache key type
 */
public final class SlidingWindowDeduplicator<K> {

    private final long windowMillis;
    private final int capacity;
    private final LinkedHashMap<K, Long> entries = new LinkedHashMap<>();

    public SlidingWindowDeduplicator(long windowMillis, int capacity) {
        if (windowMillis < 0 || capacity < 1) {
            throw new IllegalArgumentException("Invalid deduplication limits.");
        }
        this.windowMillis = windowMillis;
        this.capacity = capacity;
    }

    public synchronized boolean isDuplicate(K key, long now) {
        if (key == null) {
            return false;
        }

        var it = entries.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue() > windowMillis) {
                it.remove();
            } else {
                break;
            }
        }

        Long previous = entries.get(key);
        if (previous != null && now - previous <= windowMillis) {
            return true;
        }

        entries.put(key, now);
        while (entries.size() > capacity) {
            var keyIt = entries.keySet().iterator();
            keyIt.next();
            keyIt.remove();
        }
        return false;
    }

    public synchronized void reset() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    public int getCapacity() {
        return capacity;
    }
}
