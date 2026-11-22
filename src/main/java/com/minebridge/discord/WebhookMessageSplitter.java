package com.minebridge.discord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Message splitter that divides outbound Discord messages exceeding 2000 characters
 * into valid segments (&lt;= 2000 chars) while preserving markdown styling, natural
 * word boundaries, and UTF-16 surrogate pairs (emojis).
 */
public final class WebhookMessageSplitter {

    public static final int MAX_SEGMENT_LENGTH = 2000;
    private static final int SEARCH_WINDOW = 200;
    private static final int TAG_MARGIN = 32;

    private WebhookMessageSplitter() {
    }

    /**
     * Splits a message into segments of at most MAX_SEGMENT_LENGTH characters.
     */
    public static List<String> split(String message) {
        return split(message, MAX_SEGMENT_LENGTH);
    }

    public static List<String> split(String message, int maxLength) {
        if (message == null || message.isEmpty()) {
            return Collections.emptyList();
        }
        if (message.length() <= maxLength) {
            return List.of(message);
        }

        List<String> segments = new ArrayList<>();
        int len = message.length();
        int start = 0;
        Deque<String> activeStyles = new ArrayDeque<>();

        while (start < len) {
            // Prefix inherited from previous segment's open styles
            StringBuilder segmentPrefix = new StringBuilder();
            // Opening styles should be re-applied in order
            for (String tag : activeStyles) {
                segmentPrefix.append(tag);
            }

            int availableForContent = maxLength - segmentPrefix.length() - TAG_MARGIN;
            if (availableForContent < 50) {
                // Failsafe in case styling tags take too much space
                availableForContent = maxLength - segmentPrefix.length() - 8;
            }

            int endCandidate = start + availableForContent;
            if (endCandidate >= len) {
                // Last segment
                String rest = message.substring(start);
                // Scan styles in this final segment
                scanStyles(rest, activeStyles);
                segments.add(segmentPrefix + rest);
                break;
            }

            // Find best split point near endCandidate
            int splitPoint = findSplitPoint(message, start, endCandidate);

            // Surrogate pair protection: never split between high and low surrogate
            if (splitPoint > start && Character.isHighSurrogate(message.charAt(splitPoint - 1))) {
                splitPoint--;
            }

            String content = message.substring(start, splitPoint);
            scanStyles(content, activeStyles);

            // Suffix to close active styles for this segment
            StringBuilder segmentSuffix = new StringBuilder();
            // Closing styles must be applied in reverse stack order
            for (String tag : activeStyles) {
                segmentSuffix.append(tag);
            }

            segments.add(segmentPrefix.toString() + content + segmentSuffix.toString());

            // Move start index, trimming leading whitespace at split point if not a newline
            start = splitPoint;
            if (start < len && message.charAt(start) == ' ') {
                start++;
            }
        }

        return segments;
    }

    private static int findSplitPoint(String text, int start, int targetEnd) {
        int windowStart = Math.max(start + 1, targetEnd - SEARCH_WINDOW);

        // 1. Look for newline in the window
        int lastNewline = -1;
        for (int i = targetEnd; i >= windowStart; i--) {
            if (text.charAt(i) == '\n') {
                lastNewline = i + 1; // split right after the newline
                break;
            }
        }
        if (lastNewline > start) {
            return lastNewline;
        }

        // 2. Look for space in the window
        int lastSpace = -1;
        for (int i = targetEnd; i >= windowStart; i--) {
            if (text.charAt(i) == ' ') {
                lastSpace = i;
                break;
            }
        }
        if (lastSpace > start) {
            return lastSpace;
        }

        // 3. Fallback to hard split at targetEnd
        return targetEnd;
    }

    private static void scanStyles(String text, Deque<String> activeStyles) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '\\') {
                i++; // Skip escaped character
                continue;
            }

            if (i + 1 < len) {
                String twoChar = text.substring(i, i + 2);
                if ("**".equals(twoChar) || "__".equals(twoChar) || "~~".equals(twoChar) || "||".equals(twoChar)) {
                    toggleStyle(activeStyles, twoChar);
                    i++; // consume second char
                    continue;
                }
            }

            char c = text.charAt(i);
            if (c == '*' || c == '`') {
                toggleStyle(activeStyles, String.valueOf(c));
            }
        }
    }

    private static void toggleStyle(Deque<String> activeStyles, String tag) {
        if (!activeStyles.isEmpty() && activeStyles.peek().equals(tag)) {
            activeStyles.pop();
        } else {
            activeStyles.push(tag);
        }
    }
}
