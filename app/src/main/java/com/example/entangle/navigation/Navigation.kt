package com.example.entangle.navigation

/**
 * Navigation Routes
 * 
 * Sealed class for type-safe navigation
 */
sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object UserList : Screen("user_list")
    data class Chat(val peerUid: String) : Screen("chat/{peerUid}") {
        companion object {
            const val route = "chat/{peerUid}"
            fun createRoute(peerUid: String) = "chat/$peerUid"
        }
    }
}
