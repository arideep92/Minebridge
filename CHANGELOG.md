# Changelog

All notable changes to MineBridge are documented here. Versions use semantic
versioning: breaking configuration or command changes require a major release;
backward-compatible features use a minor release; compatible fixes use a patch
release.

## [1.4.0] - 2026-11-22

### Added

- **Modular Configuration Architecture Redesign**:
  - **Clean 4-File Separation**:
    - `config.yml`: Minimal, beginner-friendly core setup (mode, bot token, channel ID, webhook URL, core feature toggles, server name, update-checker).
    - `messages.yml`: All user-facing chat relay, player join/leave, server status, player list, death, and advancement notification templates with full placeholder documentation.
    - `events.yml`: Fine-grained event notification routing, category toggles, embed titles, descriptions, hex colors, and per-module channel overrides.
    - `advanced.yml`: Cross-world chat routing (`chat-routing.cross-world`), Markdown formatting translation toggles, webhook identity, hybrid event delivery transport, embed footers, and migration version tracking.
  - **Environment Variable Resolution**:
    - Secure inline resolution using `${VAR}` and `${VAR:default}` syntax for sensitive credentials and channel IDs (`discord.bot-token`, `discord.channel-id`, `discord.webhook.url`, and channel overrides in `events.yml`).
    - Values are held in memory only and never written back to disk or leaked in logs or `/mb status`.
  - **Automated Legacy Migration & Safe Backups**:
    - Automatic detection of legacy single-file configurations (`plugins/MineBridge/config.yml`).
    - Creates timestamped, idempotent backups in `plugins/MineBridge/backups/config-legacy-YYYYMMDD-HHmmss.yml` prior to modifying any files.
    - Maps all 108 existing legacy configuration keys to the new modular structure with zero data loss.
    - Preserves custom values and unrecognized keys in the backup.
    - Completely idempotent: once migrated, subsequent restarts leave files untouched.
  - **Atomic Reload Lifecycle**:
    - In-place configuration reload via `/mb reload` loads and validates configuration candidates in memory before committing.
    - If validation fails, existing settings and snapshot remain active with zero interruption to ongoing chat relay or event listeners.
- **DiscordSRV-Inspired Chat Integration & Webhook Redesign**:
  - **Bidirectional Markdown Formatting Translation**:
    - **Minecraft → Discord (`MinecraftToDiscordFormatter`)**: Translates Minecraft formatting codes (`&` and `§`, including hex colors `§x...`, `&#...`, `{#...}`) into standard Discord Markdown: Bold (`**`), Italic (`*`), Underline (`__`), Strikethrough (`~~`), and Obfuscated (`||` spoiler). Closes active formatting styles in reverse order upon color or reset codes. Escapes user-supplied Markdown characters to prevent user chat breaking Discord styling, and defuses mass mentions (`@everyone`, `@here`).
    - **Discord → Minecraft (`DiscordToMinecraftFormatter`)**: Translates Discord Markdown (`**`, `*`, `_`, `__`, `~~`, `` ` ``, ```` ``` ````, `||`, URLs) into corresponding Minecraft formatting codes (`§l`, `§o`, `§n`, `§m`, `§7`). Strips raw `§` characters to prevent malicious color injection, admin rank spoofing, and format exploitation.
    - **Granular Formatting Toggles**: Independent toggles under `chat.minecraft-to-discord` and `chat.discord-to-minecraft` (`format-conversion`, `allow-bold`, `allow-italic`, `allow-underline`, `allow-strikethrough`, `allow-obfuscated`, `allow-code`, `show-attachments`).
  - **Skin Avatar Resolution Service (`SkinAvatarService`)**:
    - Asynchronous, non-blocking player skin head avatar resolution supporting UUIDs (`{uuid}`) and player names (`{player}`).
    - Bounded in-memory TTL cache (`ConcurrentHashMap`, default TTL 60 minutes, max 2000 entries) preventing repeated string allocations and latency.
    - Automatic cache invalidation on player quit and `/mb reload`.
    - Predictable fallback to Steve avatar for offline or missing skins.
  - **Long Webhook Message Splitting (`WebhookMessageSplitter`)**:
    - Automatically splits outbound Discord webhook messages exceeding the 2000-character limit into valid segments (`<= 2000` chars).
    - Natural boundary detection: searches for newlines (`\n`) and whitespace (` `) within the split window.
    - UTF-16 surrogate pair protection: ensures emoji characters are never split across segments.
    - Style preservation across segment boundaries: automatically closes open formatting tags at the end of each segment and reopens them at the beginning of the next.
  - **Webhook Identity & Payload Sanitization**:
    - Webhook username sanitization: enforces 1–80 characters, strips mass mentions (`@everyone`, `@here`), and replaces disallowed Discord system names (such as "clyde").
    - Webhook avatar URL validation: ensures HTTP/HTTPS protocol, enforces 2048-character limit, and filters out malformed URLs.
  - **Discord Attachments & Inbound Chat Polish**:
    - When Discord users post attachments (images, files), MineBridge appends clean readable indicators (e.g. `[image.png]`) to the relayed Minecraft chat message.
    - Expanded template placeholders: `{uuid}` for Minecraft messages/webhooks, `{channel}` and `{nickname}` for Discord messages.
- **HYBRID Mode Integration (`discord.mode: HYBRID`)**:
  - Introduced a third supported integration mode that pairs high-fidelity webhook outbound delivery with real-time gateway bot inbound reception.
  - **Directional Chat Transport**:
    - **Minecraft → Discord**: Routed exclusively via Webhook with dynamic player avatar renders (`https://mc-heads.net/avatar/{player}/100`) and LuckPerms/Vault prefix tags. The Bot never sends outbound Minecraft chat, preventing duplicate messages.
    - **Discord → Minecraft**: Inbound Discord chat is received via JDA `MessageReceivedEvent` in the configured channel. Webhook messages and Bot messages are filtered out, preventing feedback loops.
  - **Configurable Event Notification Transport**:
    - Game event embeds (joins, leaves, deaths, advancements, and server lifecycle) are routed through **exactly one** configured path via `discord.hybrid.event-delivery` (`WEBHOOK` default, or `BOT`).
    - Eliminates duplicate notifications across transports.
  - **Independent Failure Isolation**:
    - Webhook outbound delivery and Bot gateway inbound relay operate as decoupled components.
    - If the Discord bot gateway temporarily disconnects or reconnects, outbound player chat and notifications continue uninterrupted.
    - If the Discord webhook encounters HTTP rate limiting or errors, inbound Discord chat continues uninterrupted.
    - Zero automatic transport failover that risks duplicate messages.
  - **Full Reload State Machine**:
    - Seamless live reload transitions between all 9 mode combinations (`BOT <-> HYBRID`, `WEBHOOK <-> HYBRID`, `HYBRID <-> HYBRID`) via `/mb reload`.
    - Correct startup, connection pooling, and resource shutdown on reload and server stop.
  - **Dual-State Status Reporting**:
    - `/mb status` in HYBRID mode formats both Discord Bot gateway state / channel availability and Webhook worker status / destination validity.

## [1.3.3] - 2026-11-22

### Added

- **Server Lifecycle Notifications**:
  - Added compact, dedicated Discord embeds for server lifecycle events: `SERVER ONLINE` (subtle emerald green) and `SERVER OFFLINE` (subtle soft red).
  - Added independent configuration controls under `discord-events.server-events`:
    - `enabled`: master toggle (defaults to `false` for 100% backward compatibility with existing servers).
    - `channel-id`: optional custom channel override for server lifecycle events.
    - `start`: independent toggle for `SERVER ONLINE` embeds on server startup.
    - `stop`: independent toggle for `SERVER OFFLINE` embeds on server shutdown.
- **Player Head Avatars in Embeds & Webhooks**:
  - Player head avatars (`https://mc-heads.net/avatar/{player}/64`) integrated into author icons across all player, death, and advancement embeds.
  - Webhook chat messages now default to the player's skin head render (`https://mc-heads.net/avatar/{player}/100`), while maintaining full support for custom avatar URL overrides.
  - Robust sanitization of player names for avatar URL construction with graceful fallback to `"Steve"` for missing or invalid characters.

### Changed

- **Minimalistic Notification Redesign**:
  - Completely redesigned all Discord event notifications into compact, clean, single-card embeds with zero redundant fields (~60–70% vertical height reduction).
  - **Player Join**: Compact embed with short title `JOIN`, player head avatar, subtle green sidebar (`#43B581`), timestamp, and 0 redundant fields.
  - **Player Leave**: Compact embed with short title `LEAVE`, player head avatar, subtle red sidebar (`#EF5350`), timestamp, and 0 redundant fields.
  - **First-Time Join**: Compact embed with title `FIRST JOIN`, gold sidebar (`#FAA61A`), player head avatar, short welcome message, and timestamp.
  - **Death Notifications**: Compact embed with short title `DEATH`, player head avatar, clear death message in description, and conditional detail fields (displays `Killer` or `Cause` only when relevant and available; eliminates redundant `Victim` fields).
  - **Advancement & Achievement Notifications**: Ultra-compact embeds with title `ADVANCEMENT` or `CHALLENGE`, player head avatar, advancement title in description, zero redundant fields.
  - **Subtle Category Colors**: Applied refined, subtle color palettes for advancement categories: Story (`#3498DB`), Nether (`#D35400`), End (`#9B59B6`), Adventure (`#1ABC9C`), Husbandry (`#2ECC71`), and Challenges (`#F1C40F`).
  - **Chat Formatting**: Preserved plain-text chat formatting for both Minecraft → Discord and Discord → Minecraft with clean token spacing and strict markdown escaping.

## [1.3.2] - 2026-11-22

### Changed

- **Codebase Optimization & Architecture Consolidation**:
  - Unified sliding-window event and announcement deduplication under `SlidingWindowDeduplicator<K>`, eliminating duplicate monotonic time tracking logic across Discord event, advancement, and chat deduplicators while preserving exact public APIs.
  - Centralized `{placeholder}` token scanning and replacement into `RelayMessageFormatter.replacePlaceholders(...)` across `ServerInfoFormatter`, `DeathMessageFormatter`, and `AdvancementMessageFormatter`.
  - Extracted shared public chat classification checks in `ChatClassifier.isPublicChatMessage(...)` between Spigot and Paper modern chat pathways.
  - Streamlined optional dependency reflection in `HookUtil` (`loadClass` and `findMethod`), reducing repetitive reflection boilerplate across `PlaceholderApiHook`, `MultiverseHook`, `EssentialsHook`, and `LuckPermsHook`.
  - Consolidated diagnostic and failure formatting between `DiscordService` and `WebhookService` via `DiscordDiagnosticFormatter`.
  - Centralized hex color reading, channel ID parsing, and subsection validation in `ConfigManager`.
  - Eliminated dead methods `validateOptionalChannelId` and `readString` in `ConfigManager`.
  - Optimized webhook username placeholder replacement from 8 sequential string replace allocations to a single-pass scan.

### Fixed

- **Dependency Isolation & Relocation**: Fully relocated bundled dependencies including JDA, Jackson, OkHttp3, Okio, Trove4j, Commons Collections 4, Neovisionaries, Kotlin, and JetBrains/IntelliJ annotations under `com.minebridge.libs.*`, preventing classpath collisions with other Minecraft plugins.
- **Death Embed Customization**: Corrected `DiscordDeathNotifier` to honor configured `settings.embedTitle()`.
- **Resource Lifecycle & Concurrency Hardening**:
  - Cleaned up `UpdateChecker` OkHttpClient dispatcher and connection pool on plugin shutdown.
  - Dynamically synchronized `update-checker.enabled` upon `/mb reload`.
  - Hardened lifecycle mode detection and ensured mutual termination of previous service instances during mode switches.
  - Eliminated unsafe off-thread Bukkit entity lookups during server shutdown.
  - Protected unowned executors from termination in `UpdateChecker.shutdown()`.

## [1.3.1] - 2026-11-21

### Added

- **Optional Plugin Compatibility Integrations**: Added modular runtime adapters for popular Bukkit, Spigot, and Paper plugins while maintaining 100% standalone plugin independence without hard compile dependencies or third-party library shading:
  - **LuckPerms**: Extended native API hook to resolve effective suffixes (`{suffix}`) in addition to prefixes (`{prefix}`) using context-aware query options (`ContextManager.getQueryOptions(Player)`).
  - **Vault**: Implemented `VaultHook` to resolve `net.milkbowl.vault.chat.Chat` and `net.milkbowl.vault.permission.Permission` providers independently via Bukkit `ServicesManager` as a compatibility fallback when LuckPerms is absent, avoiding duplicate prefixes when both integrations are present.
  - **EssentialsX**: Implemented `EssentialsHook` to resolve player nicknames (`{nickname}`, `{display_name}`), safely stripping tildes (`~`) and color codes with fallback to Bukkit display name and real username. Private whispers (`/msg`, `/tell`, `/w`), mutes, and commands are strictly kept out of Discord.
  - **Multiverse-Core**: Implemented `MultiverseHook` to resolve custom world aliases (`{world_alias}`) and world names (`{world}`), with graceful fallback to Bukkit world names.
  - **PlaceholderAPI**: Implemented `PlaceholderApiHook` allowing arbitrary `%...%` placeholders in configured message templates and webhook identities with fast-path skips when `%` is absent.
  - **ChatMetadataService**: Created unified metadata facade orchestrating all 5 optional integrations with deterministic precedence (LuckPerms > Vault, EssentialsX > DisplayName > RealName, Multiverse > WorldName).
- **Cross-World Chat Scoping**: Added configurable cross-world chat modes via `chat.cross-world.mode` (`GLOBAL`, `PER_WORLD`, `FILTERED`) and `chat.cross-world.enabled-worlds`, allowing servers to isolate chat by world while preserving strict privacy filtering against restricted channels.
- **Strict Identity & Placeholder Security**:
  - Pinned `{player}` strictly to the real account username (`player.getName()`), preventing nickname spoofing in Discord logs and moderation.
  - Restricted PlaceholderAPI parsing strictly to trusted administrator-configured templates before message interpolation, making it impossible for players to trigger placeholder injection or leak sensitive server variables.
- **Strict Global-Chat-Only Synchronization**: Implemented `ChatClassifier` to guarantee that only genuine, public Minecraft global player chat messages are synchronized to Discord.
- **Paper Modern Asynchronous Chat Support**: Implemented `PaperChatBridge` to dynamically bind to Paper's modern `io.papermc.paper.event.player.AsyncChatEvent` with audience viewer inspection and Adventure component plain text extraction, preserving complete binary compatibility with Spigot servers.
- **Chat Deduplication**: Implemented `ChatDeduplicator` using a sliding window to prevent duplicate message relay across dual-listener setups, server reload cycles, and Paper's simultaneous modern and legacy chat event dispatches.
- **Comprehensive Integration Test Suite**: Added extensive test suites (`VaultHookTest`, `EssentialsHookTest`, `MultiverseHookTest`, `PlaceholderApiHookTest`, `ChatMetadataServiceTest`, `CrossWorldChatTest`, `ChatClassifierTest`, `ChatDeduplicatorTest`, and updated `ChatListenerTest`, `RelayMessageFormatterTest`, `LuckPermsHookTest`) covering all individual and combined integrations, fallbacks, cross-world modes, and security invariants.
- **Clean Prefix and Suffix Message Placement**: Formatted `{prefix}` before usernames and `{suffix}` after usernames cleanly in Discord and Webhooks, avoiding double spaces and empty placeholders when absent.
- **Startup Configuration Regression Tests**: Added `StartupConfigurationHandlingRegressionTest` covering first startup without `config.yml`, empty bot tokens, rejected tokens, reload recovery, duplicate connection prevention, absent optional dependencies, and first startup in webhook mode.

### Improved

- **Discord & Webhook Lifecycle Handling**: Centralized connection state management with clear status tracking (`INVALID_CONFIGURATION`, `FAILED`, `CONNECTING`, `CONNECTED`, `READY`, `STOPPED`). Successive reloads terminate prior JDA clients and worker threads without leaking duplicate sessions or listeners.
- **Recovery Through `/mb reload`**: Enabled live recovery from incomplete initial configuration: administrators can populate tokens or webhook URLs post-startup and run `/mb reload` to transition smoothly to active Discord connectivity and activate background schedulers without restarting the Minecraft server.
- **Optional Integration Error Isolation**: Standardized exception containment across all optional hooks (`LuckPermsHook`, `VaultHook`, `EssentialsHook`, `MultiverseHook`, `PlaceholderApiHook`) via `HookUtil`, preventing any third-party integration failure from crashing or disabling MineBridge while strictly propagating fatal JVM errors (`VirtualMachineError`, `ThreadDeath`).
- **Private & Restricted Channel Filtering**: Filtered out private messages (`/msg`, `/tell`, `/whisper`, `/w`, `/r`, `/pm`, `/dm`, `-> player`, `[PM] to ...`), faction chat, team chat, party chat, guild chat, staff chat, and local/proximity chat using audience recipient subset analysis, channel tag patterns, channel shortcuts, and player channel metadata.
- **Fail-Closed Classification**: Chat events with ambiguous recipients, missing senders, or unverified audiences fail closed and are never synchronized.
- **Cancelled Event Enforcement**: `ChatListener` registered at `EventPriority.MONITOR` with `ignoreCancelled = true`, guaranteeing cancelled chat events are never relayed to Discord.
- **Plugin & System Message Filtering**: Excluded Citizens NPCs (`NPC` metadata), offline/null senders, `CONSOLE`/`Server` names, empty recipient lists, and command executions.
- **Prefix Sanitization**: Stripped Minecraft section sign (`§`) and ampersand (`&`) color codes from LuckPerms prefixes before markdown escaping, preventing raw formatting codes in Discord.

### Fixed

- **First-Startup Configuration Handling**: Automatically generate default `config.yml` on first startup if missing; gracefully fall back to bundled defaults without throwing `FileNotFoundException` or failing startup.
- **Plugin Disabling on Incomplete Discord Configuration**: Eliminated premature `disablePlugin(this)` call when `discord.bot-token` or channel ID is empty or unconfigured. MineBridge now stays fully enabled, retaining Minecraft-side functionality, event handling, and administrative commands (`/mb reload`, `/mb status`) while reporting `INVALID_CONFIGURATION`.
- **LuckPerms Prefix Synchronization**: Fixed missing player prefix resolution where `ChatListener` previously passed UUID instead of `Player` to `LuckPermsHook`, preventing context-aware prefix retrieval.
- **Discord Message Spacing**: Fixed leading and extra spaces when players have no LuckPerms prefix or when prefixes lack trailing whitespace.
- **Candidate Settings Preservation**: Populate `Candidate.settings()` during candidate creation even when configuration errors are detected, preserving mode and settings while safely maintaining `candidate.valid() == false`.

### Compatibility

- Full backward compatibility: Preserves Spigot `AsyncPlayerChatEvent` handling while adding runtime detection and dynamic binding for modern Paper `AsyncChatEvent`.
- Soft-dependencies on LuckPerms, Vault, EssentialsX, Multiverse-Core, and PlaceholderAPI: Operates strictly as optional soft dependencies (`softdepend` in `plugin.yml`). When any or all integrations are absent, disabled, or unconfigured, MineBridge functions completely standalone with zero errors, zero class-loading issues, and safe fallbacks.

## [1.3.0] - 2026-11-20

### Added

- Dual integration modes: `BOT` (default) and `WEBHOOK`, configurable via `discord.mode`.
- Modrinth Update Checker (`UpdateChecker`): Asynchronously checks for published, listed MineBridge releases on Modrinth once during startup, notifying console and online administrators (`minebridge.admin`) with semantic version comparison and clean shutdown lifecycle management.
- Outgoing Minecraft-to-Discord Webhook support (`WebhookService`) with asynchronous HTTP delivery via OkHttp, supporting text chat, player joins/leaves, deaths, and advancements.
- Custom Discord webhook username and avatar URL formatting with `{player}` and `{server}` placeholders.
- Discord embed footer customization (`discord.embed.footer.text` and `icon-url`) with `{server}` placeholder expansion.
- Optional LuckPerms soft-dependency hook (`LuckPermsHook`) resolving cached group prefixes (`{prefix}`) in message templates safely and non-blockingly.
- Mode-aware status reporting in `/minebridge status`, displaying active integration mode, destination health, queue metrics, dropped counts, and LuckPerms availability.

### Improved

- Centralized integration lifecycle management with `LifecycleManager`, ensuring mutually exclusive startup for BOT and WEBHOOK modes.
- Dynamic zero-event-leak configuration reload handling: seamlessly transitions between BOT and WEBHOOK modes without re-registering Bukkit listeners.
- Resilient Discord HTTP 429 rate-limiting with exponential backoff and JSON `retry_after` / HTTP `Retry-After` header parsing.
- Single-pass non-recursive placeholder expansion in `RelayMessageFormatter` preventing placeholder injection.
- Secret masking: Webhook URLs and Discord bot tokens are strictly scrubbed and never exposed in logs or command outputs.

### Compatibility

- Full backward compatibility: `BOT` remains the default mode, and existing `discord.bot-token` / `channel-id` configurations require zero changes.
- Soft-dependency on LuckPerms: graceful fallback to empty prefix when LuckPerms is absent, disabled, or unconfigured.

## [1.2.1] - 2026-11-18

### Added

- Track rejected outgoing Discord messages with an `AtomicLong` counter
  (`DiscordService.getOutgoingDropped()`) that increments when the outbound
  queue is full or closed, and expose it as `outgoingDropped` in
  `MineBridgeStatus` and `/minebridge status` output.

### Changed

- Set the project and plugin metadata version to 1.2.1.
- Optimize shaded JAR packaging (reducing the distributable JAR from 19.24 MB
  to ~8.78 MB) by excluding JDA's unused Discord voice/audio transitive
  dependencies (`club.minnced:opus-java`, `net.java.dev.jna:jna`,
  `com.google.crypto.tink:tink`, `com.google.protobuf:protobuf-java`,
  `com.google.code.gson:gson`, `com.google.code.findbugs:jsr305`, and
  `com.google.errorprone:error_prone_annotations`), stripping non-runtime
  Maven/Proguard/Kotlin compiler metadata, excluding verified-unused Trove4j
  `Byte`/`Short`/`Char`/`Float`/`Double`, decorator, and unmodifiable classes,
  and enabling Shadow `mergeServiceFiles()`. Discord voice/audio functionality
  is intentionally excluded and unsupported.
- Improve outgoing Discord send failure diagnostics to include the exception
  type and safe structured context (Discord API error code, HTTP status,
  missing permission, retry delay, or cause type) via rate-limited warnings
  without logging bot tokens or message content.
- Extend `DiscordGatewayListener` and `DiscordService` with null-safe
  diagnostic callbacks for JDA 5.6.1 gateway events (`onSessionDisconnect`
  close codes, `onSessionInvalidate`, `onSessionRecreate`, `onShutdown`, and
  `onException`) without duplicating JDA's built-in reconnection handling.

### Fixed

- Enforce Discord Snowflake validation (`ConfigManager.isValidSnowflake`) for
  channel IDs, accepting 17-20 decimal digits within the unsigned 64-bit
  integer range (`<= 18446744073709551615`), and delegate
  `ConfigManager.isValidChannelId` to it for backward compatibility.
- Make `discord.channel-id` optional when all Discord-dependent features are
  disabled in candidate configuration validation while still rejecting
  non-blank malformed channel IDs and avoiding blank channel lookups in
  `DiscordService`.

### Testing

- Add unit tests for Discord Snowflake and channel ID validation covering
  17-20 digit IDs, the maximum unsigned 64-bit value, out-of-range 20-digit
  values, null/blank/non-numeric inputs, optional channel IDs when Discord
  features are disabled, and candidate configuration validation.
- Add unit tests for `outgoingDropped` queue overflow and queue-closed
  counting, `MineBridgeStatus` `long` formatting, outgoing send exception
  diagnostics without secret leakage, and null-safe `DiscordGatewayListener`
  gateway event diagnostics.
- Verified static bytecode integrity and JAR contents of the optimized
  distribution on Java 21 without enabling Shadow `minimize()`.

## [1.2.0] - 2026-11-10

### Added

- Configurable Minecraft death messages.
- Discord death notification embeds.
- Minecraft advancement announcements.
- Discord achievement embeds.
- Configurable announcement templates and channels.
- Advancement category classification and challenge-specific announcements.
- Join, leave, and first-join Discord event embeds.
- A shared Discord event embed factory/notifier with timestamps, standard
  footer, and disabled allowed mentions.

### Changed

- Extend the bundled configuration and README with Death Messages and
  Achievement Announcements setup and compatibility details.
- Route player, advancement, and death notifications through the shared
  embed-only delivery path.
- Set the project and plugin metadata version to 1.2.0.

### Fixed

- Suppress the vanilla death line when MineBridge broadcasts its replacement,
  preventing duplicate Minecraft announcements.
- Prevent duplicate advancement announcements within the deduplication window.
- Prevent duplicate join, leave, and death notifications during the
  deduplication window.

### Removed

- No existing features removed.

### Security

- Escape user-controlled Discord Markdown and disable parsed mentions in both
  death and advancement notifications.

### Testing

- Add automated coverage for death classification, advancement category and
  challenge detection, duplicate prevention, message/embed formatting,
  join/leave/first-join embeds, mention suppression, delivery failures,
  notification policy, and configuration validation.

## 1.0.1 - 2026-11-02

### Added

- Add continuous integration for Java 21 builds and tests.
- Add regression and concurrency tests for outbound delivery, inbound
  processing, reloads, and blank Minecraft chat.

### Changed

- Preserve immutable configuration snapshots across Discord client reloads.
- Process Discord-to-Minecraft messages through a bounded FIFO queue.
- Drop new inbound messages when the queue is full and emit rate-limited
  diagnostics for queue overflow.

### Fixed

- Track ownership of in-flight Discord sends so client replacement cannot let
  an older send callback release a newer send's gate.
- Avoid scheduling or processing inbound Discord chat after shutdown begins;
  pending inbound messages are discarded during shutdown.
- Ignore empty and whitespace-only Minecraft chat messages when relaying to
  Discord.

### Security

- Limit inbound Discord chat per user and globally to reduce the impact of
  message floods.
