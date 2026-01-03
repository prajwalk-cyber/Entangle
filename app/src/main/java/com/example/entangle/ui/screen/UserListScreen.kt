package com.example.entangle.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.entangle.model.User
import com.example.entangle.viewmodel.UserListViewModel

/**
 * UserListScreen: Available Users Display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    onUserClick: (String) -> Unit,
    viewModel: UserListViewModel = viewModel()
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUsername = viewModel.getCurrentUsername()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "Entangle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@$currentUsername",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && users.isEmpty() -> {
                    // Initial Loading
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                error != null -> {
                    // Error State
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Retry")
                        }
                    }
                }
                
                users.isEmpty() -> {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No users available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Waiting for other users to join...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    // User List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Contacts",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        
                        items(users) { user ->
                            UserCard(
                                user = user,
                                onClick = { onUserClick(user.uid) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * User Card Component
 */
@Composable
private fun UserCard(
    user: User,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
   // User Avatar with online indicator
            Box {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.username.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                // Online status indicator
                if (user.isOnline) {
                    Surface(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary, // Green
                        border = BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.surface
                        )
                    ) {}
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // User Info
            Column(modifier = Modifier.weight(1f)) {
                // Username with unique identifier
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "#${user.uid.takeLast(6)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                // Online status or last seen
                if (user.isOnline) {
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (user.lastSeen != null) {
                    val lastSeenText = formatLastSeen(user.lastSeen!!.toDate())
                    Text(
                        text = lastSeenText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "Tap to start secure chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Arrow Icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Format last seen time relative to now
 */
private fun formatLastSeen(lastSeen: java.util.Date): String {
    val now = System.currentTimeMillis()
    val diff = now - lastSeen.time
    
    val minutes = (diff / 60000)
    val hours = (diff / 3600000)
    val days = (diff / 86400000)
    
    return when {
        minutes < 1 ->  "Just now"
        minutes < 60 -> "Last seen $minutes min ago"
        hours < 24 -> "Last seen $hours hr ago"
        days < 7 -> "Last seen $days days ago"
        else -> "Last seen long ago"
    }
}
