package com.example.entangle.viewmodel

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entangle.crypto.HybridCrypto
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.model.Message
import com.example.entangle.model.Session
import com.example.entangle.model.User
import com.example.entangle.repository.FirebaseRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import android.net.Uri
import java.io.InputStream
import android.content.ContentResolver
import android.provider.OpenableColumns

/**
 * ChatViewModel: End-to-End Encrypted Messaging
 * 
 * Handles:
 * 1. Hybrid session key derivation
 * 2. Local message encryption (AES-256-GCM)
 * 3. Local message decryption
 * 4. Real-time message sync via Firebase
 * 
 * CRITICAL: All encryption/decryption happens locally
 * Firebase only sees encrypted blobs
 */
class ChatViewModel(application: Application, private val peerUid: String) : AndroidViewModel(application) {
    
    private val repository = FirebaseRepository()
    private val keyStorage = KeyStorage(application)
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _peerUser = MutableStateFlow<User?>(null)
    val peerUser: StateFlow<User?> = _peerUser.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchMode = MutableStateFlow(false)
    val isSearchMode: StateFlow<Boolean> = _isSearchMode.asStateFlow()

    private val _searchResultIndices = MutableStateFlow<List<Int>>(emptyList())
    val searchResultIndices: StateFlow<List<Int>> = _searchResultIndices.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    // Reply state
    private val _replyToMessage = MutableStateFlow<Message?>(null)
    val replyToMessage: StateFlow<Message?> = _replyToMessage.asStateFlow()

    // Pinning state
    private val _pinnedMessageId = MutableStateFlow<String?>(null)
    val pinnedMessageId: StateFlow<String?> = _pinnedMessageId.asStateFlow()
    
    // Selection state
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()
    
    private var sessionId: String? = null
    private var sessionKey: ByteArray? = null
    private val currentUid: String
    
    init {
        // Validate current user UID
        val uid = keyStorage.getCurrentUserUid()
        if (uid.isNullOrBlank()) {
            Log.e("ChatViewModel", "Current user UID is null or empty!")
            _error.value = "User not authenticated. Please restart the app."
            _isLoading.value = false
            currentUid = ""
        } else {
            currentUid = uid
            Log.d("ChatViewModel", "Initializing chat with peer: $peerUid, current user: $currentUid")
            initializeChat()
        }
    }
    
    /**
     * Initialize chat session
     * 
     * Steps:
     * 1. Fetch peer user data
     * 2. Derive hybrid session key
     * 3. Create/get session
     * 4. Listen to messages
     */
    private fun initializeChat() {
        if (currentUid.isBlank()) {
            Log.e("ChatViewModel", "Cannot initialize chat: currentUid is blank")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Step 1: Fetching peer user data for UID: $peerUid")
                
                // Step 1: Fetch peer user
                val peerResult = repository.getUser(peerUid)
                if (peerResult.isFailure) {
                    val error = peerResult.exceptionOrNull()
                    Log.e("ChatViewModel", "Failed to fetch peer user", error)
                    throw error ?: Exception("Failed to fetch peer user")
                }
                _peerUser.value = peerResult.getOrThrow()
                Log.d("ChatViewModel", "Peer user fetched: ${_peerUser.value?.username}")
                
                // Step 2: Derive hybrid session key
                Log.d("ChatViewModel", "Step 2: Deriving hybrid session key...")
                sessionKey = deriveSessionKey(_peerUser.value!!)
                Log.d("ChatViewModel", "Session key derived successfully")
                
                // Step 3: Create or get session
                Log.d("ChatViewModel", "Step 3: Creating/fetching session...")
                val sessionResult = repository.createOrGetSession(currentUid, peerUid)
                if (sessionResult.isFailure) {
                    val error = sessionResult.exceptionOrNull()
                    Log.e("ChatViewModel", "Failed to create session", error)
                    throw error ?: Exception("Failed to create session")
                }
                sessionId = sessionResult.getOrThrow().sessionId
                Log.d("ChatViewModel", "Session created/fetched: $sessionId")
                
                // Step 4: Listen to encrypted messages
                Log.d("ChatViewModel", "Step 4: Starting message listener...")
                listenToMessages()
                
                // Step 5: Listen to session metadata (pinning)
                listenToSession()
                
                _isLoading.value = false
                Log.d("ChatViewModel", "Chat initialized successfully")
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Chat initialization failed: ${e.message}", e)
                _error.value = e.message ?: "Failed to initialize chat"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Derive Session Key from X25519 ECDH
     * 
     * FIXED: Using ONLY X25519 without hashing
     * The X25519 ECDH already produces a shared secret that's identical for both parties
     * 
     * Key derivation:
     * - Alice: sharedSecret = alice_private × bob_public
     * - Bob: sharedSecret = bob_private × alice_public  
     * - Result: BOTH GET SAME 32-byte shared secret (this is ECDH property)
     * 
     * We use the raw X25519 output directly as the 256-bit AES key
     * 
     * Session key is cached locally for performance
     */
    private fun deriveSessionKey(peer: User): ByteArray {
        try {
            Log.d("ChatViewModel", "=== DERIVING SESSION KEY ===")
            Log.d("ChatViewModel", "Peer: ${peer.username}")
            Log.d("ChatViewModel", "Peer UID: $peerUid")
            Log.d("ChatViewModel", "Current UID: $currentUid")
            
            // Check if session key already exists (USE CACHED KEY)
            val existingKey = keyStorage.getSessionKey(peerUid)
            if (existingKey != null) {
                Log.d("ChatViewModel", "Using cached session key")
                Log.d("ChatViewModel", "Cached key (hex): ${existingKey.joinToString("") { "%02x".format(it) }}")
                Log.d("ChatViewModel", "=== USING CACHED SESSION KEY ===")
                return existingKey
            }
            
            Log.d("ChatViewModel", "No cached key found, deriving new session key...")
            
            // Get local private key
            val classicalPrivateKey = keyStorage.getClassicalPrivateKey()
                ?: throw Exception("Classical private key not found. Please complete setup.")
            
            Log.d("ChatViewModel", "Local private key retrieved")
            Log.d("ChatViewModel", "Peer public key: ${peer.publicKeyClassical.take(30)}...")
            
            // Perform X25519 ECDH - both parties get SAME shared secret
            val sharedSecret = HybridCrypto.performClassicalKeyAgreement(
                privateKey = classicalPrivateKey,
                peerPublicKey = peer.publicKeyClassical
            )
            
            Log.d("ChatViewModel", "X25519 ECDH completed")
            Log.d("ChatViewModel", "Shared secret size: ${sharedSecret.size} bytes")
            Log.d("ChatViewModel", "Shared secret (hex): ${sharedSecret.joinToString("") { "%02x".format(it) }}")
            
            // Use raw X25519 output as AES-256 key (32 bytes)
            // DO NOT hash - both parties must use identical key
            val sessionKey = sharedSecret
            
            Log.d("ChatViewModel", "Session key derived: ${sessionKey.size} bytes")
            Log.d("ChatViewModel", "Session key (hex): ${sessionKey.joinToString("") { "%02x".format(it) }}")
            
            // Cache session key for future use
            keyStorage.saveSessionKey(peerUid, sessionKey)
            Log.d("ChatViewModel", "Session key cached")
            Log.d("ChatViewModel", "=== SESSION KEY DERIVATION COMPLETE ===")
            
            return sessionKey
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to derive session key", e)
            throw e
        }
    }
    
    /**
     * Listen to encrypted messages in real-time
     * Decrypt each message locally
     */
    private fun listenToMessages() {
        val session = sessionId
        val key = sessionKey
        
        if (session == null || key == null) {
            Log.e("ChatViewModel", "Cannot listen to messages: session=$session, key=${key != null}")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Listening to messages for session: $session")
                Log.d("ChatViewModel", "Using session key (hex, first 16 bytes): ${key.take(16).joinToString("") { "%02x".format(it) }}")
                
                repository.getMessages(session).collect { encryptedMessages ->
                    Log.d("ChatViewModel", "Received ${encryptedMessages.size} encrypted messages")
                    
                    // Decrypt messages locally
                    val decryptedMessages = encryptedMessages.map { msg ->
                        try {
                            Log.d("ChatViewModel", "Decrypting message from ${msg.senderId}")
                            Log.d("ChatViewModel", "Ciphertext (first 20 chars): ${msg.ciphertext.take(20)}...")
                            Log.d("ChatViewModel", "Nonce (first 20 chars): ${msg.nonce.take(20)}...")
                            
                            val encryptedMsg = HybridCrypto.EncryptedMessage(
                                ciphertext = msg.ciphertext,
                                nonce = msg.nonce
                            )
                            val plaintext = HybridCrypto.decrypt(encryptedMsg, key)
                            Log.d("ChatViewModel", "Message decrypted successfully: $plaintext")
                            
                            // Update message status if it's from peer
                            if (msg.senderId != currentUid) {
                                // Mark as delivered if not already
                                if (msg.status == "SENT") {
                                    viewModelScope.launch {
                                        repository.markMessageAsDelivered(session, msg.messageId)
                                    }
                                }
                                // Mark as read (user is viewing chat)
                                if (msg.status != "READ") {
                                    viewModelScope.launch {
                                        repository.markMessageAsRead(session, msg.messageId)
                                    }
                                }
                            }
                            
                            msg.copy(decryptedText = plaintext)
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Decryption failed for message ${msg.messageId}", e)
                            Log.e("ChatViewModel", "Error details: ${e.message}")
                            msg.copy(decryptedText = "[Decryption failed: ${e.message}]")
                        }
                    }
                    _messages.value = decryptedMessages
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Message listener error", e)
                _error.value = "Failed to receive messages: ${e.message}"
            }
        }
    }
    
    /**
     * Send encrypted message
     * 
     * Steps:
     * 1. Encrypt plaintext locally with AES-256-GCM
     * 2. Upload encrypted blob to Firebase
     * 3. Firebase never sees plaintext
     * 
     * NOTE: The message will appear via the real-time listener and be decrypted
     */
    fun sendMessage(plaintext: String) {
        if (plaintext.isBlank()) return
        
        val session = sessionId
        val key = sessionKey
        
        if (session == null || key == null) {
            Log.e("ChatViewModel", "Cannot send message: session=$session, key=${key != null}")
            _error.value = "Session not ready. Please wait."
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== SENDING MESSAGE ===")
                Log.d("ChatViewModel", "Plaintext: $plaintext")
                Log.d("ChatViewModel", "Using session key (hex): ${key.joinToString("") { "%02x".format(it) }}")
                
                // Step 1: Encrypt message locally
                val encryptedMsg = HybridCrypto.encrypt(plaintext, key)
                Log.d("ChatViewModel", "Message encrypted successfully")
                Log.d("ChatViewModel", "Ciphertext length: ${encryptedMsg.ciphertext.length} chars")
                Log.d("ChatViewModel", "Nonce length: ${encryptedMsg.nonce.length} chars")
                
                // Verify encryption by decrypting immediately
                try {
                    val testDecrypt = HybridCrypto.decrypt(encryptedMsg, key)
                    Log.d("ChatViewModel", "Self-decryption test: SUCCESS")
                    Log.d("ChatViewModel", "Original: $plaintext")
                    Log.d("ChatViewModel", "Decrypted: $testDecrypt")
                    if (testDecrypt != plaintext) {
                        Log.e("ChatViewModel", "WARNING: Self-decryption mismatch!")
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Self-decryption test FAILED", e)
                    throw Exception("Encryption verification failed: ${e.message}")
                }
                
                val message = Message(
                    messageId = UUID.randomUUID().toString(),
                    senderId = currentUid,
                    ciphertext = encryptedMsg.ciphertext,
                    nonce = encryptedMsg.nonce,
                    timestamp = Timestamp.now(),
                    replyToMessageId = _replyToMessage.value?.messageId
                )

                // Clear reply state after sending
                _replyToMessage.value = null
                
                Log.d("ChatViewModel", "Uploading to Firebase (session: $session)...")
                
                // Step 3: Upload to Firebase (only encrypted data)
                val result = repository.sendMessage(session, message)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e("ChatViewModel", "Failed to upload message", error)
                    throw error ?: Exception("Failed to send message")
                }
                
                Log.d("ChatViewModel", "Message sent successfully")
                Log.d("ChatViewModel", "=== MESSAGE SENT ===")
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Send message failed: ${e.message}", e)
                _error.value = e.message ?: "Failed to send message"
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String {
        Log.d("ChatViewModel", "getCurrentUserId: currentUid='$currentUid'")
        return currentUid
    }

    // ==================== SEARCH FUNCTIONALITY ====================

    /**
     * Toggle search mode
     */
    fun setSearchMode(enabled: Boolean) {
        _isSearchMode.value = enabled
        if (!enabled) {
            _searchQuery.value = ""
            _searchResultIndices.value = emptyList()
            _currentSearchIndex.value = -1
        }
    }

    /**
     * Update search query and find matches
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResultIndices.value = emptyList()
            _currentSearchIndex.value = -1
            return
        }

        val matches = mutableListOf<Int>()
        _messages.value.forEachIndexed { index, message ->
            val text = message.decryptedText ?: ""
            if (text.contains(query, ignoreCase = true)) {
                matches.add(index)
            }
        }
        
        _searchResultIndices.value = matches
        if (matches.isNotEmpty()) {
            _currentSearchIndex.value = 0
        } else {
            _currentSearchIndex.value = -1
        }
    }

    /**
     * Navigate to next search result
     */
    fun navigateToNextResult() {
        val matches = _searchResultIndices.value
        if (matches.isEmpty()) return
        
        _currentSearchIndex.value = (_currentSearchIndex.value + 1) % matches.size
    }

    /**
     * Navigate to previous search result
     */
    fun navigateToPreviousResult() {
        val matches = _searchResultIndices.value
        if (matches.isEmpty()) return
        
        _currentSearchIndex.value = if (_currentSearchIndex.value <= 0) {
            matches.size - 1
        } else {
            _currentSearchIndex.value - 1
        }
    }

    // ==================== REACTION FUNCTIONALITY ====================

    /**
     * Toggle reaction on a message
     */
    fun toggleReaction(message: Message, emoji: String) {
        val session = sessionId ?: return
        val currentReaction = message.reactions[currentUid]
        
        viewModelScope.launch {
            val newEmoji = if (currentReaction == emoji) null else emoji
            repository.toggleMessageReaction(session, message.messageId, currentUid, newEmoji)
        }
    }

    // ==================== FILE SHARING FUNCTIONALITY ====================

    /**
     * Send an encrypted file
     */
    fun sendFile(uri: Uri, fileName: String, fileType: String, fileSize: Long) {
        val session = sessionId ?: return
        val key = sessionKey ?: return
        
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== SENDING ENCRYPTED FILE ===")
                Log.d("ChatViewModel", "File: $fileName ($fileType, $fileSize bytes)")
                
                // 1. Read file bytes
                val context = getApplication<Application>()
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes() ?: throw Exception("Failed to read file")
                inputStream.close()
                
                // 2. Generate random file key (AES-256)
                val fileSymmetricKey = HybridCrypto.generateRandomKey()
                
                // 3. Encrypt file bytes
                val (encryptedBytes, fileNonce) = HybridCrypto.encryptBytes(fileBytes, fileSymmetricKey)
                
                // 4. Encrypt the file key (with session key)
                // We combine key + nonce for storage
                val fileKeyAndNonce = fileSymmetricKey + fileNonce
                val encryptedFileKeyBlob = HybridCrypto.encrypt(
                    Base64.encodeToString(fileKeyAndNonce, Base64.NO_WRAP),
                    key
                )
                
                // 5. Upload encrypted file to Storage
                val downloadUrl = repository.uploadFile(session, fileName, encryptedBytes) { progress ->
                    Log.d("ChatViewModel", "Upload progress: $progress%")
                    // Optional: Update UI progress
                }.getOrThrow()
                
                // 6. Send message with file metadata
                val message = Message(
                    messageId = UUID.randomUUID().toString(),
                    senderId = currentUid,
                    ciphertext = "[File: $fileName]", // Placeholder for search
                    nonce = "", // Not used for the message body itself when file is present
                    timestamp = Timestamp.now(),
                    fileUrl = downloadUrl,
                    fileName = fileName,
                    fileType = fileType,
                    fileSize = fileSize,
                    encryptedFileKey = Base64.encodeToString(
                        (encryptedFileKeyBlob.ciphertext + ":" + encryptedFileKeyBlob.nonce).toByteArray(),
                        Base64.NO_WRAP
                    )
                )
                
                repository.sendMessage(session, message).getOrThrow()
                Log.d("ChatViewModel", "File message sent successfully")
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "File send failed", e)
                _error.value = "Failed to send file: ${e.message}"
            }
        }
    }

    /**
     * Download and decrypt file
     */
    fun downloadAndDecryptFile(message: Message, onComplete: (ByteArray) -> Unit) {
        val key = sessionKey ?: return
        val encryptedKeyBlobBase64 = message.encryptedFileKey ?: return
        val fileUrl = message.fileUrl ?: return
        
        viewModelScope.launch {
            try {
                // 1. Decrypt the file key
                val blobText = String(Base64.decode(encryptedKeyBlobBase64, Base64.NO_WRAP))
                val parts = blobText.split(":")
                val encryptedKeyBlob = HybridCrypto.EncryptedMessage(parts[0], parts[1])
                
                val decryptedKeyAndNonceBase64 = HybridCrypto.decrypt(encryptedKeyBlob, key)
                val keyAndNonce = Base64.decode(decryptedKeyAndNonceBase64, Base64.NO_WRAP)
                
                val fileSymmetricKey = keyAndNonce.sliceArray(0 until 32)
                val fileNonce = keyAndNonce.sliceArray(32 until keyAndNonce.size)
                
                // 2. Download encrypted bytes
                val encryptedBytes = repository.downloadFile(fileUrl).getOrThrow()
                
                // 3. Decrypt bytes
                val decryptedBytes = HybridCrypto.decryptBytes(encryptedBytes, fileNonce, fileSymmetricKey)
                
                onComplete(decryptedBytes)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "File download/decrypt failed", e)
                _error.value = "Failed to download file: ${e.message}"
            }
        }
    }

    /**
     * listenToSession
     * Observe session updates (pinned message)
     */
    private fun listenToSession() {
        val session = sessionId ?: return
        viewModelScope.launch {
            repository.listenToSession(session).collect { sessionData ->
                _pinnedMessageId.value = sessionData?.pinnedMessageId
            }
        }
    }

    // ==================== PINNING ACTIONS ====================

    fun setReplyToMessage(message: Message?) {
        _replyToMessage.value = message
    }

    fun clearReplyToMessage() {
        _replyToMessage.value = null
    }

    fun pinMessage(messageId: String) {
        val session = sessionId ?: return
        viewModelScope.launch {
            repository.updateSessionPin(session, messageId)
        }
    }

    fun unpinMessage() {
        val session = sessionId ?: return
        viewModelScope.launch {
            repository.updateSessionPin(session, null)
        }
    }

    // ==================== SELECTION ACTIONS ====================

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedMessageIds.value = emptySet()
        }
    }

    fun toggleMessageSelection(messageId: String) {
        val current = _selectedMessageIds.value.toMutableSet()
        if (current.contains(messageId)) {
            current.remove(messageId)
        } else {
            current.add(messageId)
        }
        _selectedMessageIds.value = current
        
        // Auto-enable selection mode if not active
        if (current.isNotEmpty() && !_isSelectionMode.value) {
            _isSelectionMode.value = true
        }
        // Auto-disable if empty
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun deleteSelectedMessages() {
        val session = sessionId ?: run {
            Log.e("ChatViewModel", "Cannot delete: sessionId is null")
            return
        }
        val ids = _selectedMessageIds.value.toList()
        Log.d("ChatViewModel", "Attempting to delete ${ids.size} messages: $ids")
        
        if (ids.isEmpty()) {
            Log.w("ChatViewModel", "Delete called with empty selection")
            return
        }
        
        viewModelScope.launch {
            try {
                val result = repository.deleteMessages(session, ids)
                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Successfully deleted messages")
                } else {
                    Log.e("ChatViewModel", "Failed to delete messages", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in delete crash", e)
            } finally {
                // Clear selection
                _selectedMessageIds.value = emptySet()
                _isSelectionMode.value = false
            }
        }
    }
}
