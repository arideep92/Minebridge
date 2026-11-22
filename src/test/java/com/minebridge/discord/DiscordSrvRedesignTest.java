package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.config.ConfigManager;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordSrvRedesignTest {

    @Test
    void verifiesBotModeMinecraftToDiscordChatUsesArrowSeparatorWithoutClutter() {
        String template = "{prefix}{player}{suffix} » {message}";

        // Unranked player (no prefix/suffix)
        String normal = RelayMessageFormatter.formatForDiscord(
                template, "Arixx", "Arixx", "Hello everyone!",
                Instant.now().toString(), "Survival SMP", "", "", "world", "world");
        assertEquals("Arixx » Hello everyone!", normal);

        // Ranked player with prefix
        String ranked = RelayMessageFormatter.formatForDiscord(
                template, "Arixx", "Arixx", "Hello everyone!",
                Instant.now().toString(), "Survival SMP", "[Admin]", "", "world", "world");
        assertEquals("\\[Admin\\] Arixx » Hello everyone!", ranked);

        // Player with nickname and prefix/suffix
        String custom = RelayMessageFormatter.formatForDiscord(
                template, "Steve", "Stevie", "Good morning!",
                Instant.now().toString(), "Survival SMP", "[VIP]", "[+]", "world", "world");
        assertEquals("\\[VIP\\] Steve \\[+\\] » Good morning!", custom);
    }

    @Test
    void verifiesDiscordToMinecraftChatUsesSubtlePrefix() {
        String template = "&8[&bDiscord&8] &7{player}&8: &f{message}";
        String formatted = RelayMessageFormatter.formatForMinecraft(
                template, "ModUser", "ModUser#1234", "mod_user", "Welcome new players!",
                Instant.now().toString(), "Survival SMP", "");

        assertEquals("&8[&bDiscord&8] &7ModUser&8: &fWelcome new players!", formatted);
    }

    @Test
    void verifiesDiscordSrvAuthorOnlyJoinEmbed() {
        MessageEmbed join = DiscordEmbedFactory.playerJoined(
                "Arixx", "has joined", false, null);

        assertNull(join.getTitle(), "DiscordSRV style must omit embed title");
        assertNotNull(join.getAuthor());
        assertEquals("Arixx joined the server", join.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Arixx/64", join.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.JOIN_COLOR, join.getColor().getRGB() & 0xFFFFFF);
        assertNull(join.getDescription(), "Regular join must not have redundant description");
        assertTrue(join.getFields().isEmpty(), "Join embed must not have fields");
        assertNull(join.getFooter(), "Default join embed must not have branding footer");
        assertNull(join.getTimestamp(), "Join embed must not have timestamp");
    }

    @Test
    void verifiesDiscordSrvAuthorOnlyFirstJoinEmbed() {
        MessageEmbed firstJoin = DiscordEmbedFactory.playerJoined(
                "NewPlayer", "Welcome NewPlayer to the server!", true, null);

        assertNull(firstJoin.getTitle(), "First join must omit embed title");
        assertNotNull(firstJoin.getAuthor());
        assertEquals("NewPlayer joined for the first time!", firstJoin.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/NewPlayer/64", firstJoin.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.FIRST_JOIN_COLOR, firstJoin.getColor().getRGB() & 0xFFFFFF);
        assertEquals("Welcome NewPlayer to the server!", firstJoin.getDescription());
        assertTrue(firstJoin.getFields().isEmpty());
        assertNull(firstJoin.getFooter());
        assertNull(firstJoin.getTimestamp());
    }

    @Test
    void verifiesDiscordSrvAuthorOnlyLeaveEmbed() {
        MessageEmbed leave = DiscordEmbedFactory.playerLeft("Arixx", "has left", null);

        assertNull(leave.getTitle(), "DiscordSRV style must omit leave title");
        assertNotNull(leave.getAuthor());
        assertEquals("Arixx left the server", leave.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Arixx/64", leave.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.LEAVE_COLOR, leave.getColor().getRGB() & 0xFFFFFF);
        assertNull(leave.getDescription());
        assertTrue(leave.getFields().isEmpty());
        assertNull(leave.getFooter());
        assertNull(leave.getTimestamp());
    }

    @Test
    void verifiesDiscordSrvCompactDeathEmbedWithoutRedundantFields() {
        DeathContext pvp = new DeathContext("Arixx", "Zombie", "entity attack", DeathCategory.MOB_KILL);
        MessageEmbed embed = DiscordEmbedFactory.death(
                "DEATH", "Arixx was slain by Zombie", pvp, DiscordEmbedFactory.DEATH_COLOR, null);

        assertNull(embed.getTitle(), "Default DEATH title must be omitted");
        assertNotNull(embed.getAuthor());
        assertEquals("Arixx", embed.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Arixx/64", embed.getAuthor().getIconUrl());
        assertEquals("Arixx was slain by Zombie", embed.getDescription());
        assertEquals(DiscordEmbedFactory.DEATH_COLOR, embed.getColor().getRGB() & 0xFFFFFF);
        assertTrue(embed.getFields().isEmpty(), "Death embed must omit redundant killer/cause fields");
        assertNull(embed.getFooter());
        assertNull(embed.getTimestamp());
    }

    @Test
    void verifiesDiscordSrvAdvancementAndChallengeEmbeds() {
        AdvancementContext adv = new AdvancementContext(
                "Arixx", "Stone Age", "Mine stone", AdvancementCategory.STORY, false);
        MessageEmbed advEmbed = DiscordEmbedFactory.advancement(
                adv, "ADVANCEMENT", "Stone Age", 0, null);

        assertNull(advEmbed.getTitle());
        assertNotNull(advEmbed.getAuthor());
        assertEquals("Arixx has made the advancement [Stone Age]", advEmbed.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Arixx/64", advEmbed.getAuthor().getIconUrl());
        assertEquals(0x3498DB, advEmbed.getColor().getRGB() & 0xFFFFFF);
        assertTrue(advEmbed.getFields().isEmpty());
        assertNull(advEmbed.getFooter());
        assertNull(advEmbed.getTimestamp());

        AdvancementContext challenge = new AdvancementContext(
                "Arixx", "How Did We Get Here?", "", AdvancementCategory.NETHER, true);
        MessageEmbed chalEmbed = DiscordEmbedFactory.advancement(
                challenge, "CHALLENGE", "How Did We Get Here?", 0, null);

        assertNull(chalEmbed.getTitle());
        assertNotNull(chalEmbed.getAuthor());
        assertEquals("Arixx has completed the challenge [How Did We Get Here?]", chalEmbed.getAuthor().getName());
        assertEquals(DiscordEmbedFactory.CHALLENGE_COLOR, chalEmbed.getColor().getRGB() & 0xFFFFFF);
        assertTrue(chalEmbed.getFields().isEmpty());
        assertNull(chalEmbed.getFooter());
        assertNull(chalEmbed.getTimestamp());
    }

    @Test
    void verifiesDiscordSrvServerStartAndStopEmbeds() {
        MessageEmbed start = DiscordEmbedFactory.serverStarted("Survival SMP", null);
        assertNull(start.getTitle());
        assertEquals("Survival SMP", start.getAuthor().getName());
        assertEquals("Server is online", start.getDescription());
        assertEquals(DiscordEmbedFactory.SERVER_ONLINE_COLOR, start.getColor().getRGB() & 0xFFFFFF);
        assertTrue(start.getFields().isEmpty());
        assertNull(start.getFooter());
        assertNull(start.getTimestamp());

        MessageEmbed stop = DiscordEmbedFactory.serverStopped("Survival SMP", null);
        assertNull(stop.getTitle());
        assertEquals("Survival SMP", stop.getAuthor().getName());
        assertEquals("Server is offline", stop.getDescription());
        assertEquals(DiscordEmbedFactory.SERVER_OFFLINE_COLOR, stop.getColor().getRGB() & 0xFFFFFF);
        assertTrue(stop.getFields().isEmpty());
        assertNull(stop.getFooter());
        assertNull(stop.getTimestamp());
    }

    @Test
    void verifiesWebhookUsernameAndAvatarDoNotLeakPlaceholders() {
        // When template contains {prefix}{player} and prefix is empty
        String username = RelayMessageFormatter.formatWebhookUsername(
                "{prefix}{player}", "Steve", "Steve", "SMP", "", "", "world", "world");
        assertEquals("Steve", username);
        assertFalse(username.contains("{prefix}"));
        assertFalse(username.contains("{player}"));

        // When template contains {prefix}{player}{suffix} with prefix
        String rankedUser = RelayMessageFormatter.formatWebhookUsername(
                "{prefix}{player}{suffix}", "Steve", "Steve", "SMP", "[Admin]", "", "world", "world");
        assertEquals("[Admin] Steve", rankedUser);

        // When buildPayload encounters unexpanded tokens in system messages
        String json = WebhookService.buildPayload(
                "System alert",
                "{prefix}{player}",
                "https://mc-heads.net/avatar/{uuid}/100",
                List.of(),
                "SMP",
                null);

        DataObject root = DataObject.fromJson(json);
        assertEquals("SMP", root.getString("username"));
        assertFalse(root.hasKey("avatar_url"), "Unresolved {uuid} must not be sent to Discord as avatar_url");
    }

    @Test
    void verifiesHybridModeEventDeliverySelectsSinglePath() {
        ConfigManager.Settings botDeliverySettings = new ConfigManager.Settings(
                IntegrationMode.HYBRID,
                "token", "123456789012345678", true, true,
                Map.of(), true, true,
                new ConfigManager.DeathSettings(false, false, false, false, false, "", "", "", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(false, false, false, false, "", "", "", "", "", "", "", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(true, true, "footer",
                        true, "", true, true, true, false, "", Map.of(), false, false, "", false, false, false),
                new ConfigManager.WebhookSettings("https://discord.com/api/webhooks/123/token", "{player}", ""),
                "SMP",
                new ConfigManager.EmbedFooterSettings("", ""),
                true,
                ConfigManager.defaultCrossWorldSettings(),
                new ConfigManager.HybridSettings(IntegrationMode.BOT),
                ConfigManager.defaultChatFormattingSettings());

        assertEquals(IntegrationMode.BOT, botDeliverySettings.hybrid().eventDelivery());

        ConfigManager.Settings webhookDeliverySettings = new ConfigManager.Settings(
                IntegrationMode.HYBRID,
                "token", "123456789012345678", true, true,
                Map.of(), true, true,
                new ConfigManager.DeathSettings(false, false, false, false, false, "", "", "", 0, Map.of(), Map.of()),
                new ConfigManager.AchievementSettings(false, false, false, false, "", "", "", "", "", "", "", 0, Map.of()),
                new ConfigManager.DiscordEventsSettings(true, true, "footer",
                        true, "", true, true, true, false, "", Map.of(), false, false, "", false, false, false),
                new ConfigManager.WebhookSettings("https://discord.com/api/webhooks/123/token", "{player}", ""),
                "SMP",
                new ConfigManager.EmbedFooterSettings("", ""),
                true,
                ConfigManager.defaultCrossWorldSettings(),
                new ConfigManager.HybridSettings(IntegrationMode.WEBHOOK),
                ConfigManager.defaultChatFormattingSettings());

        assertEquals(IntegrationMode.WEBHOOK, webhookDeliverySettings.hybrid().eventDelivery());
    }

    @Test
    void verifiesAllNotificationEmbedsHaveNoTimestampsAndNoDefaultFooters() {
        MessageEmbed join = DiscordEmbedFactory.playerJoined("Arixx", "joined", false, null);
        assertNull(join.getTimestamp(), "Join embed must not have timestamp");
        assertNull(join.getFooter(), "Join embed must not have footer");

        MessageEmbed firstJoin = DiscordEmbedFactory.playerJoined("Arixx", "first", true, null);
        assertNull(firstJoin.getTimestamp(), "First join embed must not have timestamp");
        assertNull(firstJoin.getFooter(), "First join embed must not have footer");

        MessageEmbed leave = DiscordEmbedFactory.playerLeft("Arixx", "left", null);
        assertNull(leave.getTimestamp(), "Leave embed must not have timestamp");
        assertNull(leave.getFooter(), "Leave embed must not have footer");

        DeathContext deathCtx = new DeathContext("Arixx", "Skeleton", "shot", DeathCategory.MOB_KILL);
        MessageEmbed death = DiscordEmbedFactory.death("DEATH", "Arixx was shot by Skeleton", deathCtx, DiscordEmbedFactory.DEATH_COLOR, null);
        assertNull(death.getTimestamp(), "Death embed must not have timestamp");
        assertNull(death.getFooter(), "Death embed must not have footer");

        AdvancementContext advCtx = new AdvancementContext("Arixx", "Stone Age", "Mine stone", AdvancementCategory.STORY, false);
        MessageEmbed adv = DiscordEmbedFactory.advancement(advCtx, "ADVANCEMENT", "Stone Age", 0, null);
        assertNull(adv.getTimestamp(), "Advancement embed must not have timestamp");
        assertNull(adv.getFooter(), "Advancement embed must not have footer");

        AdvancementContext chalCtx = new AdvancementContext("Arixx", "Arbalistic", "", AdvancementCategory.ADVENTURE, true);
        MessageEmbed chal = DiscordEmbedFactory.advancement(chalCtx, "CHALLENGE", "Arbalistic", 0, null);
        assertNull(chal.getTimestamp(), "Challenge embed must not have timestamp");
        assertNull(chal.getFooter(), "Challenge embed must not have footer");

        MessageEmbed start = DiscordEmbedFactory.serverStarted("Survival SMP", null);
        assertNull(start.getTimestamp(), "Server start embed must not have timestamp");
        assertNull(start.getFooter(), "Server start embed must not have footer");

        MessageEmbed stop = DiscordEmbedFactory.serverStopped("Survival SMP", null);
        assertNull(stop.getTimestamp(), "Server stop embed must not have timestamp");
        assertNull(stop.getFooter(), "Server stop embed must not have footer");
    }
}
