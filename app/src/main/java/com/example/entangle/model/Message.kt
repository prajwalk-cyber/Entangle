package com.example.entangle.model

import com.google.firebase.Timestamp

/**
 * Message Status Enum
 */
enum class MessageStatus {
    SENT,       // Message sent to Firebase
    DELIVERED,  // Message received by recipient's device
    READ        // Message viewed by recipient
}

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
    val status: String = MessageStatus.SENT.name, // Message delivery status
    val deliveredAt: Timestamp? = null, // When message was delivered
    val readAt: Timestamp? = null, // When message was read
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    
    // File metadata (optional)
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileType: String? = null,
    val fileSize: Long? = null,
    val encryptedFileKey: String? = null, // Base64 key for symmetric file decryption
    
    // Reply metadata
    val replyToMessageId: String? = null,
    
    // Local only - never sent to Firebase
    var decryptedText: String? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "senderId" to senderId,
            "ciphertext" to ciphertext,
            "nonce" to nonce,
            "timestamp" to timestamp,
            "status" to status,
            "reactions" to reactions
        )
        deliveredAt?.let { map["deliveredAt"] = it }
        readAt?.let { map["readAt"] = it }
        fileUrl?.let { map["fileUrl"] = it }
        fileName?.let { map["fileName"] = it }
        fileType?.let { map["fileType"] = it }
        fileSize?.let { map["fileSize"] = it }
        encryptedFileKey?.let { map["encryptedFileKey"] = it }
        replyToMessageId?.let { map["replyToMessageId"] = it }
        return map
    }
    
    fun getStatusEnum(): MessageStatus {
        return try {
            MessageStatus.valueOf(status)
        } catch (e: Exception) {
            MessageStatus.SENT
        }
    }
}
