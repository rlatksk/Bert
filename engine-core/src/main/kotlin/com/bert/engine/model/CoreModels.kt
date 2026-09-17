package com.bert.engine.model

import kotlinx.serialization.Serializable

@Serializable
data class GameSystem(
    val id: String,
    val name: String,
    val defaultCore: String? = null,
    val romExtensions: List<String> = emptyList()
)

@Serializable
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

data class ChatMessage(
    val id: Long = 0L,
    val friendSteamId: Long,
    val senderSteamId: Long,
    val messageText: String,
    val timestamp: Long,
    val isIncoming: Boolean
)

sealed interface SteamQrLoginState {
    object Idle : SteamQrLoginState
    data class DisplayQr(val challengeUrl: String) : SteamQrLoginState
    object WaitingForMobileApproval : SteamQrLoginState
    object Success : SteamQrLoginState
    data class Failed(val error: String) : SteamQrLoginState
}

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
