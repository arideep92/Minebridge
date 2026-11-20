# Changelog

All notable changes to MineBridge are documented here. Versions use semantic
versioning: breaking configuration or command changes require a major release;
backward-compatible features use a minor release; compatible fixes use a patch
release.

## [Unreleased]

### Added

- No unreleased changes.

## [1.3.0] - 2026-11-20

### Added

- Dual integration modes: `BOT` (default) and `WEBHOOK`, configurable via `discord.mode`.
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
