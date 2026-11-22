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

    public static final String DEFAULT_FOOTER = "MineBridge | Minecraft Events";
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
        String title = firstJoin ? "FIRST JOIN" : "JOIN";
        int color = firstJoin ? FIRST_JOIN_COLOR : JOIN_COLOR;

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(title)
                .setAuthor(safePlayer, null, avatarUrl)
                .setColor(new Color(color & 0xFFFFFF))
                .setTimestamp(Instant.now());

        if (firstJoin) {
            String welcome = (description != null && !description.isBlank())
                    ? limit(safe(description), 2048)
                    : safePlayer + " joined the server for the first time!";
            builder.setDescription(welcome);
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
                .setTitle("LEAVE")
                .setAuthor(safePlayer, null, avatarUrl)
                .setColor(new Color(LEAVE_COLOR & 0xFFFFFF))
                .setTimestamp(Instant.now());

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
        String embedTitle = challenge ? "CHALLENGE" : "ADVANCEMENT";
        if (title != null && !title.isBlank()
                && !title.equalsIgnoreCase("ADVANCEMENT")
                && !title.equalsIgnoreCase("ADVANCEMENT UNLOCKED")
                && !title.equalsIgnoreCase("CHALLENGE")
                && !title.equalsIgnoreCase("CHALLENGE COMPLETED")) {
            embedTitle = title;
        }

        int embedColor = color != 0 ? color : (challenge ? CHALLENGE_COLOR : ADVANCEMENT_COLOR);

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(limit(safe(embedTitle), 256))
                .setAuthor(safePlayer, null, avatarUrl)
                .setDescription(limit(discordText(advancement != null ? advancement : ""), 2048))
                .setColor(new Color(embedColor & 0xFFFFFF))
                .setTimestamp(timestamp != null ? timestamp : Instant.now());

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
        String embedTitle = (title != null && !title.isBlank()
                && !title.equalsIgnoreCase("PLAYER DEATH")
                && !title.equalsIgnoreCase("☠ Player death"))
                ? title : "DEATH";
        int embedColor = color != 0 ? color : DEATH_COLOR;

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(limit(safe(embedTitle), 256))
                .setAuthor(safeVictim, null, avatarUrl)
                .setColor(new Color(embedColor & 0xFFFFFF))
                .setTimestamp(Instant.now());

        if (description != null && !description.isBlank()) {
            builder.setDescription(limit(safe(description), 2048));
        }

        if (context != null) {
            if (context.killer() != null && !context.killer().isBlank()) {
                builder.addField("Killer", limit(discordText(context.killer()), 512), true);
            } else if (context.cause() != null && !context.cause().isBlank()
                    && !context.cause().equalsIgnoreCase("unknown")
                    && !context.cause().equalsIgnoreCase("other")) {
                builder.addField("Cause", limit(discordText(context.cause()), 512), true);
            }
        }

        applyFooter(builder, footer, footerIconUrl);
        return builder.build();
    }

    public static MessageEmbed serverStarted(String footer, String footerIconUrl) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("SERVER ONLINE")
                .setColor(new Color(SERVER_ONLINE_COLOR & 0xFFFFFF))
                .setTimestamp(Instant.now());
        applyFooter(builder, footer, footerIconUrl);
        return builder.build();
    }

    public static MessageEmbed serverStopped(String footer, String footerIconUrl) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("SERVER OFFLINE")
                .setColor(new Color(SERVER_OFFLINE_COLOR & 0xFFFFFF))
                .setTimestamp(Instant.now());
        applyFooter(builder, footer, footerIconUrl);
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
                .setColor(new Color(color & 0xFFFFFF))
                .setTimestamp(timestamp == null ? Instant.now() : timestamp);
        applyFooter(builder, footer, footerIconUrl);
        return builder;
    }

    public static String discordText(String value) {
        return RelayMessageFormatter.escapeDiscordUserText(value);
    }

    private static void applyFooter(EmbedBuilder builder, String footer, String footerIconUrl) {
        String safeFooter = footer == null || footer.isBlank()
                ? DEFAULT_FOOTER : limit(safe(footer), 512);
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
