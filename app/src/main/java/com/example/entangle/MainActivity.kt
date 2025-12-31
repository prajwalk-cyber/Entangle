package com.example.entangle

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.navigation.Screen
import com.example.entangle.ui.screen.ChatScreen
import com.example.entangle.ui.screen.SetupScreen
import com.example.entangle.ui.screen.UserListScreen
import com.example.entangle.ui.theme.EntangleTheme
import com.google.firebase.auth.FirebaseAuth

/**
 * MainActivity: Main Entry Point
 * 
 * Handles navigation between:
 * 1. Setup Screen (if first time)
 * 2. User List Screen
 * 3. Chat Screen
 * 
 * SECURITY: Uses Anonymous Firebase Authentication
 * - No admin restrictions
 * - No user blocking
 * - Automatic key generation on first launch
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Debug: Check Firebase Auth status
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d("MainActivity", "Firebase Auth Status: ${if (currentUser != null) "Authenticated (UID: ${currentUser.uid})" else "Not authenticated"}")
        
        val keyStorage = KeyStorage(this)
        val isSetupComplete = keyStorage.isSetupComplete()
        
        Log.d("MainActivity", "Setup Status: ${if (isSetupComplete) "Complete" else "Required"}")
        
        setContent {
            EntangleTheme {
                EntangleApp(isSetupComplete = isSetupComplete)
            }
        }
    }
}

/**
 * Main App Navigation
 */
@Composable
fun EntangleApp(isSetupComplete: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (isSetupComplete) Screen.UserList.route else Screen.Setup.route
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Setup Screen
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.UserList.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        
        // User List Screen
        composable(Screen.UserList.route) {
            UserListScreen(
                onUserClick = { peerUid ->
                    navController.navigate(Screen.Chat.createRoute(peerUid))
                }
            )
        }
        
        // Chat Screen
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("peerUid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerUid = backStackEntry.arguments?.getString("peerUid") ?: return@composable
            ChatScreen(
                peerUid = peerUid,
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}