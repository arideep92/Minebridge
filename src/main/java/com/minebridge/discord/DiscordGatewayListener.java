package com.minebridge.discord;

import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionInvalidateEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Reports gateway lifecycle events for the active JDA client. */
public class DiscordGatewayListener extends ListenerAdapter {

    private final DiscordService service;

    public DiscordGatewayListener(DiscordService service) {
        this.service = service;
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewayDisconnect(
                event.getJDA(), event.getCloseCode(), event.isClosedByServer());
    }

    @Override
    public void onSessionResume(SessionResumeEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewayResume(event.getJDA());
    }

    @Override
    public void onSessionInvalidate(SessionInvalidateEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewaySessionInvalidate(event.getJDA());
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewaySessionRecreate(event.getJDA());
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewayReady(event.getJDA());
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewayShutdown(event.getJDA(), event.getCloseCode(), event.getCode());
    }

    @Override
    public void onException(ExceptionEvent event) {
        if (event == null || service == null) {
            return;
        }
        service.onGatewayException(event.getJDA(), event.getCause());
    }
}
