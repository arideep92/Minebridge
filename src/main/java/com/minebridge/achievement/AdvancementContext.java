package com.minebridge.achievement;

import java.util.UUID;

public record AdvancementContext(
        String player,
        String title,
        String description,
        AdvancementCategory category,
        boolean challenge,
        UUID playerUuid) {

    public AdvancementContext(
            String player,
            String title,
            String description,
            AdvancementCategory category,
            boolean challenge) {
        this(player, title, description, category, challenge, null);
    }

    public AdvancementContext {
        player = player == null ? "" : player;
        title = title == null ? "Unknown advancement" : title;
        description = description == null ? "" : description;
    }

    public String typeLabel() {
        return challenge ? "Challenge" : "Advancement";
    }
}
