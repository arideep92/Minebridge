package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public final class DiscordEmbedFactory {

    public static final String DEFAULT_FOOTER = "";
    public static final String DEFAULT_AVATAR_URL = "https://mc-heads.net/avatar/{player}/100";

    // Minimalistic, subtle notification colors
    public static final int JOIN_COLOR = 0x43B581;           // Subtle Emerald Green
    public static final int LEAVE_COLOR = 0xEF5350;          // Subtle Soft Red
    public static final int FIRST_JOIN_COLOR = 0xFAA61A;     // Subtle Gold
    public static final int ADVANCEMENT_COLOR = 0x3498DB;    // Subtle Sky Blue
    public static final int CHALLENGE_COLOR = 0xF1C40F;      // Subtle Challenge Gold
    public static final int DEATH_COLOR = 0xE74C3C;          // Subtle Crimson Red
    public static final int SERVER_ONLINE_COLOR = 0x43B581;  // Subtle Green
    public static final int SERVER_OFFLINE_COLOR = 0xEF5350; // Subtle Red

    private DiscordEmbedFactory() {
    }

    public static String playerAvatarUrl(String player) {
        String sanitized = (player != null) ? player.replaceAll("[^a-zA-Z0-9_]", "") : "";
        String safeName = !sanitized.isBlank() ? sanitized : "Steve";
        return "https://mc-heads.net/avatar/" + safeName + "/64";
    }

    public static int categoryColor(AdvancementCategory category, boolean challenge) {
        if (challenge) {
            return CHALLENGE_COLOR;
        }
        if (category == null) {
            return ADVANCEMENT_COLOR;
        }
        return switch (category) {
            case STORY -> 0x3498DB;     // Subtle Sky Blue
            case NETHER -> 0xD35400;    // Subtle Nether Rust/Crimson
            case END -> 0x9B59B6;       // Subtle End Purple
            case ADVENTURE -> 0x1ABC9C; // Subtle Adventure Turquoise
            case HUSBANDRY -> 0x2ECC71; // Subtle Nature Green
            case OTHER -> 0x3498DB;     // Subtle Sky Blue
        };
    }

    public static MessageEmbed playerJoined(
            String player, String description, boolean firstJoin, String footer) {
        return playerJoined(player, description, firstJoin, footer, null);
    }

    public static MessageEmbed playerJoined(
            String player, String description, boolean firstJoin, String footer, String footerIconUrl) {
        String safePlayer = discordText(player != null && !player.isBlank() ? player : "Player");
        String avatarUrl = playerAvatarUrl(player);
        String authorName = firstJoin ? safePlayer + " joined for the first time!" : safePlayer + " joined the server";
        int color = firstJoin ? FIRST_JOIN_COLOR : JOIN_COLOR;

        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(authorName, null, avatarUrl)
                .setColor(new Color(color & 0xFFFFFF));

        if (firstJoin && description != null && !description.isBlank()
                && !description.equals(safePlayer + " joined the server.")
                && !description.equals(safePlayer + " joined for the first time!")
                && !description.equals(safePlayer + " joined the Minecraft server.")) {
            builder.setDescription(limit(safe(description), 2048));
        }

        applyFooter(builder, footer, footerIconUrl);
        return builder.build();
    }

    public static MessageEmbed playerLeft(String player, String description, String footer) {
        return playerLeft(player, description, footer, null);
    }

    public static MessageEmbed playerLeft(
            String player, String description, String footer, String footerIconUrl) {
        String safePlayer = discordText(player != null && !player.isBlank() ? player : "Player");
        String avatarUrl = playerAvatarUrl(player);

        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(safePlayer + " left the server", null, avatarUrl)
                .setColor(new Color(LEAVE_COLOR & 0xFFFFFF));

        applyFooter(builder, footer, footerIconUrl);
        return builder.build();
    }

    public static MessageEmbed advancement(
            String title,
            String description,
            String player,
            String advancement,
            String category,
            boolean challenge,
            int color,
            String footer,
            Instant timestamp) {
        return advancement(
                title,
                description,
                player,
                advancement,
                category,
                challenge,
                color,
                footer,
                null,
                timestamp);
    }

    public static MessageEmbed advancement(
            String title,
            String description,
            String player,
            String advancement,
            String category,
            boolean challenge,
            int color,
            String footer,
            String footerIconUrl,
            Instant timestamp) {
        String safePlayer = discordText(player != null && !player.isBlank() ? player : "Player");
        String avatarUrl = playerAvatarUrl(player);
        String safeAdv = discordText(advancement != null ? advancement : "");
        String authorName = challenge
                ? safePlayer + " has completed the challenge [" + safeAdv + "]"
                : safePlayer + " has made the advancement [" + safeAdv + "]";

        int embedColor = color != 0 ? color : (challenge ? CHALLENGE_COLOR : ADVANCEMENT_COLOR);

        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(authorName, null, avatarUrl)
                .setColor(new Color(embedColor & 0xFFFFFF));

        if (title != null && !title.isBlank()
                && !title.equalsIgnoreCase("ADVANCEMENT")
                && !title.equalsIgnoreCase("ADVANCEMENT UNLOCKED")
                && !title.equalsIgnoreCase("CHALLENGE")
                && !title.equalsIgnoreCase("CHALLENGE COMPLETED")) {
            builder.setTitle(limit(safe(title), 256));
        }

        if (description != null && !description.isBlank()
                && !description.equals(advancement)
                && !description.equals("\u200b")
                && !description.contains("has unlocked")
                && !description.contains("has completed")
                && !description.contains("has made the advancement")) {
            builder.setDescription(limit(discordText(description), 2048));
        }

        applyFooter(builder, footer, footerIconUrl);
        return builder.build();
    }

    public static MessageEmbed advancement(
            AdvancementContext context,
            String title,
            String description,
            int color,
            String footer) {
        return advancement(context, title, description, color, footer, null);
    }

    public static MessageEmbed advancement(
            AdvancementContext context,
            String title,
            String description,
            int color,
            String footer,
            String footerIconUrl) {
        int effectiveColor = color != 0
                ? color
                : categoryColor(context.category(), context.challenge());
        return advancement(
                title,
                description,
                context.player(),
                context.title(),
                context.category().displayName(),
                context.challenge(),
                effectiveColor,
                footer,
                footerIconUrl,
                Instant.now());
    }

    public static MessageEmbed death(
            String title,
            String description,
            DeathContext context,
            int color,
            String footer) {
        return death(title, description, context, color, footer, null);
    }

    public static MessageEmbed death(
            String title,
            String description,
            DeathContext context,
            int color,
            String footer,
            String footerIconUrl) {
        String victim = context != null && context.victim() != null ? context.victim() : "Player";
        String safeVictim = discordText(victim);
        String avatarUrl = playerAvatarUrl(victim);
        int embedColor = color != 0 ? color : DEATH_COLOR;

        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(safeVictim, null, avatarUrl)
                .setColor(new Color(embedColor & 0xFFFFFF));

        if (title != null && !title.isBlank()
                && !title.equalsIgnoreCase("DEATH")
                && !title.equalsIgnoreCase("PLAYER DEATH")
                && !title.equalsIgnoreCase("☠ Player death")) {
            builder.setTitle(limit(safe(title), 256));
        }

        if (description != null && !description.isBlank()) {
            builder.setDescription(limit(safe(description), 2048));
        }

        applyFooter(builder, footer, footerIconUrl);
        return builder.build();
    }

    public static MessageEmbed serverStarted(String footer, String footerIconUrl) {
        String serverName = (footer != null && !footer.isBlank() && !DEFAULT_FOOTER.equals(footer) && !"MineBridge | Minecraft Events".equals(footer))
                ? footer : "Minecraft Server";
        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(serverName, null, (footerIconUrl != null && !footerIconUrl.isBlank()) ? footerIconUrl.trim() : null)
                .setDescription("Server is online")
                .setColor(new Color(SERVER_ONLINE_COLOR & 0xFFFFFF));
        return builder.build();
    }

    public static MessageEmbed serverStopped(String footer, String footerIconUrl) {
        String serverName = (footer != null && !footer.isBlank() && !DEFAULT_FOOTER.equals(footer) && !"MineBridge | Minecraft Events".equals(footer))
                ? footer : "Minecraft Server";
        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(serverName, null, (footerIconUrl != null && !footerIconUrl.isBlank()) ? footerIconUrl.trim() : null)
                .setDescription("Server is offline")
                .setColor(new Color(SERVER_OFFLINE_COLOR & 0xFFFFFF));
        return builder.build();
    }

    public static MessageCreateData createMessageData(MessageEmbed embed) {
        return MessageCreateBuilder.from(MessageCreateData.fromEmbeds(embed))
                .setAllowedMentions(List.of())
                .build();
    }

    public static EmbedBuilder base(
            String title,
            String description,
            int color,
            String footer,
            Instant timestamp) {
        return base(title, description, color, footer, null, timestamp);
    }

    public static EmbedBuilder base(
            String title,
            String description,
            int color,
            String footer,
            String footerIconUrl,
            Instant timestamp) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(limit(safe(title), 256))
                .setDescription(limit(safe(description), 2048))
                .setColor(new Color(color & 0xFFFFFF));
        applyFooter(builder, footer, footerIconUrl);
        return builder;
    }

    public static String discordText(String value) {
        return RelayMessageFormatter.escapeDiscordUserText(value);
    }

    private static void applyFooter(EmbedBuilder builder, String footer, String footerIconUrl) {
        if (footer == null || footer.isBlank() || DEFAULT_FOOTER.equals(footer) || "MineBridge | Minecraft Events".equals(footer)) {
            return;
        }
        String safeFooter = limit(safe(footer), 512);
        String safeIcon = footerIconUrl != null ? footerIconUrl.trim() : "";
        if (!safeIcon.isBlank()) {
            builder.setFooter(safeFooter, safeIcon);
        } else {
            builder.setFooter(safeFooter);
        }
    }

    private static String safe(String value) {
        return RelayMessageFormatter.sanitizeText(value);
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
