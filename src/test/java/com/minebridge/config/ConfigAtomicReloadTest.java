package com.minebridge.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfigAtomicReloadTest {

    private static final String VALID_TOKEN = "synthetic-valid-token-12345";
    private static final String VALID_CHANNEL = "123456789012345678";

    private YamlConfiguration createTestConfig(String serverName) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("discord.mode", "BOT");
        config.set("discord.bot-token", VALID_TOKEN);
        config.set("discord.channel-id", VALID_CHANNEL);
        config.set("server.name", serverName);
        config.set("features.chat-relay", true);
        config.set("features.join-leave", true);
        config.set("features.server-status", true);
        config.set("features.player-list", true);
        return config;
    }

    @Test
    @DisplayName("Commit updates Settings and ConfigSnapshot atomically on successful reload")
    void commitsValidConfigurationAtomically() {
        ConfigManager manager = new ConfigManager(null);

        YamlConfiguration config = createTestConfig("Updated Server Name");

        ConfigManager.Candidate candidate = manager.candidateFrom(config, null);
        assertTrue(candidate.valid(), "Candidate must be valid: " + candidate.errors());

        manager.commit(candidate);

        assertEquals("Updated Server Name", manager.serverName());
        assertEquals(VALID_TOKEN, manager.getBotToken());
        assertEquals(VALID_CHANNEL, manager.getChannelId());
        assertEquals("Updated Server Name", manager.snapshot().serverName());
        assertEquals(VALID_TOKEN, manager.snapshot().botToken());
    }

    @Test
    @DisplayName("Invalid candidate preserves existing Settings and Snapshot with zero mutation")
    void preservesExistingStateOnFailedReload() {
        ConfigManager manager = new ConfigManager(null);

        // 1. Initial valid configuration
        YamlConfiguration initialConfig = createTestConfig("Original Stable Server");

        ConfigManager.Candidate initialCandidate = manager.candidateFrom(initialConfig, null);
        assertTrue(initialCandidate.valid(), "Initial candidate must be valid: " + initialCandidate.errors());
        manager.commit(initialCandidate);

        assertEquals("Original Stable Server", manager.serverName());

        // 2. Candidate with invalid configuration (e.g. malformed channel ID and missing token)
        YamlConfiguration invalidConfig = new YamlConfiguration();
        invalidConfig.set("discord.mode", "BOT");
        invalidConfig.set("discord.bot-token", "");
        invalidConfig.set("discord.channel-id", "not-a-valid-channel");
        invalidConfig.set("server.name", "Corrupted Server Name");

        ConfigManager.Candidate invalidCandidate = manager.candidateFrom(invalidConfig, null);
        assertFalse(invalidCandidate.valid());
        assertFalse(invalidCandidate.errors().isEmpty());

        // In reload lifecycle, commit is skipped when candidate is invalid
        // Verify attempting to commit throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> manager.commit(invalidCandidate));

        // State remains 100% untouched
        assertEquals("Original Stable Server", manager.serverName());
        assertEquals(VALID_TOKEN, manager.getBotToken());
        assertEquals(VALID_CHANNEL, manager.getChannelId());
        assertEquals("Original Stable Server", manager.snapshot().serverName());
    }

    @Test
    @DisplayName("Thread-safety: concurrent readers see atomic snapshot transitions")
    void concurrentReadersObserveConsistentState() throws Exception {
        ConfigManager manager = new ConfigManager(null);

        YamlConfiguration configA = createTestConfig("Server Alpha");
        YamlConfiguration configB = createTestConfig("Server Beta");

        ConfigManager.Candidate candA = manager.candidateFrom(configA, null);
        ConfigManager.Candidate candB = manager.candidateFrom(configB, null);
        assertTrue(candA.valid(), "CandA valid: " + candA.errors());
        assertTrue(candB.valid(), "CandB valid: " + candB.errors());
        manager.commit(candA);

        int readerThreads = 4;
        CountDownLatch latch = new CountDownLatch(readerThreads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < readerThreads; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        ConfigSnapshot snap = manager.snapshot();
                        ConfigManager.Settings sett = manager.currentSettings();
                        String snapName = snap.serverName();
                        String settName = sett.serverName();
                        if (!"Server Alpha".equals(snapName) && !"Server Beta".equals(snapName)) {
                            throw new IllegalStateException("Corrupted snapshot name: " + snapName);
                        }
                        if (!"Server Alpha".equals(settName) && !"Server Beta".equals(settName)) {
                            throw new IllegalStateException("Corrupted settings name: " + settName);
                        }
                    }
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Concurrently commit updates
        for (int k = 0; k < 200; k++) {
            manager.commit(k % 2 == 0 ? candB : candA);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Readers should finish in time");
        assertNull(failure.get(), "No thread safety errors observed");
    }
}
