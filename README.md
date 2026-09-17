# Bert

> Fast, responsive Android handheld frontend engine.

Bert is a high-performance, headless frontend engine designed for Android retro gaming handhelds (Retroid Pocket, AYN Odin, ANBERNIC, AYANEO) and gaming devices. 

It provides full ES-DE library compatibility, an in-app Steam client (friends list, presence, 2-way chat), Discord Rich Presence, and an Android Notification Center & Quick Settings bridge—completely decoupled from the UI layer via a reactive Kotlin `BertEngine` facade (`StateFlow`).

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────┐
│            UI Layer (Jetpack Compose / HUD)            │
│         (Handheld Carousel, Game Grid, HUD Drawer)     │
└───────────────────────────▲────────────────────────────┘
                            │ observes StateFlows / invokes suspend methods
┌───────────────────────────▼────────────────────────────┐
│                  BertEngine API Facade                 │
│  (libraryState, steamState, systemState, discordState) │
├────────────────────────────────────────────────────────┤
│                    BertDaemonService                   │
│         (Foreground Android Service Lifecycle)         │
├───────────────┬────────────────┬───────────────────────┤
│ Steam Subsys  │ Discord Subsys │ Game/Launcher Subsys  │
│ - Auth & WS   │ - Gateway WS   │ - ES-DE ROM Scanner   │
│ - Friends/Chat│ - Game Status  │ - Emulator Intents    │
├───────────────┴────────────────┴───────────────────────┤
│            Android System & Notification Subsys        │
│    (NotificationListenerService, Quick Settings Panel) │
└────────────────────────────────────────────────────────┘
```

---

## Key Features

- **PSP-Snappy Headless Engine:** Ultra-low latency state transitions designed for 60/120fps handheld navigation.
- **ES-DE Specification:** Direct support for standard `ROMs/<system>/gamelist.xml` hierarchies and `media/` covers—share your SD card across devices without conversion.
- **In-App Steam Client:** Authenticate via Steam Mobile QR code or Steam Guard, view friend presence in real-time, and send/receive chat messages directly from the handheld HUD.
- **Discord Rich Presence:** Automatically broadcasts what game, system, and box art you are playing via a direct Discord Gateway WebSocket connection.
- **Android Notification Center & Quick Settings:**
  - Background notification capture and dismissal using `NotificationListenerService`.
  - Zero-permission floating Wi-Fi and Bluetooth quick-panel dialogs (`Settings.Panel`).
  - Battery and network telemetry streaming.

---

## Project Structure

```text
Bert/
├── app/                  # Android application module (Services, Manifest, Android Bridge)
├── engine-core/          # Pure Kotlin multiplatform/JVM core engine (Models, Parsers, Protocols)
│   ├── src/main/kotlin/com/bert/engine/
│   │   ├── BertEngine.kt # Primary API Facade interface
│   │   └── model/        # Domain models (Game, GameSystem, SteamFriend, SystemState, etc.)
│   └── src/test/kotlin/  # Core unit test suites
├── docs/superpowers/
│   ├── specs/            # Architecture & design specifications
│   └── plans/            # Phased implementation plan (Tasks 1–8)
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Building & Testing

### Requirements
- **Java:** JDK 21+
- **Gradle:** 8.x / 9.x (or bundled Gradle wrapper)
- **Android SDK:** Compile SDK 34, Min SDK 29 (for `:app` module in Android Studio)

### Run Unit Tests
The core engine runs independently of the Android SDK for fast CLI and CI validation:

```bash
# Run engine-core unit tests
./gradlew :engine-core:test
```

---

## Roadmap

- [x] **Task 1:** Project Scaffolding, Core Models & `BertEngine` Facade
- [ ] **Task 2:** ES-DE Streaming XML Parser & Serializer (`gamelist.xml`)
- [ ] **Task 3:** ROM Directory Scanner & Room Database Persistence
- [ ] **Task 4:** Emulator Intent Dispatcher & Android App Discovery
- [ ] **Task 5:** Discord Rich Presence Subsystem (Gateway Client)
- [ ] **Task 6:** Steam Client Engine (QR Login, Friends Presence, 2-Way Chat)
- [ ] **Task 7:** Android System Subsystem (Notification Center & Quick Settings Bridge)
- [ ] **Task 8:** Foreground Daemon Service & Engine Facade Integration
- [ ] **UI Layer:** Jetpack Compose Handheld Frontend & HUD Drawer

---

## License

MIT
