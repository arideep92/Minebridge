package com.minebridge.listener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperChatBridgeTest {

    @BeforeEach
    @AfterEach
    void reset() {
        PaperChatBridge.resetSerializerCacheForTesting();
        PaperChatBridge.setPaperAvailableForTesting(null);
    }

    public static class DummyContentComponent {
        private final String text;

        public DummyContentComponent(String text) {
            this.text = text;
        }

        public String content() {
            return text;
        }
    }

    @Test
    void extractPlainTextReturnsNullForNullInput() {
        assertNull(PaperChatBridge.extractPlainText(null));
    }

    @Test
    void extractPlainTextFallsBackToContentMethod() {
        DummyContentComponent component = new DummyContentComponent("Hello from Adventure TextComponent!");
        assertEquals("Hello from Adventure TextComponent!", PaperChatBridge.extractPlainText(component));
    }

    @Test
    void extractPlainTextReturnsNullWhenNoMethodsMatch() {
        assertNull(PaperChatBridge.extractPlainText(new Object()));
    }

    @Test
    void extractPlainTextCachesResolutionSafelyUnderConcurrency() throws InterruptedException {
        int threadCount = 8;
        int iterationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String msg = "Message-" + id + "-" + j;
                        String result = PaperChatBridge.extractPlainText(new DummyContentComponent(msg));
                        if (msg.equals(result)) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(threadCount * iterationsPerThread, successCount.get());
    }

    @Test
    void isPaperAvailableReturnsFalseWhenClassMissing() {
        PaperChatBridge.setPaperAvailableForTesting(false);
        assertFalse(PaperChatBridge.isPaperAvailable());
    }
}
