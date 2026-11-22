package com.minebridge.branding;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class StartupBanner {

    public static final String[] ASCII_ART = {
        "███╗   ███╗██╗███╗   ██╗███████╗██████╗ ██████╗ ██╗██████╗  ██████╗ ███████╗",
        "████╗ ████║██║████╗  ██║██╔════╝██╔══██╗██╔══██╗██║██╔══██╗██╔════╝ ██╔════╝",
        "██╔████╔██║██║██╔██╗ ██║█████╗  ██████╔╝██████╔╝██║██║  ██║██║  ███╗█████╗  ",
        "██║╚██╔╝██║██║██║╚██╗██║██╔══╝  ██╔══██╗██╔══██╗██║██║  ██║██║   ██║██╔══╝  ",
        "██║ ╚═╝ ██║██║██║ ╚████║███████╗██████╔╝██║  ██║██║██████╔╝╚██████╔╝███████╗",
        "╚═╝     ╚═╝╚═╝╚═╝  ╚═══╝╚══════╝╚═════╝ ╚═╝  ╚═╝╚═╝╚═════╝  ╚═════╝ ╚══════╝"
    };

    public static final String SUBTITLE = "                         by Arixx";

    private static final AtomicBoolean DISPLAYED = new AtomicBoolean(false);

    private StartupBanner() {
    }

    public static List<String> getBannerLines(String version, String mode, boolean enabled, boolean colored) {
        String cleanVersion = version != null && !version.isBlank() ? version : "1.4.1";
        if (cleanVersion.startsWith("v") || cleanVersion.startsWith("V")) {
            cleanVersion = cleanVersion.substring(1);
        }
        String cleanMode = mode != null && !mode.isBlank() ? mode.toUpperCase() : "BOT";
        String cleanStatus = enabled ? "Enabled" : "Disabled";

        List<String> lines = new ArrayList<>(12);
        for (String artLine : ASCII_ART) {
            lines.add(colored ? ChatColor.AQUA + artLine : artLine);
        }
        lines.add(colored ? ChatColor.DARK_GRAY + SUBTITLE : SUBTITLE);
        lines.add("");
        lines.add(colored
                ? ChatColor.WHITE + "" + ChatColor.BOLD + "MineBridge v" + cleanVersion
                : "MineBridge v" + cleanVersion);
        lines.add(colored
                ? ChatColor.GRAY + "Mode: " + ChatColor.AQUA + cleanMode
                : "Mode: " + cleanMode);
        lines.add(colored
                ? ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN : ChatColor.RED) + cleanStatus
                : "Status: " + cleanStatus);
        return Collections.unmodifiableList(lines);
    }

    public static synchronized boolean display(
            Server server,
            Consumer<String> logger,
            String version,
            String mode,
            boolean enabled) {
        if (DISPLAYED.get()) {
            return false;
        }

        List<String> lines = getBannerLines(version, mode, enabled, true);
        boolean success = false;

        // 1. Prefer plugin logger for predictable output to console and logs/latest.log
        if (logger != null) {
            try {
                for (String line : lines) {
                    logger.accept(line);
                }
                success = true;
            } catch (Throwable ignored) {
                // If logger fails unexpectedly, fall through to server console sender
            }
        }

        // 2. Fallback to server console sender if logger was unavailable or failed
        if (!success && server != null) {
            try {
                ConsoleCommandSender console = server.getConsoleSender();
                if (console != null) {
                    for (String line : lines) {
                        console.sendMessage(line);
                    }
                    success = true;
                }
            } catch (Throwable ignored) {
            }
        }

        if (success) {
            DISPLAYED.set(true);
            return true;
        }
        return false;
    }

    public static boolean display(
            Consumer<String> logger,
            String version,
            String mode,
            boolean enabled) {
        return display(null, logger, version, mode, enabled);
    }

    public static boolean hasBeenDisplayed() {
        return DISPLAYED.get();
    }

    public static void reset() {
        DISPLAYED.set(false);
    }
}
