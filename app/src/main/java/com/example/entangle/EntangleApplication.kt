package com.example.entangle

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.entangle.repository.FirebaseRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * EntangleApplication: Application Class
 * 
 * Initializes Firebase and tracks app lifecycle for user presence
 */
class EntangleApplication : Application() {
    
    // Lazy initialization - only created after Firebase is initialized
    private val repository by lazy { FirebaseRepository() }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase FIRST
        FirebaseApp.initializeApp(this)
        
        // Register lifecycle observer for presence tracking
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d("EntangleApp", "App in foreground - setting user online")
                val uid = repository.getCurrentUserId()
                if (uid != null) {
                    applicationScope.launch {
                        repository.setUserOnline(uid)
                    }
                }
            }
            
            override fun onStop(owner: LifecycleOwner) {
                Log.d("EntangleApp", "App in background - setting user offline")
                val uid = repository.getCurrentUserId()
                if (uid != null) {
                    applicationScope.launch {
                        repository.setUserOffline(uid)
                    }
                }
            }
        })
    }
}
