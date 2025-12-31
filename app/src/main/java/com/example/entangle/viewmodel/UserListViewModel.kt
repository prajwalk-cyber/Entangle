package com.example.entangle.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.model.User
import com.example.entangle.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UserListViewModel: Available Users Display
 * 
 * Handles:
 * 1. Fetch all available users (except current user)
 * 2. Real-time user list updates via Firestore
 * 3. Session initiation
 */
class UserListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = FirebaseRepository()
    private val keyStorage = KeyStorage(application)
    
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        loadUsers()
    }
    
    /**
     * Load users from Firebase
     * Real-time updates via Flow
     */
    private fun loadUsers() {
        val currentUid = keyStorage.getCurrentUserUid() ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getAllUsers(currentUid).collect { userList ->
                    _users.value = userList
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load users"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get current user info
     */
    fun getCurrentUsername(): String {
        return keyStorage.getUsername() ?: "Unknown"
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}
