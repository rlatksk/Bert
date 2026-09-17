# Bert: Android Handheld Frontend Engine — Design Specification

**Date:** 2026-09-17  
**Status:** Approved for Implementation Planning  
**Target Platform:** Android Handhelds (Android 10+ / API 29+, tested up to Android 14/15, Landscape 16:9 / 4:3)  
**Primary Language:** Kotlin (Coroutines, StateFlow, Room, Serialization)  
**UI Decoupling:** Headless Engine API facade (`BertEngine`) ready for Jetpack Compose UI binding  

---

## 1. Executive Summary & Goals

**Bert** is a high-performance Android handheld frontend designed for retro gaming devices (e.g., Retroid Pocket, AYN Odin, ANBERNIC, AYANEO) and Android gaming handhelds. 

This specification defines the **Core Backend Engine**, designed to be completely decoupled from the UI. The engine runs persistently in a lightweight Android Foreground Service (`BertDaemonService`) to ensure that background tasks (Steam chat, Discord Rich Presence, and notification monitoring) stay alive without being killed by Android's Low Memory Killer (LMK) when demanding emulators (PS2, Switch, 3DS, GameCube) run in the foreground.

### Key Goals:
1. **ES-DE Compatibility:** Native adoption of the EmulationStation Desktop Edition (`ROMs/<system>/gamelist.xml`) standard for zero-friction library sharing across devices and SD cards.
2. **In-App Steam Client:** Full Steam presence, friend list synchronization, and real-time 2-way chat engine driven via WebSocket.
3. **Discord Rich Presence:** Real-time Discord activity updating with exact ROM title, console name, elapsed time, and box art via Discord Gateway WebSocket.
4. **Android System & Notification Center:** Background notification listening and dismissal via `NotificationListenerService`, combined with zero-permission Wi-Fi and Bluetooth quick-panel dialogs (`Settings.Panel`).
5. **Headless & Reactive API:** All engine state exposed via Kotlin `StateFlow` and suspend functions, ready for any modern Compose UI or HUD overlay.

---

## 2. Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Future UI Layer (Jetpack Compose)                    │
│      (Handheld Carousel, Game Grid, Slide-over HUD Drawer, Chat View)    │
└────────────────────────────────────▲────────────────────────────────────┘
                                     │ observes StateFlows / invokes suspend methods
┌────────────────────────────────────▼────────────────────────────────────┐
│                          BertEngine API Facade                          │
│         Exposes: libraryState, steamState, systemState, discordState    │
├─────────────────────────────────────────────────────────────────────────┤
│                           BertDaemonService                             │
│       (Android Foreground Service with low-priority ongoing notification)│
├───────────────────┬───────────────────┬─────────────────────────────────┤
│  Steam Subsystem  │ Discord Subsystem │     Game & Launcher Subsystem   │
│ - Auth & QR Login │ - Gateway Client  │ - ES-DE ROM & gamelist.xml Sync │
│ - Friend Presence │ - Activity Updates│ - ScreenScraper v2 Client       │
│ - 2-Way Chat WS   │ - Launch Observer │ - Emulator Intent Dispatcher    │
├───────────────────┴───────────────────┴─────────────────────────────────┤
│                   Android System & Notification Subsystem               │
│ - BertNotificationService (NotificationListenerService)                │
│ - ConnectivityManager.NetworkCallback & Wi-Fi Panel Launcher            │
│ - Battery & Power Telemetry Receiver                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                          Data & Storage Layer                           │
│ - Room Database (Cached metadata, offline messages, launch history)     │
│ - Encrypted DataStore (Steam tokens, Discord credentials, settings)     │
│ - Storage Access Framework (SAF) / Direct File Access (ROMs directory)  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Subsystem Specifications

### 3.1. Engine API Facade (`BertEngine`)

The `BertEngine` interface is the single boundary through which the future UI communicates with the backend.

```kotlin
interface BertEngine {
    // Reactive State Streams
    val libraryState: StateFlow<LibraryState>
    val steamState: StateFlow<SteamSessionState>
    val systemState: StateFlow<SystemState>
    val discordState: StateFlow<DiscordState>

    // Game & Library Commands
    suspend fun setRomsDirectory(uri: Uri)
    suspend fun scanLibraries(forceFullRescan: Boolean = false)
    suspend fun launchGame(gameId: String): Result<Unit>
    suspend fun scrapeGame(gameId: String): Result<GameMetadata>
    suspend fun toggleFavorite(gameId: String)

    // Steam Commands
    suspend fun startSteamQrLogin(): StateFlow<SteamQrLoginState>
    suspend fun sendSteamMessage(friendSteamId: Long, messageText: String): Result<Unit>
    suspend fun logoutSteam()

    // Discord Commands
    suspend fun setDiscordToken(token: String?)
    suspend fun setDiscordEnabled(enabled: Boolean)

    // Android System & Notification Commands
    fun openWifiPanel()
    fun openBluetoothPanel()
    fun clearNotification(notificationKey: String)
    fun clearAllNotifications()
    fun openNotificationListenerSettings()
}
```

---

### 3.2. Game Library, ES-DE Sync & Emulator Launcher

#### 3.2.1. File Hierarchy
Bert strictly honors the ES-DE directory specification:
```text
<ROMs_Root>/
├── snes/
│   ├── gamelist.xml
│   ├── Super Mario World (USA).sfc
│   └── media/
│       ├── covers/
│       └── screenshots/
├── gba/
│   ├── gamelist.xml
│   └── Pokemon Emerald (USA).gba
├── psx/
│   ├── gamelist.xml
│   └── Metal Gear Solid (USA) (Disc 1).chd
└── android/
    └── gamelist.xml (Synthesized for installed native Android games)
```

#### 3.2.2. Fast Streaming XML Engine (`GamelistXmlParser` & `GamelistXmlSerializer`)
To support thousands of games with sub-100ms startup times, parsing uses Android’s native `XmlPullParser` rather than memory-heavy DOM parsers.

- **Parsed fields:**
  - `<path>`: Relative path to the ROM file.
  - `<name>`: Display title.
  - `<desc>`: Synopsis / description.
  - `<image>`: Relative path to box art thumbnail.
  - `<rating>`: Decimal rating (0.0 to 1.0).
  - `<releasedate>`: Release timestamp (YYYYMMDDTHHMMSS).
  - `<developer>`, `<publisher>`, `<genre>`, `<players>`.
  - `<playcount>`: Integer launch count.
  - `<lastplayed>`: Timestamp of last session.
  - `<favorite>`: Boolean flag.

- **Serialization:** When a game is played (updating `playcount` and `lastplayed`) or scraped, updates are buffered in memory and flushed atomically to `gamelist.xml.tmp` before renaming to `gamelist.xml` to prevent corruption during unexpected shutdowns.

#### 3.2.3. Emulator Registry & Intent Dispatcher (`EmulatorLauncher`)
Bert maps internal system identifiers (`snes`, `gba`, `psx`, `n64`, etc.) to Android package and component intents:

1. **RetroArch Core Dispatch:**
   - Package: `com.retroarch.aarch64` (or `com.retroarch`)
   - Intent Action: `android.intent.action.MAIN`
   - Extra parameters:
     - `CONFIGFILE`: Path to custom retroarch config.
     - `LIBRETRO`: Absolute path to the core `.so` file.
     - `ROM`: File path or SAF Content URI.
2. **Standalone Emulators:**
   - **PPSSPP:** `org.ppsspp.ppsspp` with ROM URI.
   - **Dolphin:** `org.dolphinemu.dolphinemu` (`AutoStartActivity`) with `AutoStartActivity.EXTRA_PLAY_GAME` extra.
   - **DuckStation:** `com.github.stenzek.duckstation` with `bootPath` extra.
   - **NetherSX2 / AetherSX2:** `xyz.aethersx2.android` with `bootPath` extra.
3. **Security & Permissions:**
   - Automatic `Intent.FLAG_GRANT_READ_URI_PERMISSION` on content URIs.
4. **Lifecycle & Session Tracking:**
   - Records session start time upon launching the intent.
   - Listens to launcher activity resume (`ON_RESUME`) to calculate played duration and increment `playcount`.
   - Fires hooks to the Discord Rich Presence manager.

---

### 3.3. Steam Client Subsystem

The Steam Subsystem enables an in-app social experience without relying on the desktop Steam client.

```
┌────────────────────────────────────────────────────────┐
│                   SteamClientEngine                    │
├───────────────────────────┬────────────────────────────┤
│     SteamAuthenticator    │       SteamCmClient        │
│  - QR Code Challenge Gen  │  - CM Server List resolver │
│  - Polling for Approval   │  - WebSocket Connection    │
│  - Token Refresh loop     │  - Heartbeat & Ping (30s)  │
├───────────────────────────┼────────────────────────────┤
│     SteamFriendsService   │       SteamChatService     │
│  - PersonaState updates   │  - FriendMessages proto    │
│  - In-game title tracking │  - Send & Recv pipeline    │
│  - Avatar URL resolution  │  - Optimistic message sync │
└───────────────────────────┴────────────────────────────┘
```

#### 3.3.1. Authentication Flow (QR Code & Steam Guard)
1. Requests an authentication challenge session from Steam's `IAuthenticationService/GetAuthSessionViaQR`.
2. Emits a challenge URL (`https://s.team/q/1/...`) to `SteamQrLoginState.DisplayQr` which the UI renders as a QR code.
3. Polls `IAuthenticationService/PollAuthSessionStatus` until the user confirms the login on their Steam Mobile app.
4. Retrieves `access_token` and `refresh_token` and saves them in Android `EncryptedSharedPreferences`.

#### 3.3.2. Connection & Presence Protocol
- Connects to Steam CM (Connection Manager) via WebSocket (`wss://.../cmsocket/`).
- Emits logon message using the OAuth access token.
- Receives friend list (`CMsgClientFriendsList`) and friend state changes (`CMsgClientPersonaState`).
- Updates `steamState: StateFlow<SteamSessionState>` containing:
  ```kotlin
  data class SteamFriend(
      val steamId: Long,
      val personaName: String,
      val personaState: PersonaState, // Online, InGame, Busy, Away, Snooze, Offline
      val gameTitle: String?,        // e.g. "Elden Ring"
      val gameAppId: Int?,
      val avatarUrl: String,
      val unreadMessageCount: Int
  )
  ```

#### 3.3.3. 2-Way Real-Time Chat Protocol
- Listens to incoming chat protobuf frames (`CMsgClientFriendMsgIncoming`).
- Dispatches outgoing messages via `CMsgClientFriendMsg` with an incremental sequence ID.
- Stores messages locally in Room database (`ChatMessageEntity`) to provide instant offline and pre-render history.

---

### 3.4. Discord Rich Presence Subsystem

Provides deep Discord status updates when playing games on the handheld:

1. **Connection:** Maintains a lightweight WebSocket connection to `wss://gateway.discord.gg/?v=10&encoding=json`.
2. **Identification & Gateway Handshake:** Sends `Opcode 2 (Identify)` with the user token and client properties.
3. **Heartbeat Loop:** Sends `Opcode 1 (Heartbeat)` based on the gateway's `heartbeat_interval` (typically every 41.25s).
4. **Status Dispatch (`Opcode 3: Presence Update`):**
   When `launchGame()` succeeds:
   ```json
   {
     "op": 3,
     "d": {
       "since": null,
       "activities": [
         {
           "name": "Super Mario World",
           "type": 0,
           "details": "Super Nintendo",
           "state": "Playing ROM",
           "timestamps": {
             "start": 1773740400000
           },
           "assets": {
             "large_image": "snes_logo",
             "large_text": "Super Nintendo Entertainment System"
           }
         }
       ],
       "status": "online",
       "afk": false
     }
   }
   ```
5. **Clear on Exit:** When user returns to the launcher, sends an empty activities array to revert status.

---

### 3.5. Android System & Notification Subsystem

#### 3.5.1. Notification Listener (`BertNotificationService`)
- Extends `android.service.notification.NotificationListenerService`.
- **Capture:** Converts `StatusBarNotification` into clean domain models:
  ```kotlin
  data class AppNotification(
      val key: String,
      val packageName: String,
      val appName: String,
      val title: String,
      val text: String,
      val postTime: Long,
      val isClearable: Boolean,
      val iconBitmap: Bitmap?
  )
  ```
- **Actions:**
  - `clearNotification(key: String)` -> calls `cancelNotification(key)`.
  - `clearAllNotifications()` -> calls `cancelAllNotifications()`.
  - Content intent trigger: Invokes `pendingIntent.send()` when user clicks on a notification.

#### 3.5.2. Quick Settings & Network Telemetry
- **Wi-Fi Toggle Dialog:** Triggers `Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` to summon Android's native floating quick-panel over the launcher.
- **Bluetooth Toggle Dialog:** Triggers `Intent(Settings.Panel.ACTION_BLUETOOTH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)`.
- **Connectivity State:** Registers `ConnectivityManager.NetworkCallback` to detect Wi-Fi connection, SSID, and cellular/ethernet changes.
- **Battery Monitor:** Listens to sticky `Intent.ACTION_BATTERY_CHANGED` to deliver real-time battery percentage and AC charging state.

---

## 4. Data Layer & Persistence

### 4.1. Room Database Schema

```kotlin
@Entity(tableName = "games", primaryKeys = ["systemId", "romPath"])
data class GameEntity(
    val systemId: String,
    val romPath: String,
    val name: String,
    val description: String?,
    val imagePath: String?,
    val rating: Float?,
    val releaseDate: String?,
    val developer: String?,
    val publisher: String?,
    val genre: String?,
    val players: String?,
    val playCount: Int,
    val lastPlayed: Long,
    val isFavorite: Boolean
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friendSteamId: Long,
    val senderSteamId: Long,
    val messageText: String,
    val timestamp: Long,
    val isIncoming: Boolean
)
```

---

## 5. Error Handling & Edge Cases

| Failure Scenario | Mitigation Strategy |
|---|---|
| Emulator triggers Low Memory Killer (LMK) | `BertDaemonService` runs as a high-priority foreground service with an ongoing notification, keeping Steam/Discord sockets alive. |
| Network disconnects during gameplay | Steam and Discord clients implement exponential backoff reconnection (3s, 6s, 12s, max 60s) with jitter. |
| `gamelist.xml` write interruption (power off) | Atomic write via temporary file (`gamelist.xml.tmp` -> `gamelist.xml`) ensures zero XML corruption. |
| Corrupt or non-standard `gamelist.xml` | Streaming `XmlPullParser` catches malformed XML tags, skips the offending entry, and recovers parsing the rest of the list. |
| Missing emulator on target device | Launcher checks `packageManager.getLaunchIntentForPackage()` before launch; if missing, returns `Result.failure(EmulatorNotFoundException)` so the UI can inform the user. |
| Missing Notification Listener Permission | `hasNotificationAccess()` checks `NotificationManagerCompat.getEnabledListenerPackages()`; helper invokes `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. |

---

## 6. Verification & Testing Strategy

1. **XML Parser & Serializer Unit Tests:**
   - Test streaming parsing of standard ES-DE `gamelist.xml` files with edge-case characters (`&`, `<`, quotes).
   - Test atomic serialization and verify output matches input structure.
2. **Steam Protocol Mock Tests:**
   - Test QR code challenge parsing and polling state transitions using mock HTTP/WebSocket endpoints.
   - Test incoming friend message packet decoding and Room database insertion.
3. **Intent Dispatcher Unit Tests:**
   - Verify RetroArch, PPSSPP, and Dolphin intent creation with correct flags (`FLAG_GRANT_READ_URI_PERMISSION`) and extras.
4. **System Bridge Integration Tests:**
   - Verify `NotificationListenerService` event mapping into domain `AppNotification`.
   - Verify network callback state transitions.
