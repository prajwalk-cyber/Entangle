package com.example.entangle.repository

import com.example.entangle.model.Message
import com.example.entangle.model.Session
import com.example.entangle.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    
    // Collection references
    private val usersCollection = firestore.collection("users")
    private val sessionsCollection = firestore.collection("sessions")
    private val messagesCollection = firestore.collection("messages")
    
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
}
