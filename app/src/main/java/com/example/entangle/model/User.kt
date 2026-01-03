package com.example.entangle.model

import com.google.firebase.Timestamp

/**
 * User Model
 * Represents a user in the system with their public cryptographic keys
 * 
 * Firebase never stores private keys - only public keys for key exchange
 */
data class User(
    val uid: String = "",
    val username: String = "",
    val publicKeyClassical: String = "", // X25519 public key (Base64)
    val pqPublicHint: String = "", // Post-quantum public hint (Base64)
    val isOnline: Boolean = false, // Real-time online status
    val lastSeen: Timestamp? = null // Last seen timestamp when offline
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "username" to username,
            "publicKeyClassical" to publicKeyClassical,
            "pqPublicHint" to pqPublicHint,
            "isOnline" to isOnline
        )
        lastSeen?.let { map["lastSeen"] = it }
        return map
    }
}
