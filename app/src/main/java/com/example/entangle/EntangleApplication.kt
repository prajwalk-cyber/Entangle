package com.example.entangle

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * EntangleApplication: Application Class
 * 
 * Initializes Firebase on app startup
 */
class EntangleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
    }
}
