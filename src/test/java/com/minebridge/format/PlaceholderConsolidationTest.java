package com.minebridge.format;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.achievement.AdvancementMessageFormatter;
import com.minebridge.command.ServerInfoFormatter;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import com.minebridge.death.DeathMessageFormatter;
import com.minebridge.discord.RelayMessageFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderConsolidationTest {

    @Test
    void testRelayMessageFormatterReplacePlaceholders() {
        assertEquals("", RelayMessageFormatter.replacePlaceholders(null, Map.of("key", "val")));

        String template = "Player {player} scored {score} points on {server}!";
        Map<String, String> values = Map.of(
                "player", "Alice",
                "score", "42",
                "server", "Survival"
        );
        assertEquals("Player Alice scored 42 points on Survival!",
                RelayMessageFormatter.replacePlaceholders(template, values));
    }

    @Test
    void testUnmatchedPlaceholdersRemainIntact() {
        String template = "Hello {user}, welcome to {unknown_place}!";
        Map<String, String> values = Map.of("user", "Bob");
        assertEquals("Hello Bob, welcome to {unknown_place}!",
                RelayMessageFormatter.replacePlaceholders(template, values));
    }

    @Test
    void testEmptyAndMalformedPlaceholders() {
        assertEquals("Empty {}", RelayMessageFormatter.replacePlaceholders("Empty {}", Map.of("other", "val")));
        assertEquals("Unclosed {placeholder", RelayMessageFormatter.replacePlaceholders("Unclosed {placeholder", Map.of("placeholder", "val")));
        assertEquals("Plain text without tokens", RelayMessageFormatter.replacePlaceholders("Plain text without tokens", Map.of("foo", "bar")));
    }

    @Test
    void testFormattersProduceExpectedOutput() {
        // ServerInfoFormatter
        String serverStatus = ServerInfoFormatter.formatServerStatus("{online}/{max} players", 12, 100);
        assertEquals("12/100 players", serverStatus);

        String playerList = ServerInfoFormatter.formatPlayerList("Online ({online}/{max}): {players}", 2, 10, List.of("Alice", "Bob"));
        assertEquals("Online (2/10): Alice, Bob", playerList);

        // DeathMessageFormatter
        DeathContext deathContext = new DeathContext("Alex", "Zombified Piglin", "drained", DeathCategory.MOB_KILL);
        String death = DeathMessageFormatter.formatDiscord("{victim} met their doom by {killer} ({category})", deathContext, "oof");
        assertEquals("Alex met their doom by Zombified Piglin (Mob kill)", death);

        // AdvancementMessageFormatter
        AdvancementContext advContext = new AdvancementContext("Steve", "Stone Age", "Mine stone", AdvancementCategory.STORY, false);
        String adv = AdvancementMessageFormatter.formatDiscord("{player} made [{title}]: {description} ({category})", advContext);
        assertEquals("Steve made [Stone Age]: Mine stone (Story)", adv);
    }
}
