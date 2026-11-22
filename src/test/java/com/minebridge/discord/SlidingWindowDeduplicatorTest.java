package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowDeduplicatorTest {

    @Test
    void validatesConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowDeduplicator<String>(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowDeduplicator<String>(1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowDeduplicator<String>(1000, -5));
    }

    @Test
    void handlesNullKeysGracefully() {
        SlidingWindowDeduplicator<String> deduplicator = new SlidingWindowDeduplicator<>(5000, 10);
        assertFalse(deduplicator.isDuplicate(null, 1000));
        assertEquals(0, deduplicator.size());
    }

    @Test
    void detectsDuplicatesWithinWindow() {
        SlidingWindowDeduplicator<String> deduplicator = new SlidingWindowDeduplicator<>(5000, 10);

        assertFalse(deduplicator.isDuplicate("msg-1", 1000));
        assertTrue(deduplicator.isDuplicate("msg-1", 2000));
        assertTrue(deduplicator.isDuplicate("msg-1", 6000)); // Exactly at window boundary (5000ms delta)
        assertFalse(deduplicator.isDuplicate("msg-2", 6000));
    }

    @Test
    void allowsDuplicatesAfterWindowExpires() {
        SlidingWindowDeduplicator<String> deduplicator = new SlidingWindowDeduplicator<>(5000, 10);

        assertFalse(deduplicator.isDuplicate("msg-1", 1000));
        // After 5001ms, window expired
        assertFalse(deduplicator.isDuplicate("msg-1", 6001));
        // And now duplicate relative to 6001
        assertTrue(deduplicator.isDuplicate("msg-1", 7000));
    }

    @Test
    void evictsOldestWhenCapacityExceeded() {
        SlidingWindowDeduplicator<String> deduplicator = new SlidingWindowDeduplicator<>(60000, 3);

        assertFalse(deduplicator.isDuplicate("k1", 100));
        assertFalse(deduplicator.isDuplicate("k2", 200));
        assertFalse(deduplicator.isDuplicate("k3", 300));
        assertEquals(3, deduplicator.size());

        // Adding 4th element evicts k1
        assertFalse(deduplicator.isDuplicate("k4", 400));
        assertEquals(3, deduplicator.size());

        // k1 was evicted, so it is no longer duplicate
        assertFalse(deduplicator.isDuplicate("k1", 500));
    }

    @Test
    void resetsCorrectly() {
        SlidingWindowDeduplicator<String> deduplicator = new SlidingWindowDeduplicator<>(5000, 10);
        assertFalse(deduplicator.isDuplicate("k1", 100));
        assertEquals(1, deduplicator.size());

        deduplicator.reset();
        assertEquals(0, deduplicator.size());
        assertFalse(deduplicator.isDuplicate("k1", 100));
    }
}
