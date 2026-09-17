package com.bert.engine.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelsTest {

    @Test
    fun testGameModelInstantiation() {
        val game = Game(
            systemId = "snes",
            romPath = "Super Mario World.sfc",
            name = "Super Mario World",
            description = "Classic SNES platformer",
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
        assertEquals("Super Mario World.sfc", game.romPath)
        assertEquals("Super Mario World", game.name)
        assertEquals(0.95f, game.rating)
        assertEquals(10, game.playCount)
        assertTrue(game.isFavorite)
    }

    @Test
    fun testGameSerialization() {
        val game = Game(
            systemId = "gba",
            romPath = "Pokemon Emerald.gba",
            name = "Pokemon Emerald",
            playCount = 3
        )

        val json = Json.encodeToString(Game.serializer(), game)
        val decoded = Json.decodeFromString(Game.serializer(), json)

        assertEquals(game.systemId, decoded.systemId)
        assertEquals(game.romPath, decoded.romPath)
        assertEquals(game.name, decoded.name)
        assertEquals(game.playCount, decoded.playCount)
    }

    @Test
    fun testGameSystemExtensions() {
        val system = GameSystem(
            id = "snes",
            name = "Super Nintendo",
            defaultCore = "snes9x",
            romExtensions = listOf("sfc", "smc", "zip")
        )

        assertEquals("snes", system.id)
        assertTrue(system.romExtensions.contains("sfc"))
        assertEquals("snes9x", system.defaultCore)
    }

    @Test
    fun testSteamFriendAndPresence() {
        val friend = SteamFriend(
            steamId = 76561198000000000L,
            personaName = "GabeN",
            personaState = 1,
            gameTitle = "Half-Life 3",
            gameAppId = 480
        )

        assertEquals(76561198000000000L, friend.steamId)
        assertEquals("GabeN", friend.personaName)
        assertEquals("Half-Life 3", friend.gameTitle)
    }

    @Test
    fun testSystemStateNotifications() {
        val notification = AppNotification(
            key = "0|com.whatsapp|1|null|10001",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Friend",
            text = "Hey, let's play Mario Kart!",
            postTime = 1773740000000L,
            isClearable = true
        )

        val state = SystemState(
            isWifiConnected = true,
            wifiSsid = "Handheld-5G",
            batteryLevel = 87,
            isCharging = false,
            activeNotifications = listOf(notification)
        )

        assertTrue(state.isWifiConnected)
        assertEquals("Handheld-5G", state.wifiSsid)
        assertEquals(87, state.batteryLevel)
        assertEquals(1, state.activeNotifications.size)
        assertEquals("WhatsApp", state.activeNotifications.first().appName)
    }
}
