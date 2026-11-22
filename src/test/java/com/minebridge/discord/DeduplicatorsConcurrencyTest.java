package com.minebridge.discord;

import com.minebridge.achievement.AdvancementAnnouncementDeduplicator;
import com.minebridge.listener.ChatDeduplicator;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeduplicatorsConcurrencyTest {

    @Test
    void nullInputsDoNotThrowOrCorruptState() {
        DiscordEventDeduplicator eventDedup = new DiscordEventDeduplicator();
        assertFalse(eventDedup.alreadySent(null, "join"));
        assertFalse(eventDedup.alreadySent(UUID.randomUUID(), null));
        assertFalse(eventDedup.alreadySent(null, null));

        AdvancementAnnouncementDeduplicator advDedup = new AdvancementAnnouncementDeduplicator();
        assertFalse(advDedup.alreadyAnnounced(null, "story/mine_stone"));
        assertFalse(advDedup.alreadyAnnounced(UUID.randomUUID(), null));
        assertFalse(advDedup.alreadyAnnounced(null, null));

        ChatDeduplicator chatDedup = new ChatDeduplicator();
        assertFalse(chatDedup.isDuplicate(null, "hello"));
        assertFalse(chatDedup.isDuplicate(UUID.randomUUID(), null));
        assertFalse(chatDedup.isDuplicate(null, null));
    }

    @Test
    void slidingWindowAndCapacityEvictionWorkCorrectly() {
        // Window 100ms, capacity 2
        DiscordEventDeduplicator dedup = new DiscordEventDeduplicator(100L, 2);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        assertFalse(dedup.alreadySent(p1, "join"));
        assertTrue(dedup.alreadySent(p1, "join"));

        assertFalse(dedup.alreadySent(p2, "join"));
        // At capacity 2: p1, p2
        // Inserting p3 evicts oldest (p1)
        assertFalse(dedup.alreadySent(p3, "join"));

        // Now p1 was evicted due to capacity
        assertFalse(dedup.alreadySent(p1, "join"));
    }

    @Test
    void chatDeduplicatorExpiryAndCapacity() {
        ChatDeduplicator dedup = new ChatDeduplicator(50L, 2);
        UUID player = UUID.randomUUID();
        long t = 1000L;

        assertFalse(dedup.isDuplicate(player, "msg1", t));
        assertTrue(dedup.isDuplicate(player, "msg1", t + 10));

        // After window expires (t + 60 > t + 50)
        assertFalse(dedup.isDuplicate(player, "msg1", t + 60));
    }

    @Test
    void concurrentDeduplicationThreadSafety() throws InterruptedException {
        int threads = 8;
        int operations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        DiscordEventDeduplicator dedup = new DiscordEventDeduplicator(5000L, 1000);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger firstTimeCount = new AtomicInteger(0);

        UUID fixedPlayer = UUID.randomUUID();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < operations; j++) {
                        // Unique event per iteration j
                        if (!dedup.alreadySent(fixedPlayer, "event-" + j)) {
                            firstTimeCount.incrementAndGet();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Exactly 'operations' should be reported as first time (not duplicates)
        assertEquals(operations, firstTimeCount.get());
    }
}
