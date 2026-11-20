# MineBridge 1.3.0 — The Dual Integration Update

MineBridge 1.3.0 introduces Webhook Mode, embed footer customization, server placeholders, and optional LuckPerms prefix support alongside the existing JDA Bot Mode. The project runs on Java 21, targets Spigot API 1.20.6, and is verified on Paper 1.20.6.

## Added

- **Dual Integration Modes**: Choose between `BOT` mode (full two-way chat synchronization and embeds via Discord bot) and `WEBHOOK` mode (lightweight one-way outbound delivery via Discord webhooks).
- **Modrinth Update Checker**: Automated, asynchronous startup check for published MineBridge releases on Modrinth via public API v2. Displays semantic release notifications to server console and in-game administrators (`minebridge.admin`) without blocking the server thread.
- **Outgoing Webhook Integration**: Asynchronous, rate-limited delivery for Minecraft chat, player joins/leaves, deaths, and advancements via standard Discord Webhooks using OkHttp.
- **Webhook Customization**: Configure webhook usernames and avatar URLs per message using `{player}` and `{server}` placeholders (e.g. avatar heads via `https://mc-heads.net/avatar/{player}/100`).
- **Embed Footer Customization**: Set custom footer text and icon URLs on all Discord embeds with `{server}` placeholder support.
- **LuckPerms Prefix Support**: Soft dependency on LuckPerms 5.x allows `{prefix}` in chat templates, safely resolved from in-memory cache without blocking the server thread.
- **Mode-Aware `/minebridge status`**: Displays active mode (`BOT` vs `WEBHOOK`), webhook/gateway state, queue usage, dropped counts, and LuckPerms availability without ever exposing sensitive tokens or webhook URLs.

## Reliability and Improvements

- **Lifecycle Management**: Mutually exclusive integration startup ensures JDA is never initialized in Webhook mode, saving memory and CPU cycles.
- **Seamless Reload**: In-place listener update (`/minebridge reload`) dynamically transitions between BOT and WEBHOOK modes without duplicate Bukkit event listeners or memory leaks.
- **Rate Limit & Backoff**: Webhook deliveries respect Discord's HTTP 429 response headers and JSON `retry_after` payload, with exponential backoff and bounded queue overflow protection.
- **Placeholder Security**: Single-pass non-recursive placeholder substitution prevents nested placeholder injection.

## Compatibility

- Existing BOT configurations continue to work without modification.
- BOT remains the default integration mode.
- WEBHOOK mode is outbound-only (Minecraft → Discord) and does not receive Discord messages.
- LuckPerms is completely optional: if not present, `{prefix}` resolves safely to an empty string.

## Installation

Place `build/libs/MineBridge-1.3.0.jar` into your server's `plugins/` directory and restart or run `/minebridge reload`.
