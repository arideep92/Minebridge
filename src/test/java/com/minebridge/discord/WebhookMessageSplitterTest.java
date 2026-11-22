package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookMessageSplitterTest {

    @Test
    void testShortMessageNotSplit() {
        String msg = "Hello world!";
        List<String> segments = WebhookMessageSplitter.split(msg);
        assertEquals(1, segments.size());
        assertEquals("Hello world!", segments.get(0));
    }

    @Test
    void testSplitsAtNaturalNewlineBoundary() {
        String line1 = "A".repeat(1850);
        String line2 = "B".repeat(500);
        String message = line1 + "\n" + line2;

        List<String> segments = WebhookMessageSplitter.split(message);
        assertEquals(2, segments.size());
        assertEquals(line1 + "\n", segments.get(0));
        assertEquals(line2, segments.get(1));
        assertTrue(segments.get(0).length() <= 2000);
        assertTrue(segments.get(1).length() <= 2000);
    }

    @Test
    void testSplitsAtNaturalSpaceBoundary() {
        String part1 = "A".repeat(1900);
        String part2 = "B".repeat(300);
        String message = part1 + " " + part2;

        List<String> segments = WebhookMessageSplitter.split(message);
        assertEquals(2, segments.size());
        assertEquals(part1, segments.get(0));
        assertEquals(part2, segments.get(1));
    }

    @Test
    void testPreservesFormattingTagsAcrossChunks() {
        // Message with open bold tag across the split boundary
        String boldPrefix = "**";
        String longBoldContent = "Word ".repeat(450); // ~2250 characters
        String message = boldPrefix + longBoldContent + "**";

        List<String> segments = WebhookMessageSplitter.split(message);
        assertTrue(segments.size() >= 2);

        // Segment 0 must close the bold tag at the end
        assertTrue(segments.get(0).endsWith("**"), "Segment 0 should end with closing bold tag");
        // Segment 1 must reopen the bold tag at the start
        assertTrue(segments.get(1).startsWith("**"), "Segment 1 should start with reopened bold tag");

        for (String seg : segments) {
            assertTrue(seg.length() <= 2000, "Segment length must be <= 2000: " + seg.length());
        }
    }

    @Test
    void testDoesNotSplitSurrogatePairs() {
        // Build a string where an emoji (surrogate pair) lands right near the split point
        String emoji = "😀"; // 2 chars (surrogate pair)
        int length = 1999;
        String filler = "A".repeat(length);
        String message = filler + emoji + "AfterEmoji";

        List<String> segments = WebhookMessageSplitter.split(message);
        assertTrue(segments.size() >= 2);
        for (String seg : segments) {
            assertTrue(seg.length() <= 2000);
            // Neither segment should start or end with a lone surrogate
            if (!seg.isEmpty()) {
                assertFalse(Character.isHighSurrogate(seg.charAt(seg.length() - 1)),
                        "Segment should not end with orphaned high surrogate");
                assertFalse(Character.isLowSurrogate(seg.charAt(0)),
                        "Segment should not start with orphaned low surrogate");
            }
        }
    }

    @Test
    void testNullAndEmptyHandling() {
        assertTrue(WebhookMessageSplitter.split(null).isEmpty());
        assertTrue(WebhookMessageSplitter.split("").isEmpty());
    }
}
