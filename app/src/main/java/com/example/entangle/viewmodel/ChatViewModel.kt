package com.example.entangle.viewmodel

import android.app.Application
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
import java.util.UUID

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
                
                // Step 2: Create message object (encrypted)
                val message = Message(
                    messageId = UUID.randomUUID().toString(),
                    senderId = currentUid,
                    ciphertext = encryptedMsg.ciphertext,
                    nonce = encryptedMsg.nonce,
                    timestamp = Timestamp.now()
                )
                
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
}
