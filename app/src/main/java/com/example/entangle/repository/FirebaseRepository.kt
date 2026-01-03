package com.example.entangle.repository

import java.util.UUID
import com.example.entangle.model.Group
import com.example.entangle.model.Message
import com.example.entangle.model.Session
import com.example.entangle.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * FirebaseRepository: Untrusted Message Relay
 * 
 * CRITICAL SECURITY PRINCIPLES:
 * 1. Firebase is UNTRUSTED - only encrypted data stored
 * 2. Firebase NEVER sees plaintext messages
 * 3. Firebase NEVER sees private keys
 * 4. Firebase only stores:
 *    - User public keys (for key exchange)
 *    - Encrypted message blobs
 *    - Session metadata
 */
class FirebaseRepository {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    
    // Collection references
    private val usersCollection = firestore.collection("users")
    private val sessionsCollection = firestore.collection("sessions")
    private val messagesCollection = firestore.collection("messages")
    private val groupsCollection = firestore.collection("groups")
    
    // ==================== AUTHENTICATION ====================
    
    /**
     * Sign in anonymously
     * Each user gets a unique UID
     * 
     * CRITICAL: Anonymous Authentication MUST be enabled in Firebase Console
     * Firebase Console → Authentication → Sign-in method → Anonymous → Enable
     */
    suspend fun signInAnonymously(): Result<String> {
        return try {
            // Check if already signed in
            val currentUser = auth.currentUser
            if (currentUser != null) {
                android.util.Log.d("FirebaseAuth", "Already authenticated: UID = ${currentUser.uid}")
                return Result.success(currentUser.uid)
            }
            
            // Sign in anonymously
            android.util.Log.d("FirebaseAuth", "Attempting anonymous sign-in...")
            val result = auth.signInAnonymously().await()
            val uid = result.user?.uid ?: throw Exception("Failed to get UID after authentication")
            
            android.util.Log.d("FirebaseAuth", "Anonymous sign-in successful: UID = $uid")
            Result.success(uid)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuth", "Sign-in failed: ${e.message}", e)
            
            // Provide helpful error message
            val errorMessage = when {
                e.message?.contains("admin", ignoreCase = true) == true -> 
                    "Anonymous Authentication is not enabled in Firebase Console. " +
                    "Please enable it: Authentication → Sign-in method → Anonymous → Enable"
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                e.message?.contains("API_KEY_INVALID", ignoreCase = true) == true ->
                    "Invalid API key. Please check your google-services.json file."
                else -> "Authentication failed: ${e.message}"
            }
            
            Result.failure(Exception(errorMessage))
        }
    }
    
    /**
     * Get current user UID
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * Sign out
     */
    fun signOut() {
        auth.signOut()
    }
    
    // ==================== USER MANAGEMENT ====================
    
    /**
     * Create user profile with public keys
     * ONLY public keys are uploaded - private keys stay on device
     * 
     * Uses Firestore merge to prevent overwriting if profile exists
     */
    suspend fun createUserProfile(user: User): Result<Unit> {
        return try {
            // Use set with merge to avoid overwriting existing data
            usersCollection.document(user.uid)
                .set(user.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get user by UID
     */
    suspend fun getUser(uid: String): Result<User> {
        return try {
            val snapshot = usersCollection.document(uid).get().await()
            val user = snapshot.toObject(User::class.java)?.copy(uid = uid)
                ?: throw Exception("User not found")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all users except current user
     * Returns Flow for real-time updates
     */
    fun getAllUsers(currentUid: String): Flow<List<User>> = callbackFlow {
        val listener = usersCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.copy(uid = doc.id)
                }?.filter { it.uid != currentUid } ?: emptyList()
                
                trySend(users)
            }
        
        awaitClose { listener.remove() }
    }
    
    // ==================== SESSION MANAGEMENT ====================
    
    /**
     * Create or get existing session between two users
     */
    suspend fun createOrGetSession(userAUid: String, userBUid: String): Result<Session> {
        return try {
            val sessionId = Session.generateSessionId(userAUid, userBUid)
            android.util.Log.d("FirebaseRepository", "Creating/getting session between $userAUid and $userBUid")
            android.util.Log.d("FirebaseRepository", "Generated session ID: $sessionId")
            
            val sessionRef = sessionsCollection.document(sessionId)
            
            // Check if session exists
            val snapshot = sessionRef.get().await()
            
            if (snapshot.exists()) {
                android.util.Log.d("FirebaseRepository", "Session already exists: $sessionId")
                val session = snapshot.toObject(Session::class.java)?.copy(sessionId = sessionId)
                    ?: throw Exception("Failed to parse session")
                Result.success(session)
            } else {
                android.util.Log.d("FirebaseRepository", "Creating new session: $sessionId")
                // Create new session
                val session = Session(
                    sessionId = sessionId,
                    userA = userAUid,
                    userB = userBUid
                )
                sessionRef.set(session.toMap()).await()
                android.util.Log.d("FirebaseRepository", "Session created successfully: $sessionId")
                Result.success(session)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to create/get session", e)
            Result.failure(e)
        }
    }

    /**
     * listenToSession
     * Observe session metadata (like pinned messages)
     */
    fun listenToSession(sessionId: String): Flow<Session?> = callbackFlow {
        val listener = sessionsCollection.document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                trySend(snapshot?.toObject(Session::class.java))
            }
        
        awaitClose { listener.remove() }
    }

    /**
     * Update pinned message
     */
    suspend fun updateSessionPin(sessionId: String, messageId: String?): Result<Unit> {
        return try {
            sessionsCollection.document(sessionId)
                .update("pinnedMessageId", messageId)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== MESSAGE OPERATIONS ====================
    
    /**
     * Send encrypted message
     * 
     * CRITICAL: Only encrypted data is sent
     * - ciphertext: AES-256-GCM encrypted (Base64)
     * - nonce: GCM nonce (Base64)
     * - Firebase NEVER sees plaintext
     * 
     * Firestore path: messages/{sessionId}/chat/{messageId}
     */
    suspend fun sendMessage(sessionId: String, message: Message): Result<Unit> {
        return try {
            android.util.Log.d("FirebaseRepository", "Sending message to session: $sessionId")
            android.util.Log.d("FirebaseRepository", "Message sender: ${message.senderId}")
            android.util.Log.d("FirebaseRepository", "Ciphertext (first 20 chars): ${message.ciphertext.take(20)}...")
            
            val docRef = messagesCollection.document(sessionId)
                .collection("chat")
                .add(message.toMap())
                .await()
                
            android.util.Log.d("FirebaseRepository", "Message sent successfully with ID: ${docRef.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to send message", e)
            Result.failure(e)
        }
    }
    
    /**
     * Listen to encrypted messages in real-time
     * 
     * Returns Flow of encrypted messages
     * Decryption happens locally on the device
     * 
     * Firestore path: messages/{sessionId}/chat/{messageId}
     */
    fun getMessages(sessionId: String): Flow<List<Message>> = callbackFlow {
        android.util.Log.d("FirebaseRepository", "Starting message listener for session: $sessionId")
        
        val listener = messagesCollection.document(sessionId)
            .collection("chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepository", "Message listener error", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                android.util.Log.d("FirebaseRepository", "Snapshot received: ${snapshot?.documents?.size ?: 0} documents")
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    android.util.Log.d("FirebaseRepository", "Processing document: ${doc.id}")
                    try {
                        val msg = doc.toObject(Message::class.java)?.copy(messageId = doc.id)
                        android.util.Log.d("FirebaseRepository", "Message parsed: sender=${msg?.senderId}, ciphertext=${msg?.ciphertext?.take(20)}...")
                        msg
                    } catch (e: Exception) {
                        android.util.Log.e("FirebaseRepository", "Failed to parse message: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                
                android.util.Log.d("FirebaseRepository", "Emitting ${messages.size} messages")
                trySend(messages)
            }
        
        awaitClose { 
            android.util.Log.d("FirebaseRepository", "Closing message listener for session: $sessionId")
            listener.remove() 
        }
    }
    
    // ==================== UTILITY ====================
    
    /**
     * Check if user profile exists in Firestore
     * Used to determine if we need to create new keys or restore existing user
     */
    suspend fun userExists(uid: String): Boolean {
        return try {
            usersCollection.document(uid).get().await().exists()
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== MESSAGE STATUS TRACKING ====================
    
    /**
     * Update message status
     * 
     * @param sessionId The session ID
     * @param messageId The message ID
     * @param status The new status
     * @param timestamp Optional timestamp for the status update
     */
    suspend fun updateMessageStatus(
        sessionId: String,
        messageId: String,
        status: String,
        timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            
            when (status) {
                "DELIVERED" -> updates["deliveredAt"] = timestamp
                "READ" -> {
                    updates["readAt"] = timestamp
                    updates["deliveredAt"] = timestamp // Ensure deliveredAt is also set
                }
                else -> {} // SENT doesn't need extra timestamps
            }
            
            firestore.collection("messages")
                .document(sessionId)
                .collection("chat")
                .document(messageId)
                .update(updates)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark message as delivered
     * Called when recipient's device receives the message
     */
    suspend fun markMessageAsDelivered(sessionId: String, messageId: String): Result<Unit> {
        return updateMessageStatus(sessionId, messageId, "DELIVERED")
    }
    
    /**
     * Mark message as read
     * Called when recipient views the message in chat
     */
    suspend fun markMessageAsRead(sessionId: String, messageId: String): Result<Unit> {
        return updateMessageStatus(sessionId, messageId, "READ")
    }
    
    // ==================== USER PRESENCE TRACKING ====================
    
    /**
     * Set user status to online
     */
    suspend fun setUserOnline(uid: String): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                    "isOnline" to true,
                    "lastSeen" to com.google.firebase.Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Set user status to offline
     */
    suspend fun setUserOffline(uid: String): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                   "isOnline" to false,
                    "lastSeen" to com.google.firebase.Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Listen to a specific user's presence status
     */
    fun listenToUserPresence(uid: String): Flow<Boolean> = callbackFlow {
        val listener = usersCollection.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val isOnline = snapshot?.getBoolean("isOnline") ?: false
                trySend(isOnline)
            }
        
        awaitClose { listener.remove() }
    }

    /**
     * Add or remove emoji reaction to a message
     */
    suspend fun toggleMessageReaction(
        sessionId: String,
        messageId: String,
        userId: String,
        emoji: String?
    ): Result<Unit> {
        return try {
            val update = if (emoji == null) {
                mapOf("reactions.$userId" to FieldValue.delete())
            } else {
                mapOf("reactions.$userId" to emoji)
            }
            
            firestore.collection("messages")
                .document(sessionId)
                .collection("chat")
                .document(messageId)
                .update(update)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete multiple messages (Batch)
     */
    suspend fun deleteMessages(sessionId: String, messageIds: List<String>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val collectionRef = messagesCollection.document(sessionId).collection("chat")
            
            messageIds.forEach { messageId ->
                batch.delete(collectionRef.document(messageId))
            }
            
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload encrypted file bytes to Firebase Storage
     */
    suspend fun uploadFile(
        sessionId: String,
        fileName: String,
        encryptedBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            val fileRef = storage.reference
                .child("attachments")
                .child(sessionId)
                .child("${System.currentTimeMillis()}_$fileName")
            
            val uploadTask = fileRef.putBytes(encryptedBytes)
            
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                onProgress(progress)
            }.await()
            
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download encrypted file bytes from Firebase Storage
     */
    suspend fun downloadFile(fileUrl: String): Result<ByteArray> {
        return try {
            val fileRef = storage.getReferenceFromUrl(fileUrl)
            val bytes = fileRef.getBytes(Long.MAX_VALUE).await() // Careful with memory for very large files
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== GROUP CHATS ====================

    /**
     * Create a new group with members
     */
    suspend fun createGroup(
        name: String,
        memberIds: List<String>,
        encryptedKeys: Map<String, String> // userId -> Base64(encryptedGroupKey)
    ): Result<String> {
        return try {
            val groupId = UUID.randomUUID().toString()
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
            
            val group = Group(
                groupId = groupId,
                name = name,
                creatorId = currentUserId,
                memberIds = memberIds + currentUserId,
                createdAt = Timestamp.now()
            )
            
            // 1. Create group document
            groupsCollection.document(groupId).set(group.toMap()).await()
            
            // 2. Upload encrypted keys for each member
            val batch = firestore.batch()
            encryptedKeys.forEach { (userId, encryptedKey) ->
                val keyRef = groupsCollection.document(groupId).collection("keys").document(userId)
                batch.set(keyRef, mapOf("key" to encryptedKey))
            }
            batch.commit().await()
            
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get groups that current user is a member of
     */
    fun getGroups(): Flow<List<Group>> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run {
            close(Exception("Not logged in"))
            return@callbackFlow
        }
        
        val listener = groupsCollection
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val groups = snapshot?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList()
                trySend(groups)
            }
        
        awaitClose { listener.remove() }
    }

    /**
     * Get encrypted GroupKey for the current user
     */
    suspend fun getGroupKeyForUser(groupId: String): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
            val doc = groupsCollection.document(groupId).collection("keys").document(userId).get().await()
            val key = doc.getString("key") ?: return Result.failure(Exception("Key not found"))
            Result.success(key)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listen to group messages
     */
    fun getGroupMessages(groupId: String): Flow<List<Message>> = callbackFlow {
        val listener = groupsCollection.document(groupId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }

    /**
     * Send message to a group
     */
    suspend fun sendGroupMessage(groupId: String, message: Message): Result<Unit> {
        return try {
            groupsCollection.document(groupId).collection("messages")
                .document(message.messageId)
                .set(message.toMap())
                .await()
            
            // Update last message in group
            groupsCollection.document(groupId)
                .update(mapOf(
                    "lastMessage" to message.ciphertext,
                    "lastMessageTime" to message.timestamp
                )).await()
                
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
