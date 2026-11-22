package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.config.ConfigManager;
import com.minebridge.config.ConfigManager.DiscordEventsSettings;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimalisticNotificationRedesignTest {

    @Test
    void verifiesPlainTextMinecraftToDiscordChatWithPrefixAndSuffix() {
        String template = "{prefix}**{player}**{suffix}: {message}";
        String formatted = RelayMessageFormatter.formatForDiscord(
                template, "Steve", "Steve", "Hello everyone!",
                Instant.now().toString(), "Survival SMP", "[Admin]", "[VIP]", "world", "world");

        assertEquals("\\[Admin\\] **Steve** \\[VIP\\]: Hello everyone!", formatted);

        // Plain format without bold when configured
        String plainTemplate = "{prefix}{player}{suffix}: {message}";
        String plainFormatted = RelayMessageFormatter.formatForDiscord(
                plainTemplate, "Steve", "Steve", "Hello everyone!",
                Instant.now().toString(), "Survival SMP", "[Admin]", "", "world", "world");
        assertEquals("\\[Admin\\] Steve: Hello everyone!", plainFormatted);

        // When prefix is empty, no awkward spacing
        String noPrefix = RelayMessageFormatter.formatForDiscord(
                plainTemplate, "Steve", "Steve", "Hello everyone!",
                Instant.now().toString(), "Survival SMP", "", "", "world", "world");
        assertEquals("Steve: Hello everyone!", noPrefix);
    }

    @Test
    void verifiesPlainTextDiscordToMinecraftChat() {
        String template = "&8[&bDiscord&8] &7{player}&8: &f{message}";
        String formatted = RelayMessageFormatter.formatForMinecraft(
                template, "Arix", "Arix#1234", "Arix", "Hello in-game!", "Survival SMP");

        assertEquals("&8[&bDiscord&8] &7Arix&8: &fHello in-game!", formatted);
        assertEquals("§8[§bDiscord§8] §7Arix§8: §fHello in-game!", org.bukkit.ChatColor.translateAlternateColorCodes('&', formatted));
    }

    @Test
    void verifiesCompactJoinNotificationEmbed() {
        MessageEmbed join = DiscordEmbedFactory.playerJoined(
                "Alex", "Alex joined the server.", false, "Survival SMP", "https://example.com/icon.png");

        assertNull(join.getTitle());
        assertNotNull(join.getAuthor());
        assertEquals("Alex joined the server", join.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Alex/64", join.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.JOIN_COLOR, join.getColor().getRGB() & 0xFFFFFF);
        assertNull(join.getTimestamp());
        assertNull(join.getDescription());
        assertTrue(join.getFields().isEmpty(), "Join embed must not have redundant fields");
        assertEquals("Survival SMP", join.getFooter().getText());
        assertEquals("https://example.com/icon.png", join.getFooter().getIconUrl());
    }

    @Test
    void verifiesCompactLeaveNotificationEmbed() {
        MessageEmbed leave = DiscordEmbedFactory.playerLeft(
                "Alex", "Alex left the server.", "Survival SMP", null);

        assertNull(leave.getTitle());
        assertNotNull(leave.getAuthor());
        assertEquals("Alex left the server", leave.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Alex/64", leave.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.LEAVE_COLOR, leave.getColor().getRGB() & 0xFFFFFF);
        assertNull(leave.getTimestamp());
        assertNull(leave.getDescription());
        assertTrue(leave.getFields().isEmpty(), "Leave embed must not have redundant fields");
        assertEquals("Survival SMP", leave.getFooter().getText());
    }

    @Test
    void verifiesFirstTimeJoinNotificationEmbed() {
        MessageEmbed firstJoin = DiscordEmbedFactory.playerJoined(
                "Steve", "Welcome Steve to the server!", true, "Survival SMP", null);

        assertNull(firstJoin.getTitle());
        assertNotNull(firstJoin.getAuthor());
        assertEquals("Steve joined for the first time!", firstJoin.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Steve/64", firstJoin.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.FIRST_JOIN_COLOR, firstJoin.getColor().getRGB() & 0xFFFFFF);
        assertEquals("Welcome Steve to the server!", firstJoin.getDescription());
        assertNull(firstJoin.getTimestamp());
        assertTrue(firstJoin.getFields().isEmpty(), "First join embed must not have redundant fields");
    }

    @Test
    void verifiesCompactDeathNotificationEmbedAndFields() {
        DeathContext pvp = new DeathContext("Alex", "Zombie", "attack", DeathCategory.MOB_KILL);
        MessageEmbed pvpDeath = DiscordEmbedFactory.death(
                "DEATH", "Alex was slain by Zombie", pvp, DiscordEmbedFactory.DEATH_COLOR, "Survival SMP");

        assertNull(pvpDeath.getTitle());
        assertEquals("Alex", pvpDeath.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Alex/64", pvpDeath.getAuthor().getIconUrl());
        assertEquals("Alex was slain by Zombie", pvpDeath.getDescription());
        assertEquals(DiscordEmbedFactory.DEATH_COLOR, pvpDeath.getColor().getRGB() & 0xFFFFFF);
        assertNull(pvpDeath.getTimestamp());
        assertTrue(pvpDeath.getFields().isEmpty(), "Compact death embed must omit redundant fields");

        // Environmental death without killer
        DeathContext env = new DeathContext("Alex", "", "fall damage", DeathCategory.FALL);
        MessageEmbed envDeath = DiscordEmbedFactory.death(
                "DEATH", "Alex fell from a high place", env, DiscordEmbedFactory.DEATH_COLOR, "Survival SMP");

        assertNull(envDeath.getTitle());
        assertEquals("Alex", envDeath.getAuthor().getName());
        assertEquals("Alex fell from a high place", envDeath.getDescription());
        assertTrue(envDeath.getFields().isEmpty(), "Compact environmental death embed must omit redundant fields");
    }

    @Test
    void verifiesUltraCompactAdvancementAndCategoryColors() {
        // Story
        assertEquals(0x3498DB, DiscordEmbedFactory.categoryColor(AdvancementCategory.STORY, false));
        // Nether
        assertEquals(0xD35400, DiscordEmbedFactory.categoryColor(AdvancementCategory.NETHER, false));
        // End
        assertEquals(0x9B59B6, DiscordEmbedFactory.categoryColor(AdvancementCategory.END, false));
        // Adventure
        assertEquals(0x1ABC9C, DiscordEmbedFactory.categoryColor(AdvancementCategory.ADVENTURE, false));
        // Husbandry
        assertEquals(0x2ECC71, DiscordEmbedFactory.categoryColor(AdvancementCategory.HUSBANDRY, false));
        // Challenge
        assertEquals(0xF1C40F, DiscordEmbedFactory.categoryColor(AdvancementCategory.NETHER, true));

        AdvancementContext context = new AdvancementContext(
                "Alex", "Suit Up", "Protect yourself with armor", AdvancementCategory.STORY, false);
        MessageEmbed advEmbed = DiscordEmbedFactory.advancement(
                context, "ADVANCEMENT", "Protect yourself with armor", 0, "Survival SMP");

        assertNull(advEmbed.getTitle());
        assertEquals("Alex has made the advancement [Suit Up]", advEmbed.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Alex/64", advEmbed.getAuthor().getIconUrl());
        assertEquals(0x3498DB, advEmbed.getColor().getRGB() & 0xFFFFFF);
        assertTrue(advEmbed.getFields().isEmpty(), "Advancement embed must omit redundant fields");
        assertNull(advEmbed.getTimestamp());
    }

    @Test
    void verifiesServerStartAndStopEmbeds() {
        MessageEmbed start = DiscordEmbedFactory.serverStarted("Survival SMP", "https://example.com/icon.png");
        assertNull(start.getTitle());
        assertEquals("Survival SMP", start.getAuthor().getName());
        assertEquals("Server is online", start.getDescription());
        assertEquals(DiscordEmbedFactory.SERVER_ONLINE_COLOR, start.getColor().getRGB() & 0xFFFFFF);
        assertNull(start.getTimestamp());
        assertNull(start.getFooter());
        assertTrue(start.getFields().isEmpty());

        MessageEmbed stop = DiscordEmbedFactory.serverStopped("Survival SMP", null);
        assertNull(stop.getTitle());
        assertEquals("Survival SMP", stop.getAuthor().getName());
        assertEquals("Server is offline", stop.getDescription());
        assertEquals(DiscordEmbedFactory.SERVER_OFFLINE_COLOR, stop.getColor().getRGB() & 0xFFFFFF);
        assertNull(stop.getTimestamp());
        assertNull(stop.getFooter());
        assertTrue(stop.getFields().isEmpty());
    }

    @Test
    void verifiesServerEventsIndependentlyConfigurable() {
        DiscordEventsSettings allDisabled = new DiscordEventsSettings(
                true, true, "footer",
                true, "", true, true, true,
                true, "", Map.of(), true,
                true, "", true, true, true,
                false, "", true, true);
        assertFalse(DiscordEventsPolicy.serverEventEnabled(allDisabled, true));
        assertFalse(DiscordEventsPolicy.serverEventEnabled(allDisabled, false));

        DiscordEventsSettings startOnly = new DiscordEventsSettings(
                true, true, "footer",
                true, "", true, true, true,
                true, "", Map.of(), true,
                true, "", true, true, true,
                true, "", true, false);
        assertTrue(DiscordEventsPolicy.serverEventEnabled(startOnly, true));
        assertFalse(DiscordEventsPolicy.serverEventEnabled(startOnly, false));

        DiscordEventsSettings stopOnly = new DiscordEventsSettings(
                true, true, "footer",
                true, "", true, true, true,
                true, "", Map.of(), true,
                true, "", true, true, true,
                true, "", false, true);
        assertFalse(DiscordEventsPolicy.serverEventEnabled(stopOnly, true));
        assertTrue(DiscordEventsPolicy.serverEventEnabled(stopOnly, false));
    }

    @Test
    void verifiesSafePlayerAvatarUrlFallback() {
        assertEquals("https://mc-heads.net/avatar/Alex/64", DiscordEmbedFactory.playerAvatarUrl("Alex"));
        assertEquals("https://mc-heads.net/avatar/Steve/64", DiscordEmbedFactory.playerAvatarUrl(null));
        assertEquals("https://mc-heads.net/avatar/Steve/64", DiscordEmbedFactory.playerAvatarUrl("   "));
    }

    @Test
    void verifiesOlderConfigBackwardCompatibilityWithoutServerEvents() {
        String yaml = """
                discord:
                  mode: "BOT"
                  bot-token: "synthetic-token"
                  channel-id: "123456789012345678"
                discord-events:
                  enabled: true
                  embeds:
                    enabled: true
                    footer: "Old Footer"
                  player-events:
                    enabled: true
                    join: true
                    leave: true
                    first-join: true
                """;

        YamlConfiguration config = new YamlConfiguration();
        try {
            try (var stream = getClass().getResourceAsStream("/config.yml")) {
                if (stream != null) {
                    YamlConfiguration defaults = new YamlConfiguration();
                    defaults.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                    config.setDefaults(defaults);
                }
            }
            config.load(new StringReader(yaml));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Candidate candidate = manager.candidateFrom(config, null);
        assertTrue(candidate.valid(), "Older configuration must remain completely valid: " + candidate.errors());
        DiscordEventsSettings settings = candidate.settings().discordEvents();
        assertFalse(settings.serverEventsEnabled(), "Server events should be disabled by default on older configs");
        assertTrue(settings.serverStartEnabled());
        assertTrue(settings.serverStopEnabled());
        assertEquals("Old Footer", settings.footer());
    }
}
