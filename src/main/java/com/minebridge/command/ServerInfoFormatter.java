package com.minebridge.command;

import com.minebridge.discord.RelayMessageFormatter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ServerInfoFormatter {

    private ServerInfoFormatter() {
    }

    public static String formatServerStatus(String template, int online, int max) {
        return RelayMessageFormatter.replacePlaceholders(template, Map.of(
                "online", Integer.toString(online),
                "max", Integer.toString(max)));
    }

    public static String formatPlayerList(
            String template, int online, int max, List<String> playerNames) {
        String players = playerNames.isEmpty()
                ? "None"
                : playerNames.stream()
                        .map(RelayMessageFormatter::sanitizeText)
                        .collect(Collectors.joining(", "));
        return RelayMessageFormatter.replacePlaceholders(template, Map.of(
                "online", Integer.toString(online),
                "max", Integer.toString(max),
                "players", players));
    }
}
