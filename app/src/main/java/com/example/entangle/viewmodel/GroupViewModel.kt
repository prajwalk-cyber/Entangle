package com.example.entangle.viewmodel

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entangle.crypto.HybridCrypto
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.model.Group
import com.example.entangle.model.Message
import com.example.entangle.model.User
import com.example.entangle.repository.FirebaseRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * GroupViewModel: End-to-End Encrypted Group Messaging
 */
class GroupViewModel(application: Application, private val groupId: String) : AndroidViewModel(application) {
    
    private val repository = FirebaseRepository()
    private val keyStorage = KeyStorage(application)
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var groupKey: ByteArray? = null
    private val currentUid: String = keyStorage.getCurrentUserUid() ?: ""
    
    init {
        initializeGroup()
    }
    
    private fun initializeGroup() {
        viewModelScope.launch {
            try {
                // 1. Fetch group metadata
                val groups = repository.getGroups()
                // For simplicity, we assume we find the group in the list or fetch it directly
                // (Direct fetch function could be added to repo)
                
                // 2. Fetch and decrypt GroupKey
                val encryptedKeyBase64 = repository.getGroupKeyForUser(groupId).getOrThrow()
                val encryptedKeyParts = String(Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)).split(":")
                
                // GroupKey is usually encrypted with member's Public Key during group creation
                // For this implementation, we'll assume it was encrypted with the session key logic
                // or similar. In a real app, you'd use the member's private key to decrypt.
                
                // For now, let's assume we have the key (This needs a robust DH exchange for groups)
                // groupKey = ...
                
                // 3. Listen to messages
                repository.getGroupMessages(groupId).collect { encryptedMessages ->
                    // Decrypt each message with groupKey
                    _messages.value = encryptedMessages // decryption skipped for now
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    fun sendMessage(text: String) {
        val key = groupKey ?: return
        viewModelScope.launch {
            val encrypted = HybridCrypto.encrypt(text, key)
            val message = Message(
                messageId = UUID.randomUUID().toString(),
                senderId = currentUid,
                ciphertext = encrypted.ciphertext,
                nonce = encrypted.nonce,
                timestamp = Timestamp.now()
            )
            repository.sendGroupMessage(groupId, message)
        }
    }
}
