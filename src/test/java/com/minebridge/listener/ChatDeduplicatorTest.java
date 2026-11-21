package com.minebridge.listener;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatDeduplicatorTest {

    @Test
    void acceptsFirstMessage() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(2_000L, 100);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.isDuplicate(playerId, "hello world"),
                "First occurrence of a message must not be marked duplicate");
    }

    @Test
    void rejectsImmediateDuplicateMessage() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(2_000L, 100);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.isDuplicate(playerId, "hello world", 1000L));
        assertTrue(deduplicator.isDuplicate(playerId, "hello world", 1001L),
                "Immediate duplicate from same player must be marked duplicate");
    }

    @Test
    void acceptsDifferentMessageFromSameSender() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(2_000L, 100);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.isDuplicate(playerId, "first message", 1000L));
        assertFalse(deduplicator.isDuplicate(playerId, "second message", 1001L),
                "Different message content from same player must be accepted");
    }

    @Test
    void acceptsSameMessageFromDifferentSender() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(2_000L, 100);
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertFalse(deduplicator.isDuplicate(playerA, "gg", 1000L));
        assertFalse(deduplicator.isDuplicate(playerB, "gg", 1001L),
                "Same message from different players must both be accepted");
    }

    @Test
    void acceptsSameMessageAfterWindowExpires() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(2_000L, 100);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.isDuplicate(playerId, "hello", 1000L));
        assertTrue(deduplicator.isDuplicate(playerId, "hello", 2500L),
                "Message within 2000ms window must be rejected");
        assertFalse(deduplicator.isDuplicate(playerId, "hello", 3001L),
                "Message after 2000ms window has elapsed must be accepted");
    }

    @Test
    void resetsCleanly() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(2_000L, 100);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.isDuplicate(playerId, "hello", 1000L));
        deduplicator.reset();
        assertFalse(deduplicator.isDuplicate(playerId, "hello", 1001L),
                "After reset, previously seen message must be accepted");
    }
}
