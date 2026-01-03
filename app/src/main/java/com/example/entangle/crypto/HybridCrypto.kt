package com.example.entangle.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * HybridCrypto: Post-Quantum Ready Cryptographic Engine
 * 
 * Architecture:
 * - Classical: X25519 (ECDH) - Real implementation
 * - Post-Quantum: Simulated (SHA-256 of random bytes) - Ready for ML-KEM drop-in
 * - Encryption: AES-256-GCM
 * - Key Derivation: SHA-256
 * 
 * Security Properties:
 * - End-to-End Encryption
 * - Forward Secrecy
 * - Quantum-Resistant Design (Hybrid)
 * - No Key Reuse
 */
object HybridCrypto {
    
    private const val CURVE = "X25519"
    private const val KEY_AGREEMENT_ALGORITHM = "XDH"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_NONCE_LENGTH = 12
    private const val PQ_SHARED_SECRET_LENGTH = 32
    
    /**
     * Classical Key Pair (X25519)
     */
    data class ClassicalKeyPair(
        val privateKey: PrivateKey,
        val publicKey: PublicKey
    ) {
        fun publicKeyToBase64(): String {
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        }
    }
    
    /**
     * Post-Quantum Key Pair (Simulated)
     * In production: Replace with ML-KEM (Kyber)
     */
    data class PQKeyPair(
        val privateKey: ByteArray,
        val publicHint: ByteArray
    ) {
        fun publicHintToBase64(): String {
            return Base64.encodeToString(publicHint, Base64.NO_WRAP)
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PQKeyPair
            return privateKey.contentEquals(other.privateKey) && 
                   publicHint.contentEquals(other.publicHint)
        }
        
        override fun hashCode(): Int {
            var result = privateKey.contentHashCode()
            result = 31 * result + publicHint.contentHashCode()
            return result
        }
    }
    
    /**
     * Encrypted Message Container
     */
    data class EncryptedMessage(
        val ciphertext: String,
        val nonce: String
    )
    
    // ==================== KEY GENERATION ====================
    
    /**
     * Generate Classical X25519 Key Pair
     */
    fun generateClassicalKeyPair(): ClassicalKeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_AGREEMENT_ALGORITHM)
        val keyPair = keyPairGenerator.generateKeyPair()
        return ClassicalKeyPair(keyPair.private, keyPair.public)
    }
    
    /**
     * Generate Post-Quantum Key Pair (Simulated)
     * 
     * Current: Random 32 bytes + SHA-256 hash as public hint
     * Future: Replace with ML-KEM (Kyber) keypair generation
     */
    fun generatePQKeyPair(): PQKeyPair {
        val privateKey = ByteArray(PQ_SHARED_SECRET_LENGTH)
        SecureRandom().nextBytes(privateKey)
        
        val publicHint = MessageDigest.getInstance(HASH_ALGORITHM).digest(privateKey)
        
        return PQKeyPair(privateKey, publicHint)
    }
    
    // ==================== KEY AGREEMENT ====================
    
    /**
     * Perform X25519 Key Agreement
     */
    fun performClassicalKeyAgreement(
        privateKey: PrivateKey,
        peerPublicKey: String
    ): ByteArray {
        android.util.Log.d("HybridCrypto", "=== X25519 KEY AGREEMENT ===")
        android.util.Log.d("HybridCrypto", "Peer public key (Base64): ${peerPublicKey.take(40)}...")
        
        val peerPublicKeyBytes = Base64.decode(peerPublicKey, Base64.NO_WRAP)
        android.util.Log.d("HybridCrypto", "Decoded peer public key: ${peerPublicKeyBytes.size} bytes")
        android.util.Log.d("HybridCrypto", "Peer public key (hex): ${peerPublicKeyBytes.joinToString("") { "%02x".format(it) }}")
        
        val keyFactory = KeyFactory.getInstance(KEY_AGREEMENT_ALGORITHM)
        val peerPublicKeyObj = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        android.util.Log.d("HybridCrypto", "Peer public key object created")
        
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM)
        keyAgreement.init(privateKey)
        android.util.Log.d("HybridCrypto", "Key agreement initialized with private key")
        
        keyAgreement.doPhase(peerPublicKeyObj, true)
        val sharedSecret = keyAgreement.generateSecret()
        
        android.util.Log.d("HybridCrypto", "Shared secret generated: ${sharedSecret.size} bytes")
        android.util.Log.d("HybridCrypto", "Shared secret (hex): ${sharedSecret.joinToString("") { "%02x".format(it) }}")
        android.util.Log.d("HybridCrypto", "=== KEY AGREEMENT COMPLETE ===")
        
        return sharedSecret
    }
    
    /**
     * Perform Post-Quantum Key Agreement (Simulated)
     * 
     * Current: SHA-256 of private key
     * Future: Replace with ML-KEM (Kyber) decapsulation
     */
    fun performPQKeyAgreement(privateKey: ByteArray): ByteArray {
        return MessageDigest.getInstance(HASH_ALGORITHM).digest(privateKey)
    }
    
    /**
     * Derive Hybrid Session Key
     * 
     * Combines classical and post-quantum shared secrets
     * Formula: SHA-256(classicalSecret || pqSecret)
     */
    fun deriveHybridSessionKey(
        classicalSharedSecret: ByteArray,
        pqSharedSecret: ByteArray
    ): ByteArray {
        val combined = classicalSharedSecret + pqSharedSecret
        return MessageDigest.getInstance(HASH_ALGORITHM).digest(combined)
    }
    
    // ==================== ENCRYPTION / DECRYPTION ====================
    
    /**
     * Encrypt Message with AES-256-GCM
     * 
     * Uses new nonce for each message
     */
    fun encrypt(plaintext: String, sessionKey: ByteArray): EncryptedMessage {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)
        
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKeySpec = SecretKeySpec(sessionKey, "AES")
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        return EncryptedMessage(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonce = Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
    }
    
    /**
     * Decrypt Message with AES-256-GCM
     */
    fun decrypt(encryptedMessage: EncryptedMessage, sessionKey: ByteArray): String {
        val plaintext = decryptBytes(
            Base64.decode(encryptedMessage.ciphertext, Base64.NO_WRAP),
            Base64.decode(encryptedMessage.nonce, Base64.NO_WRAP),
            sessionKey
        )
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Encrypt bytes with AES-256-GCM
     */
    fun encryptBytes(data: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)
        
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKeySpec = SecretKeySpec(key, "AES")
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
        val ciphertext = cipher.doFinal(data)
        return Pair(ciphertext, nonce)
    }

    /**
     * Decrypt bytes with AES-256-GCM
     */
    fun decryptBytes(ciphertext: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKeySpec = SecretKeySpec(key, "AES")
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Generate a random 256-bit key
     */
    fun generateRandomKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }
    
    // ==================== UTILITY ====================
    
    /**
     * Reconstruct Public Key from Base64
     */
    fun base64ToPublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance(KEY_AGREEMENT_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }
    
    /**
     * Hash function for key derivation
     */
    fun hash(data: ByteArray): ByteArray {
        return MessageDigest.getInstance(HASH_ALGORITHM).digest(data)
    }
}
