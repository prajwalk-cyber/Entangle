package com.example.entangle.model

import com.google.firebase.Timestamp

/**
 * Message Model
 * Represents an encrypted message
 * 
 * CRITICAL: Firebase only stores encrypted data
 * - ciphertext: AES-256-GCM encrypted message (Base64)
 * - nonce: GCM nonce for decryption (Base64)
 * - Firebase NEVER sees plaintext
 */
data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val ciphertext: String = "", // Base64 encrypted message
    val nonce: String = "", // Base64 GCM nonce
    val timestamp: Timestamp = Timestamp.now(),
    
    // Local only - never sent to Firebase
    var decryptedText: String? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "senderId" to senderId,
            "ciphertext" to ciphertext,
            "nonce" to nonce,
            "timestamp" to timestamp
        )
    }
}
