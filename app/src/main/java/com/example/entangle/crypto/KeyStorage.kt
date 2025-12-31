package com.example.entangle.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * KeyStorage: Secure Local Key Management
 * 
 * Uses Android Keystore System via EncryptedSharedPreferences
 * 
 * CRITICAL SECURITY PRINCIPLES:
 * 1. Private keys NEVER leave the device
 * 2. Private keys NEVER sent to Firebase
 * 3. Keys encrypted at rest using Android Keystore
 * 4. Only public keys shared via Firebase
 */
class KeyStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences
    
    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "entangle_secure_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // ==================== CLASSICAL KEYS ====================
    
    /**
     * Store Classical Private Key (X25519)
     */
    fun saveClassicalPrivateKey(privateKey: PrivateKey) {
        val encoded = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        sharedPreferences.edit()
            .putString(KEY_CLASSICAL_PRIVATE, encoded)
            .apply()
    }
    
    /**
     * Retrieve Classical Private Key
     */
    fun getClassicalPrivateKey(): PrivateKey? {
        val encoded = sharedPreferences.getString(KEY_CLASSICAL_PRIVATE, null) ?: return null
        val keyBytes = Base64.decode(encoded, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("XDH")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }
    
    /**
     * Store Classical Public Key (Base64)
     */
    fun saveClassicalPublicKey(publicKey: String) {
        sharedPreferences.edit()
            .putString(KEY_CLASSICAL_PUBLIC, publicKey)
            .apply()
    }
    
    /**
     * Retrieve Classical Public Key
     */
    fun getClassicalPublicKey(): String? {
        return sharedPreferences.getString(KEY_CLASSICAL_PUBLIC, null)
    }
    
    // ==================== POST-QUANTUM KEYS ====================
    
    /**
     * Store Post-Quantum Private Key (Simulated)
     */
    fun savePQPrivateKey(privateKey: ByteArray) {
        val encoded = Base64.encodeToString(privateKey, Base64.NO_WRAP)
        sharedPreferences.edit()
            .putString(KEY_PQ_PRIVATE, encoded)
            .apply()
    }
    
    /**
     * Retrieve Post-Quantum Private Key
     */
    fun getPQPrivateKey(): ByteArray? {
        val encoded = sharedPreferences.getString(KEY_PQ_PRIVATE, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }
    
    /**
     * Store Post-Quantum Public Hint (Base64)
     */
    fun savePQPublicHint(publicHint: String) {
        sharedPreferences.edit()
            .putString(KEY_PQ_PUBLIC_HINT, publicHint)
            .apply()
    }
    
    /**
     * Retrieve Post-Quantum Public Hint
     */
    fun getPQPublicHint(): String? {
        return sharedPreferences.getString(KEY_PQ_PUBLIC_HINT, null)
    }
    
    // ==================== SESSION KEYS ====================
    
    /**
     * Store Hybrid Session Key for a specific peer
     * Key format: session_key_{peerUid}
     */
    fun saveSessionKey(peerUid: String, sessionKey: ByteArray) {
        val encoded = Base64.encodeToString(sessionKey, Base64.NO_WRAP)
        sharedPreferences.edit()
            .putString("$KEY_SESSION_PREFIX$peerUid", encoded)
            .apply()
    }
    
    /**
     * Retrieve Hybrid Session Key for a specific peer
     */
    fun getSessionKey(peerUid: String): ByteArray? {
        val encoded = sharedPreferences.getString("$KEY_SESSION_PREFIX$peerUid", null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }
    
    /**
     * Clear session key for a specific peer
     */
    fun clearSessionKey(peerUid: String) {
        sharedPreferences.edit()
            .remove("$KEY_SESSION_PREFIX$peerUid")
            .apply()
    }
    
    // ==================== USER INFO ====================
    
    /**
     * Store current user UID
     */
    fun saveCurrentUserUid(uid: String) {
        sharedPreferences.edit()
            .putString(KEY_CURRENT_UID, uid)
            .apply()
    }
    
    /**
     * Retrieve current user UID
     */
    fun getCurrentUserUid(): String? {
        return sharedPreferences.getString(KEY_CURRENT_UID, null)
    }
    
    /**
     * Store username
     */
    fun saveUsername(username: String) {
        sharedPreferences.edit()
            .putString(KEY_USERNAME, username)
            .apply()
    }
    
    /**
     * Retrieve username
     */
    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }
    
    // ==================== KEY STATUS ====================
    
    /**
     * Check if user has completed setup (keys generated)
     */
    fun isSetupComplete(): Boolean {
        return getClassicalPrivateKey() != null && 
               getPQPrivateKey() != null &&
               getCurrentUserUid() != null &&
               getUsername() != null
    }
    
    /**
     * Clear all stored keys (logout/reset)
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    companion object {
        private const val KEY_CLASSICAL_PRIVATE = "classical_private_key"
        private const val KEY_CLASSICAL_PUBLIC = "classical_public_key"
        private const val KEY_PQ_PRIVATE = "pq_private_key"
        private const val KEY_PQ_PUBLIC_HINT = "pq_public_hint"
        private const val KEY_SESSION_PREFIX = "session_key_"
        private const val KEY_CURRENT_UID = "current_uid"
        private const val KEY_USERNAME = "username"
    }
}
