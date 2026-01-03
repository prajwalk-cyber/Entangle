package com.example.entangle.model

import com.google.firebase.Timestamp

/**
 * Group Model
 */
data class Group(
    val groupId: String = "",
    val name: String = "",
    val creatorId: String = "",
    val memberIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val lastMessage: String? = null,
    val lastMessageTime: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "groupId" to groupId,
            "name" to name,
            "creatorId" to creatorId,
            "memberIds" to memberIds,
            "createdAt" to createdAt
        ).filterValues { it != null }
    }
}
