# MineBridge

[![GitHub Repository](https://img.shields.io/badge/GitHub-Aridro%2FMineBridge-blue?logo=github)](https://github.com/Aridro/MineBridge)
[![Modrinth Download](https://img.shields.io/badge/Modrinth-MineBridge-00AF5C?logo=modrinth)](https://modrinth.com/plugin/minebridge)

MineBridge is a lightweight, reliable Minecraft–Discord integration plugin for Spigot and Paper servers. It connects your Minecraft server and Discord community with three distinct integration modes: **Bot Mode**, **Webhook Mode**, and **Hybrid Mode**.

- **Repository**: [https://github.com/Aridro/MineBridge](https://github.com/Aridro/MineBridge)
- **Modrinth Downloads**: [https://modrinth.com/plugin/minebridge](https://modrinth.com/plugin/minebridge)

---

## Features

- **Three Integration Modes**:
  - **BOT Mode (Default)**: Bidirectional chat synchronization (Minecraft ↔ Discord) and outbound event embeds using JDA 5.
  - **WEBHOOK Mode**: Lightweight one-way outbound delivery (Minecraft → Discord) via asynchronous HTTP webhooks without requiring a bot account, bot token, or gateway connection.
  - **HYBRID Mode**: The best of both worlds. Outbound Minecraft chat is delivered via Webhooks with player skin heads and dynamic identities, while inbound Discord chat and commands are received via the Discord Bot gateway. Event notifications follow exactly one configured transport path (`WEBHOOK` default, or `BOT`).
- **Directional Transport Architecture**:
  - Clean separation of outbound delivery and inbound reception prevents duplicate messages and infinite relay loops.
  - Independent failure resilience: Webhook delivery continues if the Discord bot gateway disconnects; inbound bot relay continues if the webhook URL encounters rate limits or errors.
  - Zero automatic transport failover that risks duplicate messages.
- **DiscordSRV-Inspired Chat Integration & Webhook Redesign**:
  - **Bidirectional Markdown Translation**: Translates Minecraft formatting codes (`&`, `§`, hex) into Discord Markdown (`**bold**`, `*italic*`, `__underline__`, `~~strike~~`, `||spoiler||`), and translates Discord Markdown into Minecraft chat codes. Raw `§` characters from Discord users are stripped to prevent color injection or rank spoofing.
  - **Skin Avatar Resolution (`SkinAvatarService`)**: Dynamic player skin avatar resolution via Crafatar (`https://crafatar.com/avatars/{uuid}?overlay`) using player UUIDs with a bounded in-memory TTL cache (60m TTL, max 2000 entries) and safe server fallback.
  - **Long Webhook Message Splitting (`WebhookMessageSplitter`)**: Splits messages exceeding 2000 characters along natural line and word boundaries while preserving formatting styles across segment boundaries and preventing broken UTF-16 surrogate pairs / emojis.
  - **Webhook Identity & Payload Sanitization**: Enforces 1–80 char usernames, defuses mass mentions (`@everyone`, `@here`), prevents disallowed names ("clyde"), and validates avatar URLs.
  - **Discord Attachments & Inbound Polish**: Relays readable file upload links (e.g. `[image.png]`) from Discord into Minecraft, with expanded placeholders (`{channel}`, `{nickname}`, `{uuid}`).
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
  - Dynamic Minecraft player skin avatars for Discord webhooks (chat, player joins/leaves, deaths, advancements/challenges) with Crafatar integration.
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

1. Download the latest `MineBridge-1.4.1.jar` release from [Modrinth](https://modrinth.com/plugin/minebridge) or [GitHub Releases](https://github.com/arideep92/Minebridge/releases).
2. Alternatively, compile from source:
   ```sh
   ./gradlew shadowJar
   ```
   The shaded JAR will be created at `build/libs/MineBridge-1.4.1.jar`.

### Installation

1. Place `MineBridge-1.4.1.jar` into your server's `plugins/` directory.
2. Start the server. MineBridge will automatically generate the default `plugins/MineBridge/config.yml` on first boot.
3. Configure **BOT Mode**, **WEBHOOK Mode**, or **HYBRID Mode** as described in the guides below.
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
       avatar-url: "https://crafatar.com/avatars/{uuid}?overlay"
   server:
     name: "Survival SMP"
   ```
5. Run `/minebridge reload` in-game or console.

### 3. HYBRID Mode Setup (Best of Both Worlds)

HYBRID mode pairs high-fidelity webhook outbound delivery with real-time gateway bot reception:

- **Minecraft → Discord**: Webhook delivers player messages using player skin heads (`https://mc-heads.net/avatar/{player}/100`) and custom display names. The Bot NEVER sends outbound Minecraft chat, ensuring zero duplicate messages.
- **Discord → Minecraft**: Bot gateway listens to the configured Discord channel and relays chat messages into Minecraft. Bot messages and Webhook messages are automatically filtered out, preventing feedback loops.
- **Event Notifications**: Player joins, leaves, deaths, advancements, and server lifecycle embeds are delivered via **exactly one** configured transport path (`discord.hybrid.event-delivery: WEBHOOK | BOT`, default: `WEBHOOK`).
- **Failure Resilience**: Webhook delivery and Bot gateway reception operate independently. If the Discord bot disconnects, outbound player chat continues uninterrupted. If the webhook encounters rate-limits, inbound Discord chat continues uninterrupted.

#### Transport Architecture Matrix

| Direction / Feature | BOT Mode | WEBHOOK Mode | HYBRID Mode |
|---|---|---|---|
| **Minecraft → Discord chat** | Bot | Webhook | **Webhook** (dynamic player skin heads) |
| **Discord → Minecraft chat** | Bot | Unsupported | **Bot** (real-time gateway listener) |
| **Minecraft event notifications** | Bot channel embeds | Webhook embeds | **Single configured path** (`WEBHOOK` default, or `BOT`) |
| **Discord bot connection** | Required | Not connected | **Required** (inbound chat, gateway events) |
| **Discord webhook** | Not required | Required | **Required** (outbound chat, default events) |
| **Discord → Minecraft commands** | Supported | Unsupported | **Supported** (via bot) |
| **Player identity** | Single bot identity | Dynamic player avatar | **Dynamic player avatar** |

#### Setup Instructions:

1. Create a Discord Bot application and obtain your `bot-token` and `channel-id` (see [BOT Mode Setup](#1-bot-mode-setup-two-way-chat--events)).
2. Create a Discord Webhook in your relay channel and copy your `webhook.url` (see [WEBHOOK Mode Setup](#2-webhook-mode-setup-one-way-outbound-relay)).
3. Edit `plugins/MineBridge/config.yml`:
   ```yaml
   discord:
     mode: HYBRID
     bot-token: "YOUR_DISCORD_BOT_TOKEN_HERE" # Or use env var MINEBRIDGE_BOT_TOKEN
     channel-id: "123456789012345678"
     webhook:
       url: "https://discord.com/api/webhooks/123456789012345678/abcdefg_hijk..."
       username: "{prefix}{player}"
       avatar-url: "https://crafatar.com/avatars/{uuid}?overlay"
     hybrid:
       # Delivery transport for event embeds: WEBHOOK (default) or BOT.
       event-delivery: WEBHOOK
   server:
     name: "Survival SMP"
   ```
4. Run `/minebridge reload` (or `/mb reload`) in-game or console.

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

## Modular Configuration Architecture

MineBridge v1.4.1 organizes settings into **four distinct, modular YAML configuration files**, providing a beginner-friendly setup for new servers while offering comprehensive advanced customization:

```
plugins/MineBridge/
├── config.yml      # Core settings, connection credentials, and high-level feature toggles
├── messages.yml    # All user-facing chat, player, death, and advancement notification templates
├── events.yml      # Event notification routing, category toggles, embed styling, and channel IDs
└── advanced.yml    # Cross-world routing, Markdown translation toggles, webhook identity, and footers
```

### Environment Variable Resolution

MineBridge securely resolves environment variables inside configuration files at runtime:
- `${VAR_NAME}` — resolves from the environment variable; flags an error if missing.
- `${VAR_NAME:default_value}` — resolves from the environment, falling back to `default_value` if unset.

Environment variable expansion is supported on all sensitive and environment-specific settings:
- `discord.bot-token` (e.g. `bot-token: "${MINEBRIDGE_BOT_TOKEN}"`)
- `discord.channel-id` (e.g. `channel-id: "${MINEBRIDGE_CHANNEL_ID}"`)
- `discord.webhook.url` (e.g. `url: "${DISCORD_WEBHOOK_URL}"`)
- Channel IDs in `events.yml` (`player-events.channel-id`, `deaths.discord.channel-id`, `advancements.discord.channel-id`, `server-events.channel-id`)

> [!TIP]
> **Zero Leakage Security**: Resolved secrets are held in memory only and are **never** written back to disk or exposed in `/mb status` or server log files.

### Automatic Migration & Idempotent Backups

When upgrading an existing server from single-file `config.yml` setups:
1. MineBridge automatically detects the legacy single-file configuration on startup.
2. A timestamped safety backup is created under `plugins/MineBridge/backups/config-legacy-YYYYMMDD-HHmmss.yml`.
3. All existing configuration keys (all 108 settings) and custom administrator modifications are migrated into the 4 modular files with zero data loss.
4. Unrecognized custom keys are preserved in the backup file and logged in the console.
5. Migration is fully **idempotent**: once migrated to `migration.version: 1`, subsequent restarts leave files untouched.

---

## Configuration Reference

### 1. `config.yml` (Core Settings & Features)

| Setting | Default | Type | Description |
| :--- | :--- | :--- | :--- |
| `discord.mode` | `BOT` | String | Integration mode: `BOT`, `WEBHOOK`, or `HYBRID`. |
| `discord.bot-token` | `""` | String | Discord bot token for BOT and HYBRID modes (supports `${VAR}`). |
| `discord.channel-id` | `""` | String | 17–20 digit Snowflake ID of primary chat channel (supports `${VAR}`). |
| `discord.webhook.url` | `""` | String | Full Discord Webhook URL for WEBHOOK and HYBRID modes (supports `${VAR}`). |
| `features.chat-relay` | `true` | Boolean | Enable chat relay between Minecraft and Discord. |
| `features.join-leave` | `true` | Boolean | Enable player join, leave, and first-join notifications. |
| `features.deaths` | `true` | Boolean | Master toggle for player death announcements. |
| `features.advancements` | `true` | Boolean | Master toggle for advancement and challenge completion announcements. |
| `features.server-status` | `true` | Boolean | Enable the `/minebridge server` status command. |
| `features.player-list` | `true` | Boolean | Enable the `/minebridge players` command. |
| `server.name` | `"Minecraft Server"` | String | Server name placeholder value used by `{server}` across templates. |
| `update-checker.enabled` | `true` | Boolean | Check Modrinth on startup and notify admins if an update is available. |

### 2. `messages.yml` (Chat & Event Templates)

| Template Key | Default | Supported Placeholders |
| :--- | :--- | :--- |
| `chat.minecraft-to-discord` | `"{prefix}**{player}**{suffix}: {message}"` | `{player}`, `{nickname}`, `{display_name}`, `{message}`, `{timestamp}`, `{server}`, `{prefix}`, `{suffix}`, `{world}`, `{world_alias}`, `{uuid}`, `%...%` (PlaceholderAPI) |
| `chat.discord-to-minecraft` | `&9[Discord] &f{player}: {message}` | `{player}`, `{display_name}`, `{username}`, `{nickname}`, `{channel}`, `{message}`, `{timestamp}`, `{server}`, `{prefix}`, `{suffix}` |
| `player.join` | `🟢 **{player}** joined the server.` | `{player}`, `{nickname}`, `{display_name}`, `{timestamp}`, `{server}`, `{prefix}`, `{suffix}`, `{world}`, `{world_alias}` |
| `player.leave` | `🔴 **{player}** left the server.` | `{player}`, `{nickname}`, `{display_name}`, `{timestamp}`, `{server}`, `{prefix}`, `{suffix}`, `{world}`, `{world_alias}` |
| `player.first-join` | `🌟 **{player}** joined the server for the first time!` | `{player}`, `{nickname}`, `{display_name}`, `{timestamp}`, `{server}`, `{prefix}`, `{suffix}`, `{world}`, `{world_alias}` |
| `player.server-status` | `&7Server &8\| &a{online}&7/&f{max} players online` | `{online}`, `{max}`, `{server}` |
| `player.player-list` | `&7Online players (&f{online}&7/&f{max}&7): {players}` | `{online}`, `{max}`, `{players}`, `{server}` |
| `deaths.<category>` | Standard death messages (e.g. `&c☠ {victim} was slain by {killer}`) | `{victim}`, `{killer}`, `{cause}`, `{category}`, `{server}`, `{prefix}` |
| `advancements.normal` | `&a✦ {player} has made the advancement [{title}]!` | `{player}`, `{title}`, `{description}`, `{category}`, `{challenge}`, `{server}`, `{prefix}` |
| `advancements.challenge` | `&6🏆 {player} has completed the challenge [{title}]!` | `{player}`, `{title}`, `{description}`, `{category}`, `{challenge}`, `{server}`, `{prefix}` |

### 3. `events.yml` (Notification Routing & Embeds)

- **Player Events (`player-events`)**:
  - `enabled`: Toggle player join/leave event system (`true`/`false`).
  - `channel-id`: Channel ID override for player join/leave embeds (leave blank to use primary channel).
  - `join` / `leave` / `first-join`: Individual toggles for each event type.
  - `embeds.enabled`: Use minimalistic Discord embeds for player events.
- **Deaths (`deaths`)**:
  - `enabled`: Master toggle for death notifications.
  - `minecraft.enabled` / `broadcast`: In-game death message formatting and broadcasting.
  - `discord.enabled`: Relay death embeds to Discord.
  - `discord.channel-id`: Channel override for death notifications (supports `${VAR}`).
  - `discord.embed` / `discord.title` / `discord.description` / `discord.color`: Embed styling (`#D32F2F`).
  - `categories.*`: Toggle individual categories (`player-kill`, `mob-kill`, `fall`, `lava`, `fire`, `drowning`, `void`, `explosion`, `environmental`, `other`).
- **Advancements (`advancements`)**:
  - `enabled`: Master toggle for advancement notifications.
  - `minecraft.enabled` / `discord.enabled`: Minecraft and Discord relay toggles.
  - `discord.channel-id`: Channel override for advancement embeds (supports `${VAR}`).
  - `discord.title` / `discord.description` / `discord.challenge-title` / `discord.challenge-description` / `discord.color`: Embed styling (`#2196F3`).
  - `challenges.enabled`: Independent toggle for challenge advancements.
  - `categories.*`: Filter by category (`story`, `nether`, `end`, `adventure`, `husbandry`, `other`).
- **Server Events (`server-events`)**:
  - `enabled`: Server lifecycle notification toggle (`false` by default).
  - `channel-id`: Channel override for server online/offline embeds.
  - `start` / `stop`: Toggle `SERVER ONLINE` and `SERVER OFFLINE` embeds.

### 4. `advanced.yml` (Advanced Settings & Formatting)

- **Cross-World Chat Routing (`chat-routing.cross-world`)**:
  - `mode`: `GLOBAL` (default), `PER_WORLD`, or `FILTERED`.
  - `enabled-worlds`: List of allowed worlds when `mode: FILTERED`.
- **Markdown Formatting Translation (`formatting`)**:
  - `minecraft-to-discord`: `format-conversion`, `allow-bold`, `allow-italic`, `allow-underline`, `allow-strikethrough`, `allow-obfuscated`.
  - `discord-to-minecraft`: `format-conversion`, `allow-bold`, `allow-italic`, `allow-underline`, `allow-strikethrough`, `allow-code`, `show-attachments`.
- **Webhook Customization (`webhook`)**:
  - `username`: Webhook author display name (default: `"{prefix}{player}"`).
  - `avatar-url`: Webhook author avatar URL (default: `"https://crafatar.com/avatars/{uuid}?overlay"`).
- **Hybrid Delivery (`hybrid`)**:
  - `event-delivery`: `WEBHOOK` (default) or `BOT`.
- **Embed Footers (`embeds.footer`)**:
  - `text`: Custom footer text.
  - `icon-url`: Custom footer icon URL.
- **Migration Tracking (`migration`)**:
  - `version`: Internal schema version (`1`). Automatically maintained.

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
- **Did MineBridge get disabled on startup?**: In v1.3.2, missing Discord credentials will **never** disable MineBridge. If `/mb` commands work, the plugin is active and waiting for your configuration.
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
