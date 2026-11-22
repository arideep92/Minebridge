# MineBridge 1.4.1 — Dynamic Webhook Player Avatars

MineBridge 1.4.1 brings dynamic Minecraft player skin avatars to Discord Webhook notifications, fully integrating Crafatar's UUID-based avatar API across chat relay, player joins, leaves, deaths, and advancements.

The project runs on Java 21, targets Spigot API 1.20.6, and is verified on Paper 1.21.11.

## What's New

### Dynamic Player Avatars for Discord Webhooks
- **Crafatar UUID Avatar Resolution**: Every player-related Discord webhook notification now uses the player's authentic Minecraft skin head avatar resolved via `https://crafatar.com/avatars/{uuid}?overlay`.
- **Chat Synchronization**: In Webhook mode, chat messages sent by Minecraft players appear in Discord with the player's custom username and their actual skin avatar.
- **Player Events**: Join and leave notifications display the player's skin head avatar on the Discord webhook embed.
- **Combat & Death Notifications**: Player death announcements display the deceased victim's skin head avatar on the Discord webhook embed.
- **Advancement & Challenge Notifications**: Unlocked advancements and completed challenges display the achiever's skin head avatar on the Discord webhook embed.
- **Server Notifications**: Server start and stop notifications retain the configured static server webhook avatar.
- **Resilient Fallback**: If a player's UUID is missing or avatar resolution encounters an error, notifications fall back safely to the configured server webhook avatar without interrupting delivery.
- **Zero State Leakage**: Webhook avatars are applied dynamically per HTTP payload, ensuring consecutive messages from different players or the server never leak or cross-contaminate avatars.

### Startup Branding & Logging
- **Reliable Startup Banner**: The ASCII startup banner reliably prints to the server console and startup log (`logs/latest.log`) via the plugin logger.
- **Version Reporting**: Accurately displays version `1.4.1` and active integration mode on server enable.

## Compatibility

- Fully backward compatible with existing configurations.
- Existing `BOT` and `WEBHOOK` configurations continue to work without modification.
- Custom avatar URL templates configured in `advanced.yml` or `legacy-config.yml` remain respected.

## Installation

Place `build/libs/MineBridge-1.4.1.jar` into your server's `plugins/` directory and restart or run `/minebridge reload`.
