package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEmbedFactoryTest {

    @Test
    void createsJoinLeaveAndFirstJoinEmbeds() {
        MessageEmbed join = DiscordEmbedFactory.playerJoined(
                "Alex", "Alex joined the Minecraft server.", false,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("JOIN", join.getTitle());
        assertEquals("Alex", join.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Alex/64", join.getAuthor().getIconUrl());
        assertNull(join.getDescription());
        assertEquals(DiscordEmbedFactory.JOIN_COLOR, join.getColor().getRGB() & 0xFFFFFF);
        assertTrue(join.getFields().isEmpty());

        MessageEmbed leave = DiscordEmbedFactory.playerLeft(
                "Alex", "Alex left the Minecraft server.", DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("LEAVE", leave.getTitle());
        assertEquals("Alex", leave.getAuthor().getName());
        assertEquals("https://mc-heads.net/avatar/Alex/64", leave.getAuthor().getIconUrl());
        assertEquals(DiscordEmbedFactory.LEAVE_COLOR, leave.getColor().getRGB() & 0xFFFFFF);
        assertTrue(leave.getFields().isEmpty());

        MessageEmbed firstJoin = DiscordEmbedFactory.playerJoined(
                "Alex", "Alex joined for the first time!", true,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("FIRST JOIN", firstJoin.getTitle());
        assertEquals("Alex", firstJoin.getAuthor().getName());
        assertEquals("Alex joined for the first time!", firstJoin.getDescription());
        assertEquals(
                DiscordEmbedFactory.FIRST_JOIN_COLOR,
                firstJoin.getColor().getRGB() & 0xFFFFFF);
        assertTrue(firstJoin.getFields().isEmpty());
    }

    @Test
    void createsAdvancementAndChallengeEmbedsWithTheExpectedDetails() {
        AdvancementContext regular = new AdvancementContext(
                "Aridro", "Stone Age", "Mine stone", AdvancementCategory.STORY, false);
        MessageEmbed regularEmbed = DiscordEmbedFactory.advancement(
                regular, "ADVANCEMENT",
                "Aridro has unlocked Stone Age.", 0,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("ADVANCEMENT", regularEmbed.getTitle());
        assertEquals("Aridro", regularEmbed.getAuthor().getName());
        assertEquals("Stone Age", regularEmbed.getDescription());
        assertTrue(regularEmbed.getFields().isEmpty());
        assertEquals(DiscordEmbedFactory.ADVANCEMENT_COLOR,
                regularEmbed.getColor().getRGB() & 0xFFFFFF);

        AdvancementContext challenge = new AdvancementContext(
                "Aridro", "Cover Me in Debris", "", AdvancementCategory.NETHER, true);
        MessageEmbed challengeEmbed = DiscordEmbedFactory.advancement(
                "CHALLENGE",
                "Aridro has completed Cover Me in Debris!",
                challenge.player(),
                challenge.title(),
                challenge.category().displayName(),
                challenge.challenge(),
                DiscordEmbedFactory.CHALLENGE_COLOR,
                DiscordEmbedFactory.DEFAULT_FOOTER,
                Instant.now());
        assertEquals("CHALLENGE", challengeEmbed.getTitle());
        assertEquals("Aridro", challengeEmbed.getAuthor().getName());
        assertEquals("Cover Me in Debris", challengeEmbed.getDescription());
        assertTrue(challengeEmbed.getFields().isEmpty());
        assertEquals(DiscordEmbedFactory.CHALLENGE_COLOR,
                challengeEmbed.getColor().getRGB() & 0xFFFFFF);
    }

    @Test
    void createsDeathEmbedsAndOmitsMissingKiller() {
        DeathContext pvp = new DeathContext(
                "Alex", "Zombie", "entity attack", DeathCategory.MOB_KILL);
        MessageEmbed pvpEmbed = DiscordEmbedFactory.death(
                "DEATH",
                "Alex was slain by Zombie.",
                pvp,
                DiscordEmbedFactory.DEATH_COLOR,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("DEATH", pvpEmbed.getTitle());
        assertEquals("Alex", pvpEmbed.getAuthor().getName());
        assertEquals("Zombie", pvpEmbed.getFields().get(0).getValue());
        assertEquals(1, pvpEmbed.getFields().size());
        assertEquals(DiscordEmbedFactory.DEATH_COLOR,
                pvpEmbed.getColor().getRGB() & 0xFFFFFF);

        DeathContext environmental = new DeathContext(
                "Alex", "", "fall damage", DeathCategory.FALL);
        MessageEmbed environmentEmbed = DiscordEmbedFactory.death(
                "DEATH", "Alex fell to their death.", environmental,
                DiscordEmbedFactory.DEATH_COLOR, DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals(1, environmentEmbed.getFields().size());
        assertEquals("Cause", environmentEmbed.getFields().get(0).getName());
        assertEquals("fall damage", environmentEmbed.getFields().get(0).getValue());
        assertTrue(environmentEmbed.getFields().stream()
                .noneMatch(field -> field.getName().equals("Killer")));
        assertTrue(environmentEmbed.getFields().stream()
                .noneMatch(field -> field.getName().equals("Victim")));
        assertNotNull(environmentEmbed.getTimestamp());
        assertEquals(DiscordEmbedFactory.DEFAULT_FOOTER,
                environmentEmbed.getFooter().getText());
    }

    @Test
    void createsServerOnlineAndOfflineEmbeds() {
        MessageEmbed start = DiscordEmbedFactory.serverStarted("MineBridge", "https://icon.invalid");
        assertEquals("SERVER ONLINE", start.getTitle());
        assertEquals(DiscordEmbedFactory.SERVER_ONLINE_COLOR, start.getColor().getRGB() & 0xFFFFFF);
        assertNotNull(start.getTimestamp());
        assertEquals("MineBridge", start.getFooter().getText());
        assertEquals("https://icon.invalid", start.getFooter().getIconUrl());
        assertTrue(start.getFields().isEmpty());

        MessageEmbed stop = DiscordEmbedFactory.serverStopped("MineBridge", null);
        assertEquals("SERVER OFFLINE", stop.getTitle());
        assertEquals(DiscordEmbedFactory.SERVER_OFFLINE_COLOR, stop.getColor().getRGB() & 0xFFFFFF);
        assertNotNull(stop.getTimestamp());
        assertEquals("MineBridge", stop.getFooter().getText());
        assertTrue(stop.getFields().isEmpty());
    }

    @Test
    void escapesDynamicMarkdownAndBuildsEmbedOnlyMessageWithNoAllowedMentions() {
        MessageEmbed embed = DiscordEmbedFactory.playerJoined(
                "@everyone *Alex*",
                DiscordEmbedFactory.discordText("@everyone *Alex* joined."),
                false,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertTrue(embed.getAuthor().getName().contains("@\u200Beveryone"));
        assertTrue(embed.getAuthor().getName().contains("\\*Alex\\*"));

        var data = DiscordEmbedFactory.createMessageData(embed);
        assertEquals("", data.getContent());
        assertEquals(1, data.getEmbeds().size());
        assertTrue(data.getAllowedMentions().isEmpty());
    }

    @Test
    void supportsCustomFooterIconUrlAndGracefullyOmitsWhenBlank() {
        String iconUrl = "https://example.com/server-icon.png";
        MessageEmbed joinWithIcon = DiscordEmbedFactory.playerJoined(
                "Alex", "Alex joined the server.", false,
                "MineBridge", iconUrl);
        assertNotNull(joinWithIcon.getFooter());
        assertEquals("MineBridge", joinWithIcon.getFooter().getText());
        assertEquals(iconUrl, joinWithIcon.getFooter().getIconUrl());

        MessageEmbed leaveWithIcon = DiscordEmbedFactory.playerLeft(
                "Alex", "Alex left the server.", "MineBridge", iconUrl);
        assertNotNull(leaveWithIcon.getFooter());
        assertEquals(iconUrl, leaveWithIcon.getFooter().getIconUrl());

        AdvancementContext adv = new AdvancementContext(
                "Alex", "Getting Wood", "", AdvancementCategory.STORY, false);
        MessageEmbed advWithIcon = DiscordEmbedFactory.advancement(
                adv, "ADVANCEMENT", "desc", 0x123456, "MineBridge", iconUrl);
        assertNotNull(advWithIcon.getFooter());
        assertEquals(iconUrl, advWithIcon.getFooter().getIconUrl());

        DeathContext death = new DeathContext("Alex", "Zombie", "attack", DeathCategory.MOB_KILL);
        MessageEmbed deathWithIcon = DiscordEmbedFactory.death(
                "DEATH", "desc", death, 0x123456, "MineBridge", iconUrl);
        assertNotNull(deathWithIcon.getFooter());
        assertEquals(iconUrl, deathWithIcon.getFooter().getIconUrl());

        // Blank or null icon gracefully omitted
        MessageEmbed joinWithoutIcon = DiscordEmbedFactory.playerJoined(
                "Alex", "desc", false, "MineBridge", "   ");
        assertNotNull(joinWithoutIcon.getFooter());
        assertEquals("MineBridge", joinWithoutIcon.getFooter().getText());
        assertTrue(joinWithoutIcon.getFooter().getIconUrl() == null || joinWithoutIcon.getFooter().getIconUrl().isBlank());
    }
}
