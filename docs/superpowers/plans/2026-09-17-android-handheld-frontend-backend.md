# Bert: Android Handheld Frontend Backend Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the headless backend engine for the Bert Android handheld frontend, providing ES-DE ROM library indexing, emulator launching, Discord Rich Presence, an in-app Steam client (QR login, friends, 2-way chat), and an Android Notification Center & Quick Settings bridge.

**Architecture:** A headless Kotlin engine running in an Android Foreground Service (`BertDaemonService`) to prevent process killing during gameplay. All state is exposed through a reactive `BertEngine` facade (`StateFlow`), decoupled from any UI.

**Tech Stack:** Kotlin, Coroutines, StateFlow, Android Gradle Plugin, Room (SQLite), OkHttp / WebSocket, Android XmlPullParser, EncryptedSharedPreferences / DataStore.

**Spec:** `docs/superpowers/specs/2026-09-17-android-handheld-frontend-backend-design.md`

## Global Constraints
- Target Android SDK: compileSdk 34, minSdk 29 (Android 10+), targetSdk 34.
- All code in Kotlin with zero UI dependencies (ready for Jetpack Compose UI binding).
- Strict adherence to ES-DE directory layout: `ROMs/<system>/gamelist.xml` and `media/`.
- All background long-lived network/presence jobs must survive emulator launches via `BertDaemonService`.

---

### Task 1: Android Project Scaffolding & Core Domain Models

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/bert/engine/model/CoreModels.kt`
- Create: `app/src/main/java/com/bert/engine/BertEngine.kt`
- Test: `app/src/test/java/com/bert/engine/model/CoreModelsTest.kt`

**Interfaces:**
- Consumes: None (Root scaffold)
- Produces: `BertEngine`, `GameSystem`, `Game`, `SteamSessionState`, `SystemState`, `DiscordState`

- [ ] **Step 1: Write the failing test for CoreModels**

```kotlin
package com.bert.engine.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreModelsTest {
    @Test
    fun testGameModelCreation() {
        val game = Game(
            systemId = "snes",
            romPath = "Super Mario World.sfc",
            name = "Super Mario World",
            description = "Classic platformer",
            imagePath = "media/covers/Super Mario World.png",
            rating = 0.95f,
            releaseDate = "19901121T000000",
            developer = "Nintendo",
            publisher = "Nintendo",
            genre = "Platform",
            players = "2",
            playCount = 10,
            lastPlayed = 1773740000000L,
            isFavorite = true
        )
        assertEquals("snes", game.systemId)
        assertEquals("Super Mario World", game.name)
        assertEquals(true, game.isFavorite)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.model.CoreModelsTest`
Expected: FAIL (unresolved reference `Game` or compilation error)

- [ ] **Step 3: Implement Gradle setup, AndroidManifest, and CoreModels**

Create `settings.gradle.kts`, `build.gradle.kts`, and `app/build.gradle.kts` with Android library/application plugins, coroutines, room, okhttp, and junit dependencies.
Create `app/src/main/java/com/bert/engine/model/CoreModels.kt`:
```kotlin
package com.bert.engine.model

data class GameSystem(
    val id: String,
    val name: String,
    val defaultCore: String? = null,
    val romExtensions: List<String>
)

data class Game(
    val systemId: String,
    val romPath: String,
    val name: String,
    val description: String? = null,
    val imagePath: String? = null,
    val rating: Float? = null,
    val releaseDate: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val players: String? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
    val isFavorite: Boolean = false
)

data class LibraryState(
    val systems: List<GameSystem> = emptyList(),
    val gamesBySystem: Map<String, List<Game>> = emptyMap(),
    val isScanning: Boolean = false
)

data class SteamSessionState(
    val isLoggedIn: Boolean = false,
    val currentSteamId: Long? = null,
    val personaName: String? = null,
    val friends: List<SteamFriend> = emptyList()
)

data class SteamFriend(
    val steamId: Long,
    val personaName: String,
    val personaState: Int = 0,
    val gameTitle: String? = null,
    val gameAppId: Int? = null,
    val avatarUrl: String = "",
    val unreadCount: Int = 0
)

data class SystemState(
    val isWifiConnected: Boolean = false,
    val wifiSsid: String? = null,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val activeNotifications: List<AppNotification> = emptyList()
)

data class AppNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isClearable: Boolean
)

data class DiscordState(
    val isConnected: Boolean = false,
    val currentActivity: String? = null
)
```

Create `app/src/main/java/com/bert/engine/BertEngine.kt` declaring the API facade interface.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.model.CoreModelsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts settings.gradle.kts app/
git commit -m "feat: scaffold android project and core domain models"
```

---

### Task 2: ES-DE XML Streaming Engine (`GamelistXmlParser` & `GamelistXmlSerializer`)

**Files:**
- Create: `app/src/main/java/com/bert/engine/xml/GamelistXmlParser.kt`
- Create: `app/src/main/java/com/bert/engine/xml/GamelistXmlSerializer.kt`
- Test: `app/src/test/java/com/bert/engine/xml/GamelistXmlTest.kt`

**Interfaces:**
- Consumes: `Game` from Task 1
- Produces: `GamelistXmlParser.parse(inputStream, systemId): List<Game>`, `GamelistXmlSerializer.serialize(games, outputStream)`

- [ ] **Step 1: Write failing unit tests for parsing and serialization**

```kotlin
package com.bert.engine.xml

import com.bert.engine.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GamelistXmlTest {
    private val sampleXml = """
        <?xml version="1.0"?>
        <gameList>
            <game>
                <path>./Super Mario World (USA).sfc</path>
                <name>Super Mario World</name>
                <desc>Mario and Luigi journey to Dinosaur Land.</desc>
                <image>./media/covers/Super Mario World (USA).png</image>
                <rating>0.95</rating>
                <developer>Nintendo</developer>
                <playcount>4</playcount>
                <favorite>true</favorite>
            </game>
        </gameList>
    """.trimIndent()

    @Test
    fun testParseGamelistXml() {
        val parser = GamelistXmlParser()
        val games = parser.parse(ByteArrayInputStream(sampleXml.toByteArray()), "snes")
        assertEquals(1, games.size)
        val game = games.first()
        assertEquals("snes", game.systemId)
        assertEquals("./Super Mario World (USA).sfc", game.romPath)
        assertEquals("Super Mario World", game.name)
        assertEquals(4, game.playCount)
        assertEquals(true, game.isFavorite)
    }

    @Test
    fun testRoundtripSerialization() {
        val parser = GamelistXmlParser()
        val serializer = GamelistXmlSerializer()
        val games = parser.parse(ByteArrayInputStream(sampleXml.toByteArray()), "snes")
        
        val out = ByteArrayOutputStream()
        serializer.serialize(games, out)
        
        val reParsed = parser.parse(ByteArrayInputStream(out.toByteArray()), "snes")
        assertEquals(games.size, reParsed.size)
        assertEquals(games.first().name, reParsed.first().name)
        assertEquals(games.first().playCount, reParsed.first().playCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.xml.GamelistXmlTest`
Expected: FAIL with `Unresolved reference: GamelistXmlParser`

- [ ] **Step 3: Implement streaming parser and serializer**

Implement `GamelistXmlParser` using `org.xmlpull.v1.XmlPullParser` handling tags: `game`, `path`, `name`, `desc`, `image`, `rating`, `releasedate`, `developer`, `publisher`, `genre`, `players`, `playcount`, `lastplayed`, `favorite`.
Implement `GamelistXmlSerializer` using `org.xmlpull.v1.XmlSerializer` outputting formatted `<gameList>` tree.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.xml.GamelistXmlTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/xml/ app/src/test/java/com/bert/engine/xml/
git commit -m "feat: implement streaming ES-DE gamelist xml parser and serializer"
```

---

### Task 3: ROM Directory Scanner & Room Database Persistence

**Files:**
- Create: `app/src/main/java/com/bert/engine/db/BertDatabase.kt`
- Create: `app/src/main/java/com/bert/engine/db/GameDao.kt`
- Create: `app/src/main/java/com/bert/engine/db/GameEntity.kt`
- Create: `app/src/main/java/com/bert/engine/library/RomScanner.kt`
- Test: `app/src/test/java/com/bert/engine/library/RomScannerTest.kt`

**Interfaces:**
- Consumes: `GamelistXmlParser` from Task 2, `Game` from Task 1
- Produces: `RomScanner.scanSystem(systemDir: File, systemId: String): List<Game>`, `BertDatabase`, `GameDao`

- [ ] **Step 1: Write unit test for RomScanner**

```kotlin
package com.bert.engine.library

import com.bert.engine.xml.GamelistXmlParser
import com.bert.engine.xml.GamelistXmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomScannerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testScanSystemWithExistingGamelist() {
        val snesDir = tempFolder.newFolder("snes")
        val romFile = File(snesDir, "Chrono Trigger.sfc").apply { writeText("dummy rom content") }
        val xmlFile = File(snesDir, "gamelist.xml").apply {
            writeText("""
                <gameList>
                    <game>
                        <path>./Chrono Trigger.sfc</path>
                        <name>Chrono Trigger</name>
                    </game>
                </gameList>
            """.trimIndent())
        }

        val scanner = RomScanner(GamelistXmlParser(), GamelistXmlSerializer())
        val games = scanner.scanDirectory(snesDir, "snes", listOf("sfc", "smc"))
        
        assertEquals(1, games.size)
        assertEquals("Chrono Trigger", games.first().name)
    }

    @Test
    fun testScanSystemWithUnscrapedRomSynthesizesEntry() {
        val gbaDir = tempFolder.newFolder("gba")
        File(gbaDir, "Pokemon FireRed.gba").apply { writeText("dummy rom content") }

        val scanner = RomScanner(GamelistXmlParser(), GamelistXmlSerializer())
        val games = scanner.scanDirectory(gbaDir, "gba", listOf("gba"))
        
        assertEquals(1, games.size)
        assertEquals("Pokemon FireRed", games.first().name)
        assertEquals("./Pokemon FireRed.gba", games.first().romPath)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.library.RomScannerTest`
Expected: FAIL with `Unresolved reference: RomScanner`

- [ ] **Step 3: Implement Room database and RomScanner**

Create `GameEntity` and `GameDao` with queries `insertGames()`, `getGamesForSystem()`, `updateGame()`, `getFavorites()`.
Implement `RomScanner` to walk directory files matching system extensions, merge with `gamelist.xml` entries, and synthesize entries for new ROMs without metadata.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.library.RomScannerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/db/ app/src/main/java/com/bert/engine/library/ app/src/test/java/com/bert/engine/library/
git commit -m "feat: implement room database and es-de rom directory scanner"
```

---

### Task 4: Emulator Intent Dispatcher & Android Game Discovery

**Files:**
- Create: `app/src/main/java/com/bert/engine/launcher/EmulatorRegistry.kt`
- Create: `app/src/main/java/com/bert/engine/launcher/EmulatorLauncher.kt`
- Create: `app/src/main/java/com/bert/engine/launcher/AndroidAppScanner.kt`
- Test: `app/src/test/java/com/bert/engine/launcher/EmulatorLauncherTest.kt`

**Interfaces:**
- Consumes: `Game` from Task 1
- Produces: `EmulatorLauncher.buildLaunchIntent(game: Game, romFileUri: Uri): Intent`

- [ ] **Step 1: Write unit test for EmulatorLauncher intent building**

```kotlin
package com.bert.engine.launcher

import android.content.Intent
import android.net.Uri
import com.bert.engine.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmulatorLauncherTest {
    @Test
    fun testBuildRetroArchLaunchIntent() {
        val launcher = EmulatorLauncher()
        val game = Game(systemId = "snes", romPath = "./Super Mario World.sfc", name = "Super Mario World")
        val uri = Uri.parse("content://com.bert.provider/snes/Super%20Mario%20World.sfc")
        
        val intent = launcher.buildLaunchIntent(game, uri, preferredPackage = "com.retroarch.aarch64")
        assertNotNull(intent)
        assertEquals("com.retroarch.aarch64", intent.`package`)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(uri, intent.data)
        assertEquals(true, (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun testBuildPPSSPPLaunchIntent() {
        val launcher = EmulatorLauncher()
        val game = Game(systemId = "psp", romPath = "./Crisis Core.iso", name = "Crisis Core")
        val uri = Uri.parse("content://com.bert.provider/psp/Crisis%20Core.iso")
        
        val intent = launcher.buildLaunchIntent(game, uri, preferredPackage = "org.ppsspp.ppsspp")
        assertNotNull(intent)
        assertEquals("org.ppsspp.ppsspp", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.launcher.EmulatorLauncherTest`
Expected: FAIL

- [ ] **Step 3: Implement EmulatorRegistry and EmulatorLauncher**

Create `EmulatorRegistry` mapping systems (`snes`, `gba`, `psx`, `psp`, `n64`, `gc`, `ps2`) to packages (RetroArch, PPSSPP, Dolphin, DuckStation, NetherSX2) with core paths and intent extra keys.
Create `EmulatorLauncher` constructing the intent with `FLAG_GRANT_READ_URI_PERMISSION` and component targets.
Create `AndroidAppScanner` querying `PackageManager` for `CATEGORY_LAUNCHER` and `CATEGORY_GAME`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.launcher.EmulatorLauncherTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/launcher/ app/src/test/java/com/bert/engine/launcher/
git commit -m "feat: implement emulator registry and intent launch engine"
```

---

### Task 5: Discord Rich Presence Subsystem (Gateway Client)

**Files:**
- Create: `app/src/main/java/com/bert/engine/discord/DiscordGatewayClient.kt`
- Create: `app/src/main/java/com/bert/engine/discord/DiscordRpcManager.kt`
- Create: `app/src/main/java/com/bert/engine/discord/DiscordPayloads.kt`
- Test: `app/src/test/java/com/bert/engine/discord/DiscordRpcManagerTest.kt`

**Interfaces:**
- Consumes: `Game` from Task 1
- Produces: `DiscordRpcManager.setActivity(game: Game)`, `DiscordRpcManager.clearActivity()`

- [ ] **Step 1: Write unit test for Discord Activity payload formatting**

```kotlin
package com.bert.engine.discord

import com.bert.engine.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordRpcManagerTest {
    @Test
    fun testBuildPresenceUpdateJson() {
        val game = Game(systemId = "snes", romPath = "./smw.sfc", name = "Super Mario World")
        val payload = DiscordPayloads.createPresenceUpdate(
            gameName = game.name,
            systemName = "Super Nintendo",
            startTimestamp = 1773740000000L
        )

        assertEquals(3, payload.op)
        val d = payload.d
        assertEquals("online", d.status)
        val activity = d.activities.first()
        assertEquals("Super Mario World", activity.name)
        assertEquals(0, activity.type)
        assertEquals("Super Nintendo", activity.details)
        assertEquals(1773740000000L, activity.timestamps?.start)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.discord.DiscordRpcManagerTest`
Expected: FAIL

- [ ] **Step 3: Implement Discord Gateway WebSocket & RPC Manager**

Create `DiscordPayloads` data structures mapping Gateway Opcodes (Opcode 1 Heartbeat, Opcode 2 Identify, Opcode 3 Presence Update).
Create `DiscordGatewayClient` using OkHttp WebSocket to connect to `wss://gateway.discord.gg/?v=10&encoding=json`.
Create `DiscordRpcManager` to manage connect/disconnect, heartbeat loop, and presence update dispatches.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.discord.DiscordRpcManagerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/discord/ app/src/test/java/com/bert/engine/discord/
git commit -m "feat: implement discord gateway client and presence manager"
```

---

### Task 6: Steam Client Engine (Auth, Friends, 2-Way Chat)

**Files:**
- Create: `app/src/main/java/com/bert/engine/steam/SteamAuthenticator.kt`
- Create: `app/src/main/java/com/bert/engine/steam/SteamCmClient.kt`
- Create: `app/src/main/java/com/bert/engine/steam/SteamFriendsManager.kt`
- Create: `app/src/main/java/com/bert/engine/steam/SteamChatManager.kt`
- Create: `app/src/main/java/com/bert/engine/steam/models/SteamModels.kt`
- Test: `app/src/test/java/com/bert/engine/steam/SteamFriendsManagerTest.kt`

**Interfaces:**
- Consumes: None
- Produces: `SteamSessionState`, `startQrLogin()`, `sendMessage(friendId, text)`

- [ ] **Step 1: Write unit test for Steam Friends and PersonaState parsing**

```kotlin
package com.bert.engine.steam

import com.bert.engine.model.SteamFriend
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamFriendsManagerTest {
    @Test
    fun testUpdateFriendPersona() {
        val manager = SteamFriendsManager()
        val friend = SteamFriend(
            steamId = 76561198000000000L,
            personaName = "GabeN",
            personaState = 1,
            gameTitle = "Half-Life 3"
        )
        manager.upsertFriend(friend)
        
        val state = manager.friendsFlow.value
        assertEquals(1, state.size)
        assertEquals("GabeN", state.first().personaName)
        assertEquals("Half-Life 3", state.first().gameTitle)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.steam.SteamFriendsManagerTest`
Expected: FAIL

- [ ] **Step 3: Implement Steam Authenticator, CM Client, and Chat Manager**

Implement `SteamAuthenticator` with `IAuthenticationService/GetAuthSessionViaQR` polling.
Implement `SteamCmClient` using OkHttp WebSocket for CM message framing.
Implement `SteamFriendsManager` and `SteamChatManager` to track friends, game presence, and message buffers.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.steam.SteamFriendsManagerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/steam/ app/src/test/java/com/bert/engine/steam/
git commit -m "feat: implement steam authentication, presence, and chat engine"
```

---

### Task 7: Android System & Notification Subsystem

**Files:**
- Create: `app/src/main/java/com/bert/engine/system/BertNotificationService.kt`
- Create: `app/src/main/java/com/bert/engine/system/SystemBridge.kt`
- Create: `app/src/main/java/com/bert/engine/system/ConnectivityObserver.kt`
- Test: `app/src/test/java/com/bert/engine/system/SystemBridgeTest.kt`

**Interfaces:**
- Consumes: None
- Produces: `SystemState`, `openWifiPanel()`, `openBluetoothPanel()`, `clearNotification(key)`

- [ ] **Step 1: Write unit test for SystemBridge and panel intents**

```kotlin
package com.bert.engine.system

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SystemBridgeTest {
    @Test
    fun testCreateWifiPanelIntent() {
        val bridge = SystemBridge(RuntimeEnvironment.getApplication())
        val intent = bridge.createWifiPanelIntent()
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, intent.action)
    }

    @Test
    fun testCreateBluetoothPanelIntent() {
        val bridge = SystemBridge(RuntimeEnvironment.getApplication())
        val intent = bridge.createBluetoothPanelIntent()
        assertEquals(Settings.Panel.ACTION_BLUETOOTH, intent.action)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.system.SystemBridgeTest`
Expected: FAIL

- [ ] **Step 3: Implement BertNotificationService and SystemBridge**

Create `BertNotificationService` extending `NotificationListenerService` dispatching `onNotificationPosted` and `onNotificationRemoved` to a shared repository.
Create `SystemBridge` with `createWifiPanelIntent()`, `createBluetoothPanelIntent()`, and battery broadcast listener.
Create `ConnectivityObserver` using `ConnectivityManager.NetworkCallback`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.system.SystemBridgeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/system/ app/src/test/java/com/bert/engine/system/
git commit -m "feat: implement android notification listener and system quick panel bridge"
```

---

### Task 8: BertDaemonService & BertEngine Facade Integration

**Files:**
- Create: `app/src/main/java/com/bert/engine/service/BertDaemonService.kt`
- Create: `app/src/main/java/com/bert/engine/BertEngineImpl.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/bert/engine/BertEngineIntegrationTest.kt`

**Interfaces:**
- Consumes: Tasks 1 through 7
- Produces: `BertEngine` singleton implementation, `BertDaemonService` foreground service

- [ ] **Step 1: Write integration test for BertEngine facade**

```kotlin
package com.bert.engine

import com.bert.engine.model.Game
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BertEngineIntegrationTest {
    @Test
    fun testEngineInitializationAndStateFlows() {
        val context = RuntimeEnvironment.getApplication()
        val engine = BertEngineImpl.create(context)
        
        assertNotNull(engine.libraryState.value)
        assertNotNull(engine.steamState.value)
        assertNotNull(engine.systemState.value)
        assertNotNull(engine.discordState.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.BertEngineIntegrationTest`
Expected: FAIL

- [ ] **Step 3: Implement BertDaemonService and BertEngineImpl**

Create `BertDaemonService` managing the foreground notification and holding background references to `SteamCmClient` and `DiscordRpcManager`.
Implement `BertEngineImpl` coordinating `RomScanner`, `EmulatorLauncher`, `DiscordRpcManager`, `SteamFriendsManager`, `SteamChatManager`, and `SystemBridge`.
Register the service in `AndroidManifest.xml` with `foregroundServiceType="connectedDevice|dataSync"`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.bert.engine.BertEngineIntegrationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bert/engine/ app/src/main/AndroidManifest.xml app/src/test/java/com/bert/engine/
git commit -m "feat: wire bert daemon foreground service and engine facade"
```
