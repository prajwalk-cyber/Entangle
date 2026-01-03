package com.example.entangle.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.entangle.crypto.KeyStorage
import com.example.entangle.data.SettingsManager
import java.util.concurrent.Executor

/**
 * SettingsScreen: Functional Customization & Security Hub
 * 
 * FEATURES:
 * - Real-time Theme Switching
 * - Biometric Authentication (Fingerprint/Face)
 * - Identity Key Visualization
 * - Panic Button (Data Wipe)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    val keyStorage = remember { KeyStorage(context) }
    val settingsManager = remember { SettingsManager(context) }
    val currentUid = remember { keyStorage.getCurrentUserUid() ?: "UNKNOWN" }
    val scope = rememberCoroutineScope()
    
    // Observable States
    val isDarkTheme by settingsManager.isDarkTheme.collectAsState()
    val textSize by settingsManager.textSizeScale.collectAsState()
    
    // Local States
    var showPanicDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyFingerprint by remember { mutableStateOf("") }
    
    // Biometric State
    var isBiometricEnabled by remember { mutableStateOf(settingsManager.isBiometricEnabled) }

    // Biometric Helper Function
    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity == null) {
            onError("Host activity not found")
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ================== PROFILE SECTION ==================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                "Avatar",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Agent #$currentUid",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Status: Online & Encrypted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ================== SETTINGS LIST ==================
            
            SectionHeader("APPEARANCE")
            
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = "Industrial Mode",
                subtitle = "Always on for maximum stealth",
                trailing = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { settingsManager.setDarkTheme(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            )
            
            SettingsItem(
                icon = Icons.Default.FormatSize,
                title = "Text Size",
                subtitle = "Adjust message readability",
                trailing = {
                    // Simple Toggle for now (Normal vs Large)
                    Switch(
                        checked = textSize > 1.0f,
                        onCheckedChange = { 
                            settingsManager.setTextSizeScale(if (it) 1.2f else 1.0f) 
                        },
                        thumbContent = {
                            Icon(Icons.Default.FormatSize, null, modifier = Modifier.size(12.dp))
                        }
                    )
                }
            )
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
            
            SectionHeader("CHAT CUSTOMIZATION")
            
            val currentBgIndex by settingsManager.chatBackgroundIndex.collectAsState()
            
            Text(
                "Chat Background",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    "Standard", "Obsidian", "Circuit", "Grid", "Mesh", 
                    "Noise", "Midnight", "Emerald", "Steel", "Carbon"
                ).forEachIndexed { index, name ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { settingsManager.setChatBackgroundIndex(index) }
                            .padding(vertical = 8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp, 80.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = if (currentBgIndex == index) 
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                             // Minimal Preview (Actual themes defined in ChatScreen)
                             Box(
                                 modifier = Modifier
                                     .fillMaxSize()
                                     .background(getPreviewColor(index, isDarkTheme))
                             ) {
                                 if (currentBgIndex == index) {
                                     Icon(
                                         Icons.Default.CheckCircle, 
                                         null, 
                                         modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
                                         tint = MaterialTheme.colorScheme.primary
                                     )
                                 }
                             }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
            
            SectionHeader("MESSAGE STYLE")
            
            val senderIndex by settingsManager.senderBubbleIndex.collectAsState()
            val receiverIndex by settingsManager.receiverBubbleIndex.collectAsState()
            
            val bubbleColors = listOf(
                "Default" to com.example.entangle.ui.theme.ElectricBlue,
                "Blue" to com.example.entangle.ui.theme.BubbleBlue,
                "Green" to com.example.entangle.ui.theme.BubbleGreen,
                "Slate" to com.example.entangle.ui.theme.BubbleSlate,
                "Purple" to com.example.entangle.ui.theme.BubblePurple,
                "Teal" to com.example.entangle.ui.theme.BubbleTeal,
                "Carbon" to com.example.entangle.ui.theme.BubbleCarbon
            )
            
            Text(
                "Sender Bubble (Me)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bubbleColors.forEachIndexed { index, (name, color) ->
                    BubbleColorPicker(
                        name = name,
                        color = color,
                        isSelected = senderIndex == index,
                        onClick = { settingsManager.setSenderBubbleIndex(index) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Receiver Bubble (Peer)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bubbleColors.forEachIndexed { index, (name, color) ->
                    BubbleColorPicker(
                        name = name,
                        color = color,
                        isSelected = receiverIndex == index,
                        onClick = { settingsManager.setReceiverBubbleIndex(index) }
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
            
            SectionHeader("PRIVACY & SECURITY")
            
            SettingsItem(
                icon = Icons.Default.Fingerprint,
                title = "Identity Key",
                subtitle = "View your public fingerprint",
                onClick = { 
                    val key = keyStorage.getClassicalPublicKey()
                    keyFingerprint = key ?: "No key generated"
                    showKeyDialog = true 
                }
            )
            
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Passcode Lock",
                subtitle = if (isBiometricEnabled) "Biometrics Enabled" else "Biometrics Disabled",
                trailing = {
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            // Secure Toggle
                            if (enabled) {
                                // Enabling: Auth required
                                authenticate(
                                    title = "Enable Security",
                                    subtitle = "Verify identity to enable lock",
                                    onSuccess = {
                                        settingsManager.isBiometricEnabled = true
                                        isBiometricEnabled = true
                                        Toast.makeText(context, "Security Enabled", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { Toast.makeText(context, "Auth Failed: $it", Toast.LENGTH_SHORT).show() }
                                )
                            } else {
                                // Disabling: Auth required (Security!)
                                authenticate(
                                    title = "Disable Security",
                                    subtitle = "Verify identity to disable lock",
                                    onSuccess = {
                                        settingsManager.isBiometricEnabled = false
                                        isBiometricEnabled = false
                                        Toast.makeText(context, "Security Disabled", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { Toast.makeText(context, "Auth Failed: $it", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    )
                }
            )
            
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "Panic Button",
                subtitle = "Clear all local keys and data",
                titleColor = MaterialTheme.colorScheme.error,
                iconColor = MaterialTheme.colorScheme.error,
                onClick = { 
                    // Auth required for Panic? Maybe not, panic should be fast.
                    // But to prevent accidental wipe, let's keep the dialog confirmation.
                    showPanicDialog = true 
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Version
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Entangle v1.0.1 (Functional)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Panic Dialog
    if (showPanicDialog) {
        AlertDialog(
            onDismissRequest = { showPanicDialog = false },
            title = { Text("⚠️ PANIC RESET") },
            text = { Text("This will permanently delete your identity keys. You will lose access to all previous encrypted messages. Are you sure?") },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
            textContentColor = MaterialTheme.colorScheme.onErrorContainer,
            confirmButton = {
                Button(
                    onClick = { 
                        keyStorage.clearAll()
                        Runtime.getRuntime().exit(0) 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("WIPE DATA")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        )
    }

    // Key Dialog
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("YOUR IDENTITY KEY") },
            text = { 
                Column {
                    Text("This is your public cryptographic fingerprint. You can share this to verify identity.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) {
                        Text(
                            keyFingerprint.take(50) + "...", // Truncate visually
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("CLOSE")
                }
            }
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BubbleColorPicker(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.size(50.dp, 40.dp),
            shape = RoundedCornerShape(8.dp),
            color = color,
            border = if (isSelected) 
                BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground) 
            else null
        ) {
            if (isSelected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check, 
                        null, 
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall)
    }
}

private fun getPreviewColor(index: Int, isDark: Boolean): Color {
    return when (index) {
        0 -> if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0) // Standard
        1 -> Color(0xFF0F0F0F) // Obsidian
        2 -> Color(0xFF16213E) // Midnight
        3 -> Color(0xFF1B1B2F) // Circuit
        4 -> Color(0xFF0F3460) // Grid
        5 -> Color(0xFF03045E) // Mesh
        6 -> Color(0xFF023E8A) // Noise
        7 -> Color(0xFF0077B6) // Emerald Style
        8 -> Color(0xFF0096C7) // Steel Style
        9 -> Color(0xFF00B4D8) // Carbon Style
        else -> Color.Black
    }
}
