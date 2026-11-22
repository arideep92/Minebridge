package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.Instant;

public final class DiscordMessageListener extends ListenerAdapter {

    private final DiscordService discord;

    public DiscordMessageListener(DiscordService discord) {
        this.discord = discord;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        ConfigManager.Settings settings =
                discord.captureIncomingSettings(event.getJDA());
        if (settings == null
                || !DiscordMessageFilter.shouldRelay(
                settings.chatEnabled(),
                event.getAuthor().isBot(),
                event.isWebhookMessage(),
                event.isFromGuild(),
                event.getChannel().getId(),
                settings.channelId())) {
            return;
        }

        String content = DiscordMessageFilter.sanitizeIncoming(
                event.getMessage().getContentDisplay());

        ConfigManager.DiscordChatFormattingSettings formattingSettings =
                settings.chatFormatting() != null
                        ? settings.chatFormatting().discordToMinecraft()
                        : ConfigManager.defaultDiscordChatFormattingSettings();

        java.util.List<net.dv8tion.jda.api.entities.Message.Attachment> attachments =
                event.getMessage().getAttachments();
        if (formattingSettings.showAttachments() && attachments != null && !attachments.isEmpty()) {
            StringBuilder attSb = new StringBuilder();
            for (net.dv8tion.jda.api.entities.Message.Attachment att : attachments) {
                if (attSb.length() > 0) {
                    attSb.append(" ");
                }
                attSb.append("[").append(att.getFileName()).append("]");
            }
            if (!content.isEmpty()) {
                content = content + " " + attSb;
            } else {
                content = attSb.toString();
            }
        }

        if (content.isEmpty()) {
            return;
        }

        String player = DiscordMessageFilter.sanitizeIncoming(
                event.getAuthor().getEffectiveName());
        if (player.isEmpty()) {
            return;
        }
        String displayName = DiscordMessageFilter.sanitizeIncoming(
                event.getMember() == null
                        ? event.getAuthor().getEffectiveName()
                        : event.getMember().getEffectiveName());
        String username = DiscordMessageFilter.sanitizeIncoming(event.getAuthor().getName());
        String nickname = event.getMember() != null && event.getMember().getNickname() != null
                ? DiscordMessageFilter.sanitizeIncoming(event.getMember().getNickname())
                : displayName;
        String channel = DiscordMessageFilter.sanitizeIncoming(event.getChannel().getName());

        String template = settings.messages().getOrDefault(
                "messages.discord-to-minecraft", "&9[Discord] &f{player}: {message}");

        discord.receiveIncoming(
                event.getAuthor().getId(),
                new InboundMessageProcessor.ChatMessage(
                        template,
                        player,
                        displayName,
                        username,
                        content,
                        Instant.now().toString(),
                        channel,
                        nickname));

    }

}
