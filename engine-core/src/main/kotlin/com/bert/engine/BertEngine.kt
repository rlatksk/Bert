package com.bert.engine

import com.bert.engine.model.DiscordState
import com.bert.engine.model.LibraryState
import com.bert.engine.model.SteamQrLoginState
import com.bert.engine.model.SteamSessionState
import com.bert.engine.model.SystemState
import kotlinx.coroutines.flow.StateFlow

interface BertEngine {
    // Reactive State Streams
    val libraryState: StateFlow<LibraryState>
    val steamState: StateFlow<SteamSessionState>
    val systemState: StateFlow<SystemState>
    val discordState: StateFlow<DiscordState>

    // Game & Library Commands
    suspend fun setRomsDirectoryPath(path: String)
    suspend fun scanLibraries(forceFullRescan: Boolean = false)
    suspend fun launchGame(systemId: String, romPath: String): Result<Unit>
    suspend fun toggleFavorite(systemId: String, romPath: String)

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
