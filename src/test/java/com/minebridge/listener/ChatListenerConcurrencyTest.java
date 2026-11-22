package com.minebridge.listener;

import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.WebhookService;
import com.minebridge.hook.ChatMetadataService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatListenerConcurrencyTest {

    @Test
    void concurrentUpdateServicesAndAccessIsThreadSafe() throws InterruptedException {
        ChatMetadataService meta = new ChatMetadataService();
        ChatListener listener = new ChatListener(null, null, null, null, meta);

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 500; j++) {
                        if (id % 2 == 0) {
                            // Writer thread
                            listener.updateServices(null, null);
                        } else {
                            // Reader / caller
                            listener.handlePaperChat(null, "Test message " + j);
                        }
                    }
                } catch (Throwable t) {
                    errorOccurred.set(true);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertFalse(errorOccurred.get(), "Concurrent access to ChatListener should not throw");
    }
}
