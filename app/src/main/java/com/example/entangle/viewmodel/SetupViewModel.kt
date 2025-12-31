package com.example.entangle.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entangle.crypto.HybridCrypto
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.model.User
import com.example.entangle.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SetupViewModel: User Setup & Key Generation
 * 
 * Handles:
 * 1. Username input
 * 2. Hybrid key generation (Classical X25519 + Simulated PQ)
 * 3. Anonymous Firebase authentication
 * 4. Public key upload to Firebase
 * 5. Private key secure storage locally
 */
class SetupViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = FirebaseRepository()
    private val keyStorage = KeyStorage(application)
    
    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Input)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()
    
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()
    
    /**
     * Update username
     */
    fun updateUsername(name: String) {
        _username.value = name
    }
    
    /**
     * Complete setup process
     * 
     * Steps:
     * 1. Sign in anonymously to Firebase (NO ADMIN CHECKS)
     * 2. Check if user profile already exists
     * 3. Generate hybrid key pairs if new user (Classical + PQ)
     * 4. Store private keys locally (encrypted)
     * 5. Upload public keys to Firebase
     * 
     * SECURITY: No admin restrictions - all users can register
     */
    fun completeSetup() {
        if (_username.value.isBlank()) {
            _uiState.value = SetupUiState.Error("Username cannot be empty")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            
            try {
                Log.d("SetupViewModel", "Starting setup for username: ${_username.value}")
                
                // Step 1: Anonymous Firebase Auth (NO ADMIN CHECKS)
                Log.d("SetupViewModel", "Step 1: Attempting anonymous authentication...")
                val authResult = repository.signInAnonymously()
                if (authResult.isFailure) {
                    val error = authResult.exceptionOrNull()
                    Log.e("SetupViewModel", "Authentication failed", error)
                    throw error ?: Exception("Authentication failed")
                }
                val uid = authResult.getOrThrow()
                Log.d("SetupViewModel", "Authentication successful: UID = $uid")
                
                // Step 2: Check if user profile already exists
                Log.d("SetupViewModel", "Step 2: Checking if user exists in Firestore...")
                val userExists = repository.userExists(uid)
                Log.d("SetupViewModel", "User exists: $userExists")
                
                if (userExists) {
                    // User already has profile, just restore from Firestore
                    Log.d("SetupViewModel", "Restoring existing user profile...")
                    val existingUser = repository.getUser(uid)
                    if (existingUser.isSuccess) {
                        val user = existingUser.getOrThrow()
                        keyStorage.saveCurrentUserUid(uid)
                        keyStorage.saveUsername(user.username)
                        Log.d("SetupViewModel", "User profile restored: ${user.username}")
                        _uiState.value = SetupUiState.Success
                        return@launch
                    }
                }
                
                // Step 3: Generate Hybrid Keys (new user)
                Log.d("SetupViewModel", "Step 3: Generating hybrid key pairs...")
                val classicalKeyPair = HybridCrypto.generateClassicalKeyPair()
                val pqKeyPair = HybridCrypto.generatePQKeyPair()
                Log.d("SetupViewModel", "Keys generated successfully")
                
                // Step 4: Store Private Keys Locally (Encrypted)
                Log.d("SetupViewModel", "Step 4: Storing private keys locally...")
                keyStorage.saveClassicalPrivateKey(classicalKeyPair.privateKey)
                keyStorage.saveClassicalPublicKey(classicalKeyPair.publicKeyToBase64())
                keyStorage.savePQPrivateKey(pqKeyPair.privateKey)
                keyStorage.savePQPublicHint(pqKeyPair.publicHintToBase64())
                keyStorage.saveCurrentUserUid(uid)
                keyStorage.saveUsername(_username.value)
                Log.d("SetupViewModel", "Private keys stored securely")
                
                // Step 5: Upload Public Keys to Firebase (create user profile)
                Log.d("SetupViewModel", "Step 5: Uploading public keys to Firebase...")
                val user = User(
                    uid = uid,
                    username = _username.value,
                    publicKeyClassical = classicalKeyPair.publicKeyToBase64(),
                    pqPublicHint = pqKeyPair.publicHintToBase64()
                )
                
                val uploadResult = repository.createUserProfile(user)
                if (uploadResult.isFailure) {
                    val error = uploadResult.exceptionOrNull()
                    Log.e("SetupViewModel", "Failed to upload profile", error)
                    throw error ?: Exception("Failed to upload profile")
                }
                
                Log.d("SetupViewModel", "Setup completed successfully!")
                _uiState.value = SetupUiState.Success
                
            } catch (e: Exception) {
                Log.e("SetupViewModel", "Setup failed: ${e.message}", e)
                _uiState.value = SetupUiState.Error(e.message ?: "Setup failed")
            }
        }
    }
    
    /**
     * Check if setup is already complete
     */
    fun checkSetupStatus(): Boolean {
        return keyStorage.isSetupComplete()
    }
}

/**
 * Setup UI State
 */
sealed class SetupUiState {
    object Input : SetupUiState()
    object Loading : SetupUiState()
    object Success : SetupUiState()
    data class Error(val message: String) : SetupUiState()
}
