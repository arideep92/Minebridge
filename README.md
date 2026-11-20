# MineBridge

[![GitHub Repository](https://img.shields.io/badge/GitHub-Aridro%2FMineBridge-blue?logo=github)](https://github.com/Aridro/MineBridge)
[![Modrinth Download](https://img.shields.io/badge/Modrinth-MineBridge-00AF5C?logo=modrinth)](https://modrinth.com/plugin/minebridge)

MineBridge is a lightweight, reliable Minecraft–Discord integration plugin for Spigot and Paper servers. It connects your Minecraft server and Discord community with two distinct integration modes: **Bot Mode** and **Webhook Mode**.

- **Repository**: [https://github.com/Aridro/MineBridge](https://github.com/Aridro/MineBridge)
- **Modrinth Downloads**: [https://modrinth.com/plugin/minebridge](https://modrinth.com/plugin/minebridge)

---

## Features

- **Dual Integration Modes**:
  - **BOT Mode (Default)**: Bidirectional chat synchronization (Minecraft ↔ Discord) and outbound event embeds using JDA 5.
  - **WEBHOOK Mode**: Lightweight one-way outbound delivery (Minecraft → Discord) via asynchronous HTTP webhooks without requiring a bot account, bot token, or gateway connection.
- **Event Announcements & Rich Embeds**:
  - Player join, leave, and first-join welcome embeds.
  - Categorized death announcements (player kills, mob kills, environmental damage, void, explosions) with customizable Discord embeds.
  - Advancement and challenge completion announcements with tier-aware embeds.
- **Customizable Appearance**:
  - Dynamic webhook username and avatar URLs (e.g. displaying player heads).
  - Customizable embed footer text and icon URLs.
  - Server name placeholder `{server}` across templates and footers.
- **Optional LuckPerms Integration**:
  - Soft-dependency integration: automatically resolves player group prefixes (`{prefix}`) in chat templates using cached metadata without blocking the server thread.
  - Works seamlessly with zero configuration if LuckPerms is absent.
- **Reliability & Rate Limiting**:
  - Bounded FIFO queues for outgoing messages.
  - Automatic Discord HTTP 429 rate-limit backoff and retry handling (`Retry-After` / `retry_after`).
  - Secret masking in logs and status commands (bot tokens and webhook URLs are never logged).
  - Zero-event-leak in-place configuration reload (`/minebridge reload`).

> [!IMPORTANT]
> **WEBHOOK Mode is strictly outbound (Minecraft → Discord).** Discord chat messages cannot be received or relayed into Minecraft in WEBHOOK mode. If you require two-way chat synchronization, use **BOT Mode**.

---

## Requirements and Compatibility

- **Java**: Java 21 or newer.
- **Server Platform**: Spigot API `1.20.6-R0.1-SNAPSHOT` (`api-version: '1.20'`). Verified on Paper 1.20.6.
- **Permissions Plugin (Optional)**: LuckPerms 5.x for `{prefix}` placeholder support.

---

## Build and Installation

### Download or Build

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/minebridge) or [GitHub Releases](https://github.com/Aridro/MineBridge/releases).
2. Alternatively, compile from source:
   ```sh
   ./gradlew shadowJar
   ```
   The shaded JAR will be created at `build/libs/MineBridge-1.3.0.jar`.

### Installation

1. Place `MineBridge-1.3.0.jar` into your server's `plugins/` directory.
2. Start the server once to generate `plugins/MineBridge/config.yml`.
3. Configure either **BOT Mode** or **WEBHOOK Mode** as detailed below.
4. Run `/minebridge reload` or restart the server.

---

## Integration Setup Guides

### 1. BOT Mode Setup (Two-Way Chat & Events)

BOT mode connects via Discord's Gateway API using JDA.

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and create a new application and bot.
2. Under the **Bot** tab:
   - Enable **Message Content Intent** under Privileged Gateway Intents.
   - Reset and copy the **Bot Token**.
3. Invite the bot to your Discord server with **View Channel** and **Send Messages** permissions.
4. Copy the **Channel ID** of your target Discord text channel (enable Developer Mode in Discord settings, right-click the channel, and select *Copy Channel ID*).
5. In `plugins/MineBridge/config.yml`:
   ```yaml
   discord:
     mode: BOT
     bot-token: "YOUR_BOT_TOKEN" # Or set the MINEBRIDGE_BOT_TOKEN environment variable
     channel-id: "123456789012345678"
   ```
6. Reload the plugin: `/minebridge reload`.

### 2. WEBHOOK Mode Setup (One-Way Outbound Relay)

WEBHOOK mode uses Discord Webhooks. No bot account, token, or gateway intent is required.

1. In your Discord server, open **Channel Settings** for the desired text channel.
2. Navigate to **Integrations** → **Webhooks** → **New Webhook**.
3. Name the webhook (e.g. `Server Relay`) and click **Copy Webhook URL**.
4. In `plugins/MineBridge/config.yml`:
   ```yaml
   discord:
     mode: WEBHOOK
     webhook:
       url: "https://discord.com/api/webhooks/123456789012345678/abcdefg_hijk..."
       username: "{player}"
       avatar-url: "https://mc-heads.net/avatar/{player}/100"
   ```
5. Reload the plugin: `/minebridge reload`.

---

## LuckPerms Integration

MineBridge includes a built-in soft dependency on [LuckPerms](https://luckperms.net/).

- If LuckPerms is installed and enabled, you can include `{prefix}` in chat templates:
  ```yaml
  messages:
    minecraft-to-discord: "{prefix}**{player}**: {message}"
    discord-to-minecraft: "&9[Discord] &f{prefix}{player}: {message}"
  ```
- Cached metadata is read directly from memory; prefix retrieval never blocks the main Minecraft server thread.
- If LuckPerms is not installed or the player has no prefix, `{prefix}` resolves safely to an empty string.

---

## Configuration Reference

### Main Settings

| Setting | Default | Description |
| :--- | :--- | :--- |
| `discord.mode` | `BOT` | Integration mode: `BOT` or `WEBHOOK` (case-insensitive). |
| `discord.bot-token` | `""` | Bot token for BOT mode. (Prefer setting `MINEBRIDGE_BOT_TOKEN` in the environment). |
| `discord.channel-id` | `""` | 17–20 digit Discord Snowflake channel ID (required for BOT mode). |
| `discord.webhook.url` | `""` | Discord webhook URL (required for WEBHOOK mode; must be HTTPS discord.com/discordapp.com). |
| `discord.webhook.username` | `"{player}"` | Webhook message author name. Supports `{player}` and `{server}`. |
| `discord.webhook.avatar-url` | `""` | Webhook avatar image URL. Supports `{player}`. |
| `discord.embed.footer.text` | `""` | Embed footer text. Supports `{server}`. |
| `discord.embed.footer.icon-url` | `""` | Embed footer icon URL (HTTP/HTTPS). |
| `server.name` | `"Minecraft Server"` | Server name used by `{server}` placeholder. |

### Message Templates and Placeholders

| Template | Default | Supported Placeholders |
| :--- | :--- | :--- |
| `messages.minecraft-to-discord` | `**{player}**: {message}` | `{player}`, `{message}`, `{timestamp}`, `{server}`, `{prefix}` |
| `messages.discord-to-minecraft` | `&9[Discord] &f{player}: {message}` | `{player}`, `{display_name}`, `{username}`, `{message}`, `{timestamp}`, `{server}`, `{prefix}` |
| `messages.player-join` | `🟢 **{player}** joined the server.` | `{player}`, `{timestamp}`, `{server}` |
| `messages.player-leave` | `🔴 **{player}** left the server.` | `{player}`, `{timestamp}`, `{server}` |
| `messages.server-status` | `&7Server &8\| &a{online}&7/&f{max} players online` | `{online}`, `{max}` |
| `messages.player-list` | `&7Online players (&f{online}&7/&f{max}&7): {players}` | `{online}`, `{max}`, `{players}` |

---

## Commands and Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/minebridge help` | None | Displays available commands. |
| `/minebridge status` | None | Shows active mode, connection/webhook health, queue metrics, and uptime. |
| `/minebridge server` | None | Displays online player count and server capacity. |
| `/minebridge players` | None | Lists names of currently online players. |
| `/minebridge reload` | `minebridge.admin` (op) | Validates and reloads configuration, smoothly transitioning services. |

Command aliases: `/mb`

---

## Troubleshooting

### Bot Mode
- **Status shows `NOT_STARTED` or `SHUTDOWN`**: Verify `discord.bot-token` or `MINEBRIDGE_BOT_TOKEN`. Ensure internet connectivity.
- **Bot does not relay Discord messages**: Verify that **Message Content Intent** is toggled on in the Discord Developer Portal under your application's Bot settings.
- **Configured channel unavailable**: Verify that `discord.channel-id` is correct and that the bot has been invited to the server and granted **View Channel** and **Send Messages** permissions.

### Webhook Mode
- **Status shows `INVALID_WEBHOOK`**: Confirm that `discord.webhook.url` is a valid HTTPS Discord webhook URL starting with `https://discord.com/api/webhooks/` or `https://discordapp.com/api/webhooks/`.
- **Messages dropped during high traffic**: MineBridge buffers messages in a bounded queue and respects Discord's HTTP 429 rate limits automatically. If your server produces massive bursts of chat, temporary queue drops may occur to prevent memory leaks.
- **Discord chat not appearing in Minecraft**: This is intended behavior. Discord Webhooks are one-way outbound only. Switch to `BOT` mode if bidirectional relay is needed.

### Diagnostics & Privacy
- All diagnostic warnings are rate-limited to avoid log flooding.
- MineBridge **never** prints Discord bot tokens or webhook URLs in server logs, chat messages, or status outputs.

---

## License

MineBridge is licensed under the MIT License. See [LICENSE](LICENSE) for details.
