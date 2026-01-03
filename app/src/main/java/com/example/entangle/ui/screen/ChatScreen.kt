package com.example.entangle.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.util.Log
import com.example.entangle.model.Message
import com.example.entangle.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChatScreen: End-to-End Encrypted Chat
 * 
 * Features:
 * 1. Real-time encrypted message display
 * 2. Local decryption of messages
 * 3. Message encryption before sending
 * 4. Visual distinction between sent/received
 * 
 * CRITICAL: All encryption/decryption happens locally
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerUid: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            application = context.applicationContext as android.app.Application,
            peerUid = peerUid
        )
    )
    val messages by viewModel.messages.collectAsState()
    val peerUser by viewModel.peerUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUserId = viewModel.getCurrentUserId()
    
    // Search states
    val isSearchMode by viewModel.isSearchMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResultIndices by viewModel.searchResultIndices.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                cursor.moveToFirst()
                val fileName = cursor.getString(nameIndex)
                val fileSize = cursor.getLong(sizeIndex)
                val fileType = contentResolver.getType(it) ?: "application/octet-stream"
                
                viewModel.sendFile(it, fileName, fileType, fileSize)
            }
        }
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isSearchMode) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-scroll to current search result
    LaunchedEffect(currentSearchIndex, searchResultIndices) {
        if (currentSearchIndex >= 0 && currentSearchIndex < searchResultIndices.size) {
            val targetIndex = searchResultIndices[currentSearchIndex]
            listState.animateScrollToItem(targetIndex)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchMode) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text("Search messages...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            )
                        )
                    } else {
                        Column {
                            val user = peerUser
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user?.username ?: "Loading...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (user != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "#${user.uid.takeLast(6)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                            if (!isLoading) {
                                Text(
                                    text = "🔒 End-to-End Encrypted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (isSearchMode) { { viewModel.setSearchMode(false) } } else onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSearchMode) {
                        if (searchResultIndices.isNotEmpty()) {
                            Text(
                                text = "${currentSearchIndex + 1}/${searchResultIndices.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(onClick = { viewModel.navigateToPreviousResult() }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                            }
                            IconButton(onClick = { viewModel.navigateToNextResult() }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                            }
                        }
                        IconButton(onClick = { viewModel.setSearchMode(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = { viewModel.setSearchMode(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Messages")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            // Message Input Bar
            Surface(
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        maxLines = 4,
                        enabled = !isLoading
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText)
                                messageText = ""
                            }
                        },
                        enabled = messageText.isNotBlank() && !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank() && !isLoading) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    // Loading State
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Establishing secure connection...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            Text("Dismiss")
                        }
                    }
                }
                
                messages.isEmpty() -> {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔐",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Secure Channel Established",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Send a message to start encrypted chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    // Message List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(messages) { index, message ->
                            val isHighlighted = isSearchMode && searchResultIndices.contains(index)
                            val isCurrentResult = isSearchMode && currentSearchIndex >= 0 && 
                                                searchResultIndices.getOrNull(currentSearchIndex) == index
                            
                            MessageBubble(
                                message = message,
                                isCurrentUser = message.senderId == currentUserId,
                                isHighlighted = isHighlighted,
                                isCurrentResult = isCurrentResult,
                                searchQuery = if (isSearchMode) searchQuery else "",
                                onReactionSelect = { emoji ->
                                    viewModel.toggleReaction(message, emoji)
                                },
                                onFileClick = {
                                    viewModel.downloadAndDecryptFile(message) { bytes ->
                                        // Handle decrypted bytes (e.g. save to file system or open)
                                        Log.d("ChatScreen", "File decrypted: ${bytes.size} bytes")
                                        // In a real app, you'd save this to a file or open it
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Message Bubble Component
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    isHighlighted: Boolean = false,
    isCurrentResult: Boolean = false,
    searchQuery: String = "",
    onReactionSelect: (String) -> Unit = {},
    onFileClick: () -> Unit = {}
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    val commonEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")
    
    // Debug logging
    android.util.Log.d("MessageBubble", "Message from: ${message.senderId}, isCurrentUser: $isCurrentUser, text: ${message.decryptedText}")
    
    val backgroundColor = when {
        isCurrentResult -> MaterialTheme.colorScheme.tertiaryContainer
        isHighlighted -> MaterialTheme.colorScheme.surfaceVariant
        isCurrentUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
        ) {
            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                        bottomEnd = if (isCurrentUser) 4.dp else 16.dp
                    ),
                    color = backgroundColor,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { showReactionPicker = true }
                        ),
                    border = if (isCurrentResult) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                val text = message.decryptedText ?: "[Encrypted]"
                
                // File Attachment UI
                if (message.fileUrl != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "File",
                                tint = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName ?: "File",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${(message.fileSize ?: 0) / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (searchQuery.isNotEmpty() && text.contains(searchQuery, ignoreCase = true)) {
                    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                        var start = 0
                        while (start < text.length) {
                            val index = text.indexOf(searchQuery, start, ignoreCase = true)
                            if (index == -1) {
                                append(text.substring(start))
                                break
                            }
                            append(text.substring(start, index))
                            pushStyle(androidx.compose.ui.text.SpanStyle(
                                background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold
                            ))
                            append(text.substring(index, index + searchQuery.length))
                            pop()
                            start = index + searchQuery.length
                        }
                    }
                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        }
                    )
                    
                    // Show status icons for sent messages only
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val statusIcon = when (message.status) {
                            "READ" -> "✓✓" // Double check for read
                            "DELIVERED" -> "✓✓" // Double check for delivered
                            else -> "✓" // Single check for sent
                        }
                        val statusColor = if (message.status == "READ") {
                            MaterialTheme.colorScheme.primary // Blue for read
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        }
                        Text(
                            text = statusIcon,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    } else {
                        // Encryption lock icon for received messages
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "🔒",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                
                // Reaction Picker
                DropdownMenu(
                    expanded = showReactionPicker,
                    onDismissRequest = { showReactionPicker = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        commonEmojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        onReactionSelect(emoji)
                                        showReactionPicker = false
                                    },
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        }
        
        // Display Reactions
        if (message.reactions.isNotEmpty()) {
            val reactionCounts = message.reactions.values.groupingBy { it }.eachCount()
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reactionCounts.forEach { (emoji, count) ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.labelSmall)
                            if (count > 1) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
}

/**
 * Format timestamp
 */
fun formatTimestamp(date: Date): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}

/**
 * ViewModelFactory for ChatViewModel
 * Required to pass peerUid to ViewModel
 */
class ChatViewModelFactory(
    private val application: android.app.Application,
    private val peerUid: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            application = application,
            peerUid = peerUid
        ) as T
    }
}
