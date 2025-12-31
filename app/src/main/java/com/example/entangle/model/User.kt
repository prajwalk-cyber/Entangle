package com.example.entangle.model

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
    val pqPublicHint: String = "" // Post-quantum public hint (Base64)
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "username" to username,
            "publicKeyClassical" to publicKeyClassical,
            "pqPublicHint" to pqPublicHint
        )
    }
}
