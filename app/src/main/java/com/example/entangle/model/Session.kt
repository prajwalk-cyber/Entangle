package com.example.entangle.model

import com.google.firebase.Timestamp

/**
 * Session Model
 * Represents a chat session between two users
 * 
 * Session ID is deterministic: sorted(userA_uid, userB_uid)
 * This ensures both users reference the same session
 */
data class Session(
    val sessionId: String = "",
    val userA: String = "",
    val userB: String = "",
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userA" to userA,
            "userB" to userB,
            "createdAt" to createdAt
        )
    }
    
    companion object {
        /**
         * Generate deterministic session ID from two user IDs
         * Ensures both users reference the same session
         */
        fun generateSessionId(uid1: String, uid2: String): String {
            val sorted = listOf(uid1, uid2).sorted()
            return "${sorted[0]}_${sorted[1]}"
        }
    }
}
