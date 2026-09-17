# Repository Guidelines

## Project Overview

**Bert** is a high-performance, headless Android handheld frontend backend engine designed for retro gaming devices (Retroid Pocket, AYN Odin, ANBERNIC, AYANEO) and Android handhelds. 

It solves foreground Low Memory Killer (LMK) process evictions during heavy emulation, strictly implements the EmulationStation Desktop Edition (`ROMs/<system>/gamelist.xml`) directory format for cross-device SD card sharing, and incorporates a native Steam client (QR login, presence, 2-way chat), Discord Rich Presence, and an Android Notification Center & Quick Settings bridge. All state is exposed via a headless reactive Kotlin `BertEngine` facade (`StateFlow`), fully decoupled from the future Jetpack Compose UI.

---

## Architecture & Data Flow

The project is structured as a decoupled multi-module Gradle project:

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
│             engine-core (Pure Kotlin JVM)              │
│ - Domain Models & Data Structures                      │
│ - ES-DE Streaming XmlPullParser & Serializer           │
│ - Steam Protocol Engine (Auth, CM WebSocket, Chat)     │
│ - Discord Gateway Client (WebSocket Opcode 3 Updates)  │
│ - ROM Scanner & Indexer                                │
├────────────────────────────────────────────────────────┤
│                 app (Android OS Bridge)                │
│ - BertDaemonService (Foreground Service with LMK shield│
│ - BertNotificationService (NotificationListenerService)│
│ - Emulator Intent Dispatcher (SAF Content URIs)        │
│ - Settings.Panel Quick Dialogs (Wi-Fi, Bluetooth)      │
└────────────────────────────────────────────────────────┘
```

- **Headless Facade (`BertEngine`)**: Exposes read-only `StateFlow<T>` streams to consumers while encapsulating internal mutable `MutableStateFlow` instances.
- **Process Resilience (`BertDaemonService`)**: Runs as an Android Foreground Service with an ongoing notification to ensure background networking (Steam chat, Discord status) survives emulator launches.
- **Zero-SDK Core**: Core business logic and networking live in `engine-core` (pure Kotlin JVM), allowing instant headless unit testing on host machines without an Android SDK installed.

---

## Key Directories

```text
Bert/
├── app/                                       # Android application module
│   └── src/main/
│       ├── AndroidManifest.xml                # Permissions, Foreground Service, NotificationListener
│       └── java/com/bert/engine/              # Android-specific bridges and services
├── engine-core/                               # Pure Kotlin/JVM core engine module
│   ├── src/main/kotlin/com/bert/engine/
│   │   ├── BertEngine.kt                      # Core public API facade interface
│   │   └── model/CoreModels.kt                # Domain models, sealed states, state objects
│   └── src/test/kotlin/com/bert/engine/       # Unit test suites (JUnit 4, Coroutine test)
├── docs/superpowers/
│   ├── specs/                                 # Architecture and subsystem design specifications
│   └── plans/                                 # Phased implementation plans (Tasks 1–8)
├── build.gradle.kts                           # Root Gradle configuration & plugin definitions
└── settings.gradle.kts                        # Multi-project inclusions (:engine-core, :app)
```

---

## Development Commands

### Build & Compilation
```bash
# Build engine-core module
./gradlew :engine-core:build

# Check compilation without running tests
./gradlew :engine-core:assemble
```

### Testing
```bash
# Run all unit tests in engine-core
./gradlew :engine-core:test

# Run a specific test class
./gradlew :engine-core:test --tests com.bert.engine.model.CoreModelsTest

# Run a specific test method
./gradlew :engine-core:test --tests com.bert.engine.model.CoreModelsTest.testGameSerialization

# Run tests with stacktrace on failure
./gradlew :engine-core:test --stacktrace
```

*Note on Windows:* Use `gradle` or `.\gradlew.bat` if the wrapper script is present.

---

## Code Conventions & Common Patterns

### 1. Model Definitions & Immutability
- Use immutable Kotlin `data class` declarations with explicit default values for optional collections or primitives.
- Persistent models that need JSON serialization must be annotated with `@Serializable` from `kotlinx.serialization`.
- Runtime UI/engine state containers (e.g. `LibraryState`, `SystemState`) remain plain data classes.

```kotlin
@Serializable
data class Game(
    val systemId: String,
    val romPath: String,
    val name: String,
    val playCount: Int = 0,
    val isFavorite: Boolean = false
)
```

### 2. State Machines via Sealed Interfaces
Model multi-step asynchronous workflows (e.g., authentication, scraping) using `sealed interface` with singleton `object` and payload-bearing `data class` variants:

```kotlin
sealed interface SteamQrLoginState {
    object Idle : SteamQrLoginState
    data class DisplayQr(val challengeUrl: String) : SteamQrLoginState
    object WaitingForMobileApproval : SteamQrLoginState
    object Success : SteamQrLoginState
    data class Failed(val error: String) : SteamQrLoginState
}
```

### 3. Asynchronous APIs & Reactive Streams
- **Expose StateFlow, Not MutableStateFlow**: Engine facades must expose public `val state: StateFlow<T>` backed by a private `_state = MutableStateFlow(...)`.
- **Suspend for Operations**: Non-instantaneous operations (network calls, filesystem scans, login) must be `suspend` functions.
- **Explicit Error Handling**: Return `Result<Unit>` or `Result<T>` on fallible operations rather than throwing unhandled exceptions.

```kotlin
interface BertEngine {
    val libraryState: StateFlow<LibraryState>
    suspend fun launchGame(systemId: String, romPath: String): Result<Unit>
    suspend fun scanLibraries(forceFullRescan: Boolean = false)
}
```

### 4. File I/O & Metadata Safety
- Always use streaming `XmlPullParser` / `XmlSerializer` for ES-DE `gamelist.xml` files rather than DOM parsers to ensure fast sub-100ms startup.
- Always use the atomic write pattern: write XML updates to `gamelist.xml.tmp`, flush, close, and atomically rename to `gamelist.xml` to prevent corruption during unexpected shutdowns.

---

## Important Files

- `engine-core/src/main/kotlin/com/bert/engine/BertEngine.kt`: The master API facade contract implemented by the backend and consumed by the UI.
- `engine-core/src/main/kotlin/com/bert/engine/model/CoreModels.kt`: All core domain types (`Game`, `GameSystem`, `SteamFriend`, `SystemState`, `AppNotification`, `DiscordState`).
- `app/src/main/AndroidManifest.xml`: Android system permissions, foreground service declarations, and notification listener filters.
- `engine-core/build.gradle.kts`: Dependencies for core engine (Kotlinx Coroutines, Kotlinx Serialization, OkHttp, JUnit 4).
- `docs/superpowers/specs/2026-09-17-android-handheld-frontend-backend-design.md`: Complete subsystem architecture specification.
- `docs/superpowers/plans/2026-09-17-android-handheld-frontend-backend.md`: Step-by-step implementation plan (Tasks 1 through 8).

---

## Runtime & Tooling Preferences

- **JDK Version**: Requires **JDK 21+** (e.g. OpenJDK 21 or Oracle JDK 21).
- **Gradle Version**: Tested with **Gradle 9.7.1** and Gradle 8.x.
- **Kotlin Version**: **2.1.0** (with `kotlinx-coroutines 1.9.0` and `kotlinx-serialization 1.7.3`).
- **Platform Separation**: 
  - Host CLI / CI workflows execute tests against `:engine-core` without requiring an Android SDK or Android platform tools.
  - Android development (`:app`) targets `compileSdk 34`, `minSdk 29` (Android 10+), and requires Android Studio with Android SDK installed.

---

## Testing & QA

- **Framework**: Standard **JUnit 4** (`junit:junit:4.13.2`) via `tasks.test { useJUnit() }`.
- **Coroutine Testing**: Use `kotlinx-coroutines-test:1.9.0` (`runTest`) when testing asynchronous suspending functions and flows.
- **Test Naming Convention**: `test<TargetEntity><Scenario>` (e.g., `testGameSerialization`, `testGameSystemExtensions`, `testSystemStateNotifications`).
- **QA Expectations**:
  - Every `@Serializable` model must have JSON serialization round-trip verification.
  - Every parser must handle missing optional tags, empty strings, and malformed tags gracefully.
  - Unit tests must be fast, deterministic, and self-contained with no filesystem leaks (use `TemporaryFolder` for file tests).
