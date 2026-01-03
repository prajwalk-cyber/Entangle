package com.example.entangle

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.SharedTransitionLayout
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.navigation.Screen
import com.example.entangle.ui.screen.ChatScreen
import com.example.entangle.ui.screen.SetupScreen
import com.example.entangle.ui.screen.SettingsScreen
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.entangle.data.SettingsManager
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsManager = SettingsManager(this)
        val keyStorage = KeyStorage(this)
        val isSetupComplete = keyStorage.isSetupComplete()
        
        setContent {
            val isDarkTheme by settingsManager.isDarkTheme.collectAsState()
            val textSizeScale by settingsManager.textSizeScale.collectAsState()
            
            // Industrial Slate Theme (enforced via settings)
            EntangleTheme(
                darkTheme = isDarkTheme,
                textSizeScale = textSizeScale,
                dynamicColor = false // Force our custom theme
            ) {
                EntangleApp(isSetupComplete = isSetupComplete)
            }
        }
    }
}

/**
 * Main App Navigation
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EntangleApp(isSetupComplete: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (isSetupComplete) Screen.UserList.route else Screen.Setup.route
    
    SharedTransitionLayout {
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
                        navController.navigate(Screen.Chat.createRoute(peerUid)) {
                            // Ensure we don't build up a stack of chat screens
                            popUpTo(Screen.UserList.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    animatedVisibilityScope = this,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }
            
            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.navigateUp() }
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
                    onNavigateBack = { navController.navigateUp() },
                    animatedVisibilityScope = this,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }
        }
    }
}