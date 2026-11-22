package com.minebridge.listener;

import com.minebridge.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict classifier for Minecraft chat events.
 * Ensures only genuine, public, global player chat is synchronized to Discord.
 * Excludes private messages, faction/team chat, party/guild chat, staff chat,
 * plugin-generated messages, server announcements, commands, and system messages.
 */
public final class ChatClassifier {

    private static final Pattern PRIVATE_COMMAND_PATTERN = Pattern.compile(
            "^/(?:msg|w|whisper|tell|r|reply|pm|dm|t|emsg|etell|ewhisper|m)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CHANNEL_PREFIX_COMMAND_PATTERN = Pattern.compile(
            "^/(?:tc|nc|lc|gc|fc|pc|sc|ac|staffchat|partychat|factionchat|teamchat|guildchat)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRIVATE_MESSAGE_TEXT_PATTERN = Pattern.compile(
            "^(?:\\[(?:msg|tell|whisper|pm|dm|private|to|from)[^\\]]*\\]|->|\\[me\\s*->[^\\]]*\\]|\\[[^\\]]*->\\s*me\\])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NON_GLOBAL_CHANNEL_TAG_PATTERN = Pattern.compile(
            "(?i)\\[(?:party|faction|team|guild|staff(?:chat)?|adminchat|modchat|officer|local|proximity|town|nation|clan|alliance|sc|ac|tc|nc|lc|pc|fc|gc)\\]");

    private static final Pattern CHANNEL_SHORTCUT_PATTERN = Pattern.compile(
            "^[@#!$%](?:party|faction|team|guild|staff|admin|p|f|t|g|s|a)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> NON_GLOBAL_METADATA_KEYS = Set.of(
            "channel",
            "active_channel",
            "chat_channel",
            "chat_mode",
            "towny_chat_channel",
            "current_channel"
    );

    private static final Set<String> NON_GLOBAL_CHANNEL_VALUES = Set.of(
            "party", "faction", "team", "staff", "admin", "mod", "guild",
            "local", "town", "nation", "clan", "alliance", "msg", "pm", "tell"
    );

    private ChatClassifier() {
    }

    /**
     * Evaluates an AsyncPlayerChatEvent to verify whether it represents genuine public global chat.
     * Fails closed on any ambiguity.
     */
    public static boolean isGlobalChat(AsyncPlayerChatEvent event) {
        return isGlobalChat(event, ConfigManager.defaultCrossWorldSettings());
    }

    public static boolean isGlobalChat(AsyncPlayerChatEvent event, ConfigManager.CrossWorldSettings crossWorld) {
        if (event == null || event.isCancelled()) {
            return false;
        }

        Player player = event.getPlayer();
        if (player == null || !isValidSender(player)) {
            return false;
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        Collection<? extends Player> onlinePlayers = null;
        if (player.getServer() != null) {
            onlinePlayers = player.getServer().getOnlinePlayers();
        } else if (Bukkit.getServer() != null) {
            onlinePlayers = Bukkit.getOnlinePlayers();
        }

        return isGlobalChat(
                player,
                message,
                event.getFormat(),
                event.isCancelled(),
                event.getRecipients(),
                onlinePlayers,
                crossWorld);
    }

    /**
     * Verifies that the sender and message content meet public chat criteria.
     * Excludes commands, private messaging formats, channel prefixes, channel tags,
     * and players with non-global channel metadata.
     */
    public static boolean isPublicChatMessage(Player sender, String message, String format, boolean cancelled) {
        if (cancelled) {
            return false;
        }

        if (sender == null || !isValidSender(sender)) {
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String trimmed = message.trim();

        // 1. Exclude command attempts and private message commands
        if (trimmed.startsWith("/")
                || PRIVATE_COMMAND_PATTERN.matcher(trimmed).find()
                || CHANNEL_PREFIX_COMMAND_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        // 2. Exclude direct/private message text formats
        if (PRIVATE_MESSAGE_TEXT_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        // 3. Exclude channel shortcut prefixes in message
        if (CHANNEL_SHORTCUT_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        // 4. Exclude private channel tags in event format (e.g., [Party] <Player> msg)
        if (format != null && NON_GLOBAL_CHANNEL_TAG_PATTERN.matcher(format).find()) {
            return false;
        }

        // 5. Exclude private channel tags prefixed in message body
        if (NON_GLOBAL_CHANNEL_TAG_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        // 6. Exclude if sender has active non-global channel metadata
        return !hasPrivateChannelMetadata(sender);
    }

    /**
     * Core classification logic operating on explicit chat event attributes.
     */
    public static boolean isGlobalChat(
            Player sender,
            String message,
            String format,
            boolean cancelled,
            Collection<? extends Player> recipients,
            Collection<? extends Player> onlinePlayers) {
        return isGlobalChat(
                sender,
                message,
                format,
                cancelled,
                recipients,
                onlinePlayers,
                ConfigManager.defaultCrossWorldSettings());
    }

    public static boolean isGlobalChat(
            Player sender,
            String message,
            String format,
            boolean cancelled,
            Collection<? extends Player> recipients,
            Collection<? extends Player> onlinePlayers,
            ConfigManager.CrossWorldSettings crossWorld) {

        if (!isPublicChatMessage(sender, message, format, cancelled)) {
            return false;
        }

        // Validate recipients collection basics
        if (recipients == null || recipients.isEmpty() || !recipients.contains(sender)) {
            return false;
        }

        ConfigManager.CrossWorldSettings settings = crossWorld != null
                ? crossWorld : ConfigManager.defaultCrossWorldSettings();

        // Filtered mode: world must be whitelisted
        if (settings.mode() == ConfigManager.CrossWorldMode.FILTERED) {
            if (sender.getWorld() == null || !settings.enabledWorlds().contains(sender.getWorld().getName())) {
                return false;
            }
        }

        // Audience verification
        if (settings.mode() == ConfigManager.CrossWorldMode.PER_WORLD) {
            Collection<? extends Player> worldPlayers = sender.getWorld() != null
                    ? sender.getWorld().getPlayers() : onlinePlayers;
            if (worldPlayers == null || worldPlayers.isEmpty()) {
                return false;
            }
            int worldCount = worldPlayers.size();
            if (worldCount == 1) {
                return recipients.contains(sender);
            }
            if (recipients.size() < worldCount) {
                return false;
            }
            return recipients.containsAll(worldPlayers);
        }

        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            // Cannot verify global audience -> fail closed
            return false;
        }

        int onlineCount = onlinePlayers.size();
        if (onlineCount == 1) {
            // Only the sender is online; recipient set must contain only the sender
            return recipients.size() == 1 && recipients.contains(sender);
        }

        // Multiple players online: genuine public global chat must be delivered to all online players
        if (recipients.size() < onlineCount) {
            // Non-global channels (party, faction, team, staff, local) exclude other online players
            return false;
        }

        return recipients.containsAll(onlinePlayers);
    }

    /**
     * Evaluates Paper AsyncChatEvent viewers against the online player audience.
     */
    public static boolean isPaperGlobalChat(
            Player sender,
            String message,
            boolean cancelled,
            Set<?> viewers,
            Collection<? extends Player> onlinePlayers) {
        return isPaperGlobalChat(sender, message, cancelled, viewers, onlinePlayers, ConfigManager.defaultCrossWorldSettings());
    }

    public static boolean isPaperGlobalChat(
            Player sender,
            String message,
            boolean cancelled,
            Set<?> viewers,
            Collection<? extends Player> onlinePlayers,
            ConfigManager.CrossWorldSettings crossWorld) {

        if (!isPublicChatMessage(sender, message, null, cancelled)) {
            return false;
        }

        if (viewers == null || viewers.isEmpty() || !viewers.contains(sender)) {
            return false;
        }

        ConfigManager.CrossWorldSettings settings = crossWorld != null
                ? crossWorld : ConfigManager.defaultCrossWorldSettings();

        if (settings.mode() == ConfigManager.CrossWorldMode.FILTERED) {
            if (sender.getWorld() == null || !settings.enabledWorlds().contains(sender.getWorld().getName())) {
                return false;
            }
        }

        if (settings.mode() == ConfigManager.CrossWorldMode.PER_WORLD) {
            Collection<? extends Player> worldPlayers = sender.getWorld() != null
                    ? sender.getWorld().getPlayers() : onlinePlayers;
            if (worldPlayers == null || worldPlayers.isEmpty()) {
                return false;
            }
            if (worldPlayers.size() == 1) {
                return viewers.contains(sender);
            }
            for (Player wp : worldPlayers) {
                if (!viewers.contains(wp)) {
                    return false;
                }
            }
            return true;
        }

        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return false;
        }

        int onlineCount = onlinePlayers.size();
        if (onlineCount == 1) {
            return viewers.contains(sender);
        }

        for (Player onlinePlayer : onlinePlayers) {
            if (!viewers.contains(onlinePlayer)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidSender(Player player) {
        if (player == null) {
            return false;
        }
        if (!player.isOnline()) {
            return false;
        }
        String name = player.getName();
        if (name == null || name.isBlank()) {
            return false;
        }
        if ("CONSOLE".equalsIgnoreCase(name) || "Server".equalsIgnoreCase(name)) {
            return false;
        }
        // Citizens or plugin NPC check
        if (player.hasMetadata("NPC")) {
            return false;
        }
        return true;
    }

    public static boolean hasPrivateChannelMetadata(Player player) {
        if (player == null) {
            return false;
        }
        for (String key : NON_GLOBAL_METADATA_KEYS) {
            if (player.hasMetadata(key)) {
                List<MetadataValue> values = player.getMetadata(key);
                if (values != null) {
                    for (MetadataValue val : values) {
                        if (val != null && val.asString() != null) {
                            String lower = val.asString().toLowerCase(Locale.ROOT).trim();
                            if (NON_GLOBAL_CHANNEL_VALUES.contains(lower)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
