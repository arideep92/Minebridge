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
- **Fail-Safe Startup & Lifecycle**:
  - Automatically generates a well-commented `config.yml` on first startup if missing.
  - Never disables the plugin on missing or empty Discord tokens.
  - Minecraft-side features, commands, and listeners remain fully operational even if Discord is not yet configured or temporarily unreachable.
  - Instant live configuration reload via `/minebridge reload` to initialize Discord once credentials are set without restarting the server.
- **Strict Global Chat Synchronization**:
  - Forwards strictly genuine, public Minecraft global chat to Discord.
  - Automatically filters private messages (`/msg`, `/tell`, `/whisper`), faction, team, party, guild, staff, and local/proximity channels.
  - Respects cancelled events and excludes plugin-generated or system messages.
  - Natively supports Paper's modern `AsyncChatEvent` while preserving full Spigot compatibility.
  - Built-in deduplication prevents duplicate relays across dual-event dispatch pipelines or server reloads.
- **Event Announcements & Rich Embeds**:
  - Player join, leave, and first-join welcome embeds.
  - Categorized death announcements (player kills, mob kills, environmental damage, void, explosions) with customizable Discord embeds.
  - Advancement and challenge completion announcements with tier-aware embeds.
- **Customizable Appearance**:
  - Dynamic webhook username and avatar URLs (e.g. displaying player heads via `{player}`).
  - Customizable embed footer text and icon URLs.
  - Server name placeholder `{server}` across templates and footers.
- **Optional Compatibility Integrations**:
  - **LuckPerms**: Resolves effective player prefixes (`{prefix}`) and suffixes (`{suffix}`) via native API with full context and inheritance awareness, non-blocking cached lookups, and graceful fallback.
  - **Vault**: Compatible with Vault Chat and Permission providers as a fallback for alternative permission plugins. Prefers LuckPerms when installed to prevent duplicate prefixes.
  - **EssentialsX**: Resolves player nicknames (`{nickname}`) while ensuring private whispers, muted states, and command messages are never forwarded to Discord.
  - **Multiverse-Core**: Resolves world names (`{world}`) and custom world aliases (`{world_alias}`) with configurable cross-world chat handling (`GLOBAL`, `PER_WORLD`, `FILTERED`).
  - **PlaceholderAPI (PAPI)**: Safe expansion of server placeholders on configured message and webhook templates. Strict security guarantees: player-generated message content is never evaluated by PlaceholderAPI, preventing placeholder injection attacks.
  - **Zero Mandatory Dependencies**: All integrations are strictly optional soft dependencies. MineBridge remains a 100% standalone plugin with safe reflection adapters, isolated error handling, and zero third-party shading.
- **Modrinth Update Checker**:
  - Automatically checks for newer published releases on Modrinth once during startup.
  - Delivers semantic version release alerts to the server console and in-game administrators (`minebridge.admin`) without blocking the server thread.
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
- **Server Platform**: Spigot API `1.20`+ (e.g. `1.20.6-R0.1-SNAPSHOT`). Fully verified on Paper 1.20.6.
- **Optional Compatibility Integrations**:
  - **LuckPerms** (5.x): Effective prefixes (`{prefix}`) and suffixes (`{suffix}`).
  - **Vault**: Fallback Chat and Permission provider resolution.
  - **EssentialsX**: Player nicknames via `{nickname}` and `{display_name}`.
  - **Multiverse-Core**: World aliases via `{world_alias}` and multi-world chat scoping.
  - **PlaceholderAPI**: Placeholder expansion in configured templates.
  - *Note*: None of these plugins are required. MineBridge works fully standalone out of the box.

---

## Build and Installation

### Download or Build

1. Download the latest `MineBridge-1.3.1.jar` release from [Modrinth](https://modrinth.com/plugin/minebridge) or [GitHub Releases](https://github.com/Aridro/MineBridge/releases).
2. Alternatively, compile from source:
   ```sh
   ./gradlew shadowJar
   ```
   The shaded JAR will be created at `build/libs/MineBridge-1.3.1.jar`.

### Installation

1. Place `MineBridge-1.3.1.jar` into your server's `plugins/` directory.
2. Start the server. MineBridge will automatically generate the default `plugins/MineBridge/config.yml` on first boot.
3. Configure either **BOT Mode** or **WEBHOOK Mode** as described in the guides below.
4. Execute `/minebridge reload` (or `/mb reload`) in-game or from console to apply the configuration.

---

## First-Time Setup & Startup Lifecycle

MineBridge is designed with a resilient, decoupled initialization architecture. Minecraft-side functionality and Discord connectivity operate under separate initialization lifecycles:

```
Server Starts
     │
     ▼
Save Default Config (if missing)
     │
     ▼
Register Commands (/mb) & Minecraft Listeners
     │
     ▼
Validate Configuration Candidate
     ├── Valid Token/Webhook ───► Start Discord Client / Webhook ───► CONNECTED / READY
     └── Missing / Empty Token ──► Log Actionable Warning ─────────► INVALID_CONFIGURATION
                                      │
                                      ▼
                        Plugin Remains 100% Enabled
                        Minecraft Features Active
                        Admins configure token & run /mb reload
```

### Key Setup Principles:

1. **Automatic Configuration Generation**:
   - If `plugins/MineBridge/config.yml` does not exist when the server starts, MineBridge automatically creates it using the bundled default configuration.
2. **Starts Without a Discord Token**:
   - Out of the box, `discord.bot-token` is blank (`""`). MineBridge will **never** disable itself or crash the server due to an empty or missing token on startup.
3. **Minecraft Functionality Stays Available**:
   - All internal components—including Minecraft chat formatting, join/leave tracking, server status commands (`/mb server`, `/mb players`), and optional integration hooks—initialize immediately and remain active regardless of Discord connectivity.
4. **Configuring the Token Post-Startup**:
   - Open `plugins/MineBridge/config.yml`.
   - In **BOT Mode**, insert your bot token into `discord.bot-token` (or define the `MINEBRIDGE_BOT_TOKEN` environment variable) and set `discord.channel-id`.
   - In **WEBHOOK Mode**, set `discord.mode: WEBHOOK` and paste your webhook URL into `discord.webhook.url`.
5. **Live Reload via `/mb reload`**:
   - Run `/minebridge reload` (alias `/mb reload`).
   - MineBridge validates the updated configuration. If valid, it starts the Discord gateway connection (or webhook service) and smoothly transitions to active status without restarting the server or creating duplicate connections.
6. **Understanding Connection States**:
   - `/minebridge status` shows the current state:
     - `INVALID_CONFIGURATION`: The token or required channel ID/webhook URL is missing, empty, or syntactically invalid. The plugin remains active; check console warnings and update `config.yml`.
     - `CONNECTING`: Establishing gateway handshake with Discord.
     - `CONNECTED`: Successfully connected to Discord gateway (BOT mode).
     - `READY`: Webhook delivery worker is initialized and active (WEBHOOK mode).
     - `FAILED`: Discord rejected the credentials (e.g. invalid token, unprivileged gateway intent). Check console logs.
     - `DISCONNECTED`: Connection was closed or lost; auto-reconnect or reload will retry.

---

## Integration Setup Guides

### 1. BOT Mode Setup (Two-Way Chat & Events)

BOT mode connects to Discord's Gateway API using JDA. It supports bidirectional chat synchronization (Minecraft ↔ Discord) and outbound embed events.

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and click **New Application**.
2. Navigate to the **Bot** tab:
   - Click **Add Bot** / **Reset Token** to obtain your bot token.
   - Under **Privileged Gateway Intents**, enable **Message Content Intent** (required for the bot to read chat messages in Discord).
3. Invite the bot to your Discord server:
   - Go to **OAuth2** → **URL Generator**.
   - Select scope `bot`.
   - Select permissions: `View Channels`, `Send Messages`, `Embed Links`, `Read Message History`.
   - Open the generated URL to authorize the bot.
4. Copy your channel ID:
   - Enable Developer Mode in Discord (*User Settings* → *Advanced* → *Developer Mode*).
   - Right-click your relay text channel and click **Copy Channel ID**.
5. Edit `plugins/MineBridge/config.yml`:
   ```yaml
   discord:
     mode: BOT
     bot-token: "YOUR_DISCORD_BOT_TOKEN_HERE" # Or use env var MINEBRIDGE_BOT_TOKEN
     channel-id: "123456789012345678"
   server:
     name: "Survival SMP"
   ```
6. Run `/minebridge reload` in-game or console.

### 2. WEBHOOK Mode Setup (One-Way Outbound Relay)

WEBHOOK mode uses asynchronous HTTP webhooks. No bot application, bot token, or gateway intent is required.

> [!NOTE]
> Webhook mode is strictly one-way (Minecraft → Discord). Discord messages will not be relayed into Minecraft.

1. In your Discord server, open **Channel Settings** for the target text channel.
2. Navigate to **Integrations** → **Webhooks** → **New Webhook**.
3. Name your webhook (e.g. `Server Chat`) and click **Copy Webhook URL**.
4. Edit `plugins/MineBridge/config.yml`:
   ```yaml
   discord:
     mode: WEBHOOK
     webhook:
       url: "https://discord.com/api/webhooks/123456789012345678/abcdefg_hijk..."
       username: "{prefix}{player}"
       avatar-url: "https://mc-heads.net/avatar/{player}/100"
   server:
     name: "Survival SMP"
   ```
5. Run `/minebridge reload` in-game or console.

---

## Optional Plugin Integrations

MineBridge is a **100% standalone plugin** with zero mandatory dependencies. When popular third-party plugins are detected, MineBridge activates modular, fail-safe runtime adapters (soft dependencies) to enhance message formatting:

### 1. LuckPerms (Native Prefix & Suffix Support)
- **Features**: Retrieves effective player prefixes (`{prefix}`) and suffixes (`{suffix}`) using LuckPerms' native API.
- **Context-Aware**: Evaluates current permissions contexts (per-world, per-server, or custom rules) via `ContextManager.getQueryOptions(Player)`.
- **Inheritance & Clean Formatting**: Respects group inheritance. Prefixes and suffixes are stripped of Minecraft color codes (`§`, `&`) for clean Discord presentation. Empty prefixes/suffixes avoid extra spacing.
- **Precedence**: When both LuckPerms and Vault are present, LuckPerms takes precedence to avoid duplicate prefix lookups.

### 2. Vault (Permission & Chat Compatibility Layer)
- **Features**: Acts as an open compatibility layer for alternative permission and chat management plugins (e.g. PermissionsEx, UltraPermissions, GroupManager).
- **Independent Provider Resolution**: Resolves `net.milkbowl.vault.chat.Chat` and `net.milkbowl.vault.permission.Permission` providers independently through Bukkit's `ServicesManager`.
- **Graceful Fallback**: If LuckPerms is absent, Vault Chat provides player prefixes and suffixes. If no Vault Chat provider is registered, MineBridge falls back gracefully.

### 3. EssentialsX (Nickname Support)
- **Features**: Supports EssentialsX nicknames via `{nickname}` and `{display_name}` placeholders.
- **Formatting**: Automatically cleans EssentialsX nickname prefix tildes (`~`) and color codes. Falls back to Bukkit `player.getDisplayName()`, then to the real username.
- **Identity Integrity**: `{player}` **always** returns the genuine Minecraft account username (`player.getName()`). Nicknames are never used as player identifiers for moderation or Discord logging, preventing identity spoofing.
- **Channel Protection**: EssentialsX private messaging (`/msg`, `/tell`, `/w`, `/r`), muted players, and command executions are never forwarded to Discord.

### 4. Multiverse-Core (Multi-World Chat & Scoping)
- **Features**: Resolves world identifiers (`{world}`) and custom world aliases (`{world_alias}`).
- **Cross-World Chat Scoping**: Works in tandem with `chat.cross-world.mode` (`GLOBAL`, `PER_WORLD`, `FILTERED`) to manage chat delivery for multi-world survival, creative, minigame, or hub networks.
- **Fallbacks**: If Multiverse-Core is absent or an alias is unset, `{world_alias}` falls back to the Bukkit world name (`world.getName()`).

### 5. PlaceholderAPI (Custom Template Placeholders)
- **Features**: Allows expanding arbitrary PlaceholderAPI `%...%` placeholders inside configured server templates (`messages.minecraft-to-discord`, `discord.webhook.username`, `discord.webhook.avatar-url`).
- **Security & Injection Prevention**: PlaceholderAPI is **strictly** evaluated on trusted, administrator-defined configuration templates *prior* to message insertion. Player chat messages (`{message}`) are **never** processed with PlaceholderAPI, preventing malicious players from executing placeholder injection attacks (e.g., `%vault_eco_balance%`, `%server_ram%`).
- **Performance**: High-efficiency lookup with early fast-path exit when templates do not contain `%` characters.

---

## Global Chat Synchronization & Privacy Filtering

MineBridge enforces strict global-chat-only synchronization to prevent private or restricted channel communications from leaking into Discord.

### Cross-World Chat Modes

Multi-world servers can customize chat relay scope using the `chat.cross-world` configuration:

```yaml
chat:
  cross-world:
    mode: GLOBAL # GLOBAL, PER_WORLD, or FILTERED
    enabled-worlds:
      - "world"
      - "world_nether"
      - "world_the_end"
```

1. **`GLOBAL` (Default)**:
   - Forwards messages only if they are delivered to **all online players** across the entire server.
   - If a message is restricted to a single world while players are online in other worlds, it is treated as local/non-global chat and filtered out.
2. **`PER_WORLD`**:
   - Forwards messages if they are delivered publicly to **all online players in the sender's world**.
   - Preserves strict privacy: channel messages delivered to only a subset of players in that world (e.g. party, faction, or local proximity chat within that world) are still rejected.
3. **`FILTERED`**:
   - Forwards messages only if the sender is currently in one of the worlds listed in `enabled-worlds`.
   - Messages from unlisted worlds (e.g. staff worlds, test realms, minigames) are rejected.

### Excluded Messages

The following message categories are strictly excluded from Discord synchronization:
- **Private Messages**: Commands and direct messages (`/msg`, `/tell`, `/whisper`, `/w`, `/r`, `/pm`, `/dm`, `-> player`, `[PM] to ...`).
- **Faction & Team Chat**: Faction channels (`/f c f`, `[Faction]`, `@f`), team channels (`/team msg`, `[Team]`, `@t`), and alliance chat.
- **Party & Guild Chat**: Party channels (`/p`, `[Party]`, `@p`), guild channels (`[Guild]`, `@g`), and clan communications.
- **Staff & Admin Chat**: Staff channels (`/sc`, `[Staff]`, `[StaffChat]`, `[AdminChat]`, `@s`, `@a`).
- **Local & Proximity Chat**: Range-restricted chat (`/l`, `[Local]`, nearby/radius chat).
- **Cancelled Messages**: Any chat event cancelled by another plugin or moderation handler.
- **System & Plugin Messages**: Server announcements (`[Server]`, `[Announcement]`), command executions (`/...`), Citizens NPCs (`NPC` metadata), and non-player or offline senders.

### Technical Architecture

- **Global Chat Classification (`ChatClassifier`)**: Evaluates incoming chat events using a multi-stage inspection pipeline that fails closed whenever a message's classification is ambiguous or uncertain.
- **Audience & Recipient Verification**: Genuine public global chat is delivered to all online players. MineBridge compares the event's recipient list against the current online player collection (`recipients.containsAll(onlinePlayers)`). If a message targets only a subset of online players, it is rejected.
- **Paper Modern Chat API (`PaperChatBridge`)**: On Paper servers, MineBridge dynamically registers an `EventPriority.MONITOR` listener for Paper's adventure-based `io.papermc.paper.event.player.AsyncChatEvent`, verifying audience viewers and extracting plain text from Adventure components.
- **Chat Deduplication (`ChatDeduplicator`)**: A sliding 2-second window keyed by `(PlayerUUID, MessageContent)` ensures that once a message is forwarded, duplicate event dispatches from Paper's dual modern/legacy pipelines or reload cycles are suppressed.

---

## Configuration Reference

### Discord & Core Settings

| Setting | Default | Type | Description |
| :--- | :--- | :--- | :--- |
| `discord.mode` | `BOT` | String | Integration mode: `BOT` or `WEBHOOK` (case-insensitive). |
| `discord.bot-token` | `""` | String | Discord bot token for BOT mode. (Or set `MINEBRIDGE_BOT_TOKEN` in environment). |
| `discord.channel-id` | `""` | String | 17–20 digit Discord Snowflake channel ID (required in BOT mode when relay features are enabled). |
| `discord.webhook.url` | `""` | String | Discord webhook HTTPS URL (required for WEBHOOK mode; points to discord.com or discordapp.com). |
| `discord.webhook.username` | `"{prefix}{player}"` | String | Webhook author display name. Supports `{player}`, `{nickname}`, `{prefix}`, `{suffix}`, `{world}`, `{world_alias}`, `{server}`, and PAPI `%...%`. |
| `discord.webhook.avatar-url` | `""` | String | Webhook author avatar URL. Supports `{player}` and PAPI `%...%`. |
| `discord.embed.footer.text` | `""` | String | Custom embed footer text. Supports `{server}`. |
| `discord.embed.footer.icon-url` | `""` | String | Custom embed footer icon URL (HTTP/HTTPS). |
| `server.name` | `"Minecraft Server"` | String | Server name placeholder value used by `{server}` across templates and footers. |
| `chat.cross-world.mode` | `GLOBAL` | String | Cross-world chat scope: `GLOBAL`, `PER_WORLD`, or `FILTERED`. |
| `chat.cross-world.enabled-worlds` | `[]` | List | List of allowed world names when `mode` is `FILTERED`. |
| `features.chat-relay` | `true` | Boolean | Enable chat relay (two-way for BOT, outbound for WEBHOOK). |
| `features.join-leave-notifications` | `true` | Boolean | Enable player join/leave broadcasts and Discord notifications. |
| `features.server-status` | `true` | Boolean | Enable the `/minebridge server` status command. |
| `features.player-list` | `true` | Boolean | Enable the `/minebridge players` command. |
| `update-checker.enabled` | `true` | Boolean | Check Modrinth on startup and notify admins if an update is available. |

### Message Templates and Placeholders

| Template | Default | Supported Placeholders |
| :--- | :--- | :--- |
| `messages.minecraft-to-discord` | `"{prefix}**{player}**: {message}"` | `{player}`, `{nickname}`, `{display_name}`, `{message}`, `{timestamp}`, `{server}`, `{prefix}`, `{suffix}`, `{world}`, `{world_alias}`, `%...%` (PlaceholderAPI) |
| `messages.discord-to-minecraft` | `&9[Discord] &f{player}: {message}` | `{player}`, `{display_name}`, `{username}`, `{message}`, `{timestamp}`, `{server}`, `{prefix}` |
| `messages.player-join` | `🟢 **{player}** joined the server.` | `{player}`, `{timestamp}`, `{server}` |
| `messages.player-leave` | `🔴 **{player}** left the server.` | `{player}`, `{timestamp}`, `{server}` |
| `messages.server-status` | `&7Server &8\| &a{online}&7/&f{max} players online` | `{online}`, `{max}` |
| `messages.player-list` | `&7Online players (&f{online}&7/&f{max}&7): {players}` | `{online}`, `{max}`, `{players}` |

> [!NOTE]
> - `{player}` is **always** the real, unalterable Minecraft username (`player.getName()`).
> - `{nickname}` and `{display_name}` resolve from EssentialsX (or display name fallback).
> - `{prefix}` and `{suffix}` are resolved from LuckPerms (or Vault Chat fallback).
> - `{world_alias}` is resolved from Multiverse-Core (falling back to world name).
> - Arbitrary `%placeholder%` tokens are expanded via PlaceholderAPI when installed, evaluated strictly on templates (never on user message text).

### Event Notification Settings

- **Death Messages (`death-messages`)**:
  - `death-messages.enabled`: Master toggle for death notifications (`true`/`false`).
  - `death-messages.minecraft.enabled` / `broadcast`: In-game death message formatting.
  - `death-messages.discord.enabled`: Relay death embeds to Discord.
  - `death-messages.discord.channel-id`: Specific channel ID for death embeds in BOT mode (leave blank to use main channel).
  - `death-messages.discord.title` / `description` / `color`: Embed customization (supports `{victim}`, `{killer}`, `{cause}`, `{category}`, `{message}`).
  - `death-messages.categories.*`: Enable/disable specific death categories (`player-kill`, `mob-kill`, `fall`, `lava`, `fire`, `drowning`, `void`, `explosion`, `environmental`, `other`).
- **Achievements & Advancements (`achievements`)**:
  - `achievements.enabled`: Master toggle for advancement notifications (`true`/`false`).
  - `achievements.minecraft.enabled`: In-game advancement messages.
  - `achievements.discord.enabled`: Relay advancement embeds to Discord.
  - `achievements.discord.channel-id`: Specific channel ID for advancement embeds in BOT mode.
  - `achievements.challenges.enabled`: Special formatting for challenge advancements.
  - `achievements.categories.*`: Filter by category (`story`, `nether`, `end`, `adventure`, `husbandry`, `other`).
- **Discord Event Embeds (`discord-events`)**:
  - `discord-events.enabled` / `embeds.enabled`: Master toggle and embed formatting.
  - `discord-events.embeds.footer`: Fallback footer string.
  - `discord-events.player-events`: Channel ID and individual toggles for `join`, `leave`, and `first-join`.
  - `discord-events.achievements` / `deaths`: Channel routing and subcategory toggles.

---

## Commands and Permissions

MineBridge provides a unified administrative and player command interface under `/minebridge` (alias `/mb`):

| Command | Permission | Default | Description |
| :--- | :--- | :--- | :--- |
| `/minebridge help` | None | Everyone | Displays available commands tailored to sender permissions and enabled features. |
| `/minebridge status` | None | Everyone | Shows active mode (`BOT` vs `WEBHOOK`), connection/gateway state, queue metrics, uptime, and hook statuses. |
| `/minebridge server` | None | Everyone | Shows online player count and server capacity (controlled by `features.server-status`). |
| `/minebridge players` | None | Everyone | Lists names of currently online players (controlled by `features.player-list`). |
| `/minebridge reload` | `minebridge.admin` | `op` | Validates configuration, smoothly transitions Discord services, and reloads settings without server restart. |

**Command Alias**: `/mb` (e.g. `/mb status`, `/mb reload`).

**Permission Node**:
- `minebridge.admin`: Grants access to `/minebridge reload` and administrative update checker notifications. Defaults to server operators (`op`).

---

## Troubleshooting & Common Issues

### Startup & First-Time Configuration
- **MineBridge started with `INVALID_CONFIGURATION`**: This is completely normal on the first startup. It indicates that `discord.bot-token` or `discord.channel-id` (or `discord.webhook.url`) is empty. The plugin remains active; simply fill in your credentials in `plugins/MineBridge/config.yml` and execute `/mb reload`.
- **Did MineBridge get disabled on startup?**: In v1.3.1, missing Discord credentials will **never** disable MineBridge. If `/mb` commands work, the plugin is active and waiting for your configuration.
- **Reloading after adding token**: Run `/mb reload`. If the configuration is valid, the status will transition from `INVALID_CONFIGURATION` to `CONNECTING` and then `CONNECTED` (or `READY` for webhooks).

### Bot Mode
- **Status shows `FAILED`**: Discord rejected the configured bot credentials. Check console logs for details. Common causes:
  - An invalid, revoked, or mistyped bot token.
  - **Message Content Intent** is disabled in the Discord Developer Portal under *Bot* settings.
- **Status shows `CONNECTING` or `DISCONNECTED`**: The bot is attempting to connect to Discord's gateway. Verify the server has outbound internet access and can reach Discord endpoints.
- **Bot does not relay messages sent in Discord**:
  - Verify that **Message Content Intent** is toggled ON in the Developer Portal.
  - Verify that `discord.channel-id` matches the exact text channel Snowflake ID.
  - Ensure the bot has `View Channel`, `Send Messages`, and `Read Message History` permissions in that channel.

### Webhook Mode
- **Status shows `NOT_STARTED` or validation errors on webhook**:
  - Verify that `discord.webhook.url` is non-empty and starts with `https://discord.com/api/webhooks/` or `https://discordapp.com/api/webhooks/`.
- **Discord messages not appearing in Minecraft**:
  - Webhooks are strictly one-way (outbound from Minecraft to Discord). Webhook mode cannot read messages from Discord. Switch to `BOT` mode if you need two-way chat.
- **Dropped messages during chat spikes**:
  - MineBridge buffers messages in a bounded FIFO queue and honors Discord's HTTP 429 rate limits. If thousands of messages are sent rapidly, excess messages may be dropped to protect server memory. Check dropped counts in `/mb status`.

### Optional Integrations
- **Prefixes or suffixes not appearing**:
  - Verify LuckPerms or Vault is installed and running (`/plugins`).
  - Verify the player's group has a prefix assigned.
  - Check `/mb status` to verify `LuckPerms Hook` or `Vault Hook` is recognized as active.
- **Nicknames not showing**:
  - Ensure EssentialsX is installed and players have nicknames set. Make sure `{nickname}` or `{display_name}` is included in `messages.minecraft-to-discord`.
- **PlaceholderAPI placeholders not expanding**:
  - Ensure PlaceholderAPI is installed and the relevant PAPI expansion is downloaded (e.g. `/papi ecloud download Server`).
  - Remember that PlaceholderAPI expansion applies to configuration templates, not directly to player chat text (by security design).

---

## License

MineBridge is licensed under the MIT License. See [LICENSE](LICENSE) for details.
