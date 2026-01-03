package com.example.entangle.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.foundation.BorderStroke
import com.example.entangle.model.Message
import com.example.entangle.viewmodel.ChatViewModel
import com.example.entangle.ui.theme.glass
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * ChatScreen: Advanced Glassmorphic UI
 * 
 * FEATURES:
 * - Glassmorphism Top Bar & Pinned Header
 * - Pinned Messages (Sticky Header)
 * - Multi-Select & Delete (Long Press)
 * - Swipe to Reply
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ChatScreen(
    peerUid: String,
    onNavigateBack: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            application = context.applicationContext as android.app.Application,
            peerUid = peerUid
        )
    )
    
    // States
    val messages by viewModel.messages.collectAsState()
    val peerUser by viewModel.peerUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()
    val pinnedMessageId by viewModel.pinnedMessageId.collectAsState()
    
    // Selection States
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsState()
    
    // Search States
    val isSearchMode by viewModel.isSearchMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResultIndices by viewModel.searchResultIndices.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()
    
    val currentUserId = viewModel.getCurrentUserId()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val settingsManager = remember { com.example.entangle.data.SettingsManager(context) }
    val chatBgIndex by settingsManager.chatBackgroundIndex.collectAsState()
    val senderBubbleIndex by settingsManager.senderBubbleIndex.collectAsState()
    val receiverBubbleIndex by settingsManager.receiverBubbleIndex.collectAsState()
    val isDarkTheme by settingsManager.isDarkTheme.collectAsState()
    
    // Group messages by date
    val groupedMessages = remember(messages) {
        messages.groupBy {
            val date = it.timestamp.toDate()
            SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(date)
        }
    }
    
    // Resolve Pinned Message
    val pinnedMessage = remember(messages, pinnedMessageId) {
        messages.find { it.messageId == pinnedMessageId }
    }

    // File picker
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
    
    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isSearchMode) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                // GLASS TOP BAR
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    // 1. Glass Background Layer
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .glass(backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    )
                    
                    // 2. Content Layer
                    with(sharedTransitionScope) {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "TopBarTransition"
                        ) { selectionActive ->
                             TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                                navigationIcon = {
                                    if (selectionActive) {
                                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                                            Icon(Icons.Default.Close, "Close")
                                        }
                                    } else {
                                        IconButton(onClick = if (isSearchMode) { { viewModel.setSearchMode(false) } } else onNavigateBack) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                        }
                                    }
                                },
                                title = {
                                    if (selectionActive) {
                                        Text("${selectedMessageIds.size} Selected", style = MaterialTheme.typography.titleMedium)
                                    } else if (isSearchMode) {
                                        TextField(
                                            value = searchQuery,
                                            onValueChange = { viewModel.onSearchQueryChange(it) },
                                            placeholder = { Text("Search messages...", style = MaterialTheme.typography.bodyMedium) },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            )
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 🐌 Slow Liquid Shared Avatar
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .sharedElement(
                                                        state = rememberSharedContentState(key = "image-${peerUid}"),
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        boundsTransform = { _, _ ->
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessVeryLow
                                                            )
                                                        }
                                                    )
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                 Text(
                                                    text = peerUser?.username?.take(1)?.uppercase() ?: "?",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(12.dp))
                                            
                                            Column {
                                                Text(
                                                    text = peerUser?.username ?: "Decrypting...", 
                                                    style = MaterialTheme.typography.titleMedium, 
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.sharedElement(
                                                        state = rememberSharedContentState(key = "name-${peerUid}"),
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        boundsTransform = { _, _ ->
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessVeryLow
                                                            )
                                                        }
                                                    )
                                                )
                                                AnimatedVisibility(visible = !isLoading) {
                                                    Text(
                                                        if (peerUser?.isOnline == true) "Online" else "Encrypted Channel", 
                                                        style = MaterialTheme.typography.labelSmall, 
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                actions = {
                                    if (selectionActive) {
                                        // PIN ACTION
                                        if (selectedMessageIds.size == 1) {
                                            IconButton(onClick = { 
                                                viewModel.pinMessage(selectedMessageIds.first())
                                                viewModel.toggleSelectionMode()
                                            }) {
                                                Icon(Icons.Default.PushPin, "Pin")
                                            }
                                        }
                                        
                                        // DELETE ACTION (With Fix Confirmation)
                                        IconButton(onClick = { viewModel.deleteSelectedMessages() }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                        
                                        // MORE OPTIONS
                                        var showMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(Icons.Default.MoreVert, "More")
                                            }
                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Copy Text") },
                                                    onClick = { /* Not implemented */ showMenu = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Forward") },
                                                    onClick = { showMenu = false }
                                                )
                                            }
                                        }
                                    } else if (isSearchMode) {
                                        if (searchResultIndices.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${currentSearchIndex + 1}/${searchResultIndices.size}", style = MaterialTheme.typography.labelSmall)
                                                IconButton(onClick = { viewModel.navigateToPreviousResult() }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                                IconButton(onClick = { viewModel.navigateToNextResult() }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                            }
                                        }
                                    } else {
                                        IconButton(onClick = { viewModel.setSearchMode(true) }) {
                                            Icon(Icons.Default.Search, "Search")
                                        }
                                    }
                                }
                            )
                        }   
                    }
                }
                
                // PINNED MESSAGE HEADER
                AnimatedVisibility(
                    visible = pinnedMessage != null && !isSelectionMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                            .glass(
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                blurRadius = 10.dp
                            )
                            .clickable { 
                                // Scroll to pinned message
                                val index = messages.indexOf(pinnedMessage)
                                if (index != -1) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(modifier = Modifier.width(2.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pinned Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(pinnedMessage?.decryptedText ?: "File/Media", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            IconButton(onClick = { viewModel.unpinMessage() }) {
                                Icon(Icons.Default.Close, "Unpin", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
             if (!isSelectionMode) {
                 Column {
                    // Reply Preview
                    AnimatedVisibility(
                        visible = replyToMessage != null,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Vertical Indicator
                                Box(modifier = Modifier.width(4.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Replying to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(replyToMessage?.decryptedText ?: "Media", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { viewModel.setReplyToMessage(null) }) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    
                    // Input Area
                     Surface(
                         color = MaterialTheme.colorScheme.background,
                         tonalElevation = 2.dp
                     ) {
                         Row(
                             modifier = Modifier.fillMaxWidth().padding(16.dp),
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             IconButton(
                                 onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                 modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(40.dp)
                             ) {
                                 Icon(Icons.Default.AttachFile, "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                             }
                             Spacer(modifier = Modifier.width(8.dp))
                             OutlinedTextField(
                                 value = messageText,
                                 onValueChange = { messageText = it },
                                 modifier = Modifier.weight(1f).heightIn(min = 50.dp, max = 120.dp),
                                 placeholder = { Text("Secure message...") },
                                 shape = RoundedCornerShape(24.dp),
                                 colors = OutlinedTextFieldDefaults.colors(
                                     focusedBorderColor = MaterialTheme.colorScheme.primary,
                                     unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                 ),
                                 trailingIcon = {
                                     if (messageText.isNotBlank()) {
                                         IconButton(
                                             onClick = { 
                                                 viewModel.sendMessage(messageText)
                                                 messageText = ""
                                             },
                                             modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(32.dp)
                                         ) {
                                             Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                                         }
                                     }
                                 }
                             )
                         }
                     }
                 }
             }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Render Background Layer
            ChatBackground(index = chatBgIndex, isDark = isDarkTheme)
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedMessages.forEach { (date, messagesForDate) ->
                    stickyHeader {
                        DateHeader(date)
                    }
                    
                    itemsIndexed(messagesForDate, key = { _, msg -> msg.messageId }) { index, message ->
                        val isSelected = selectedMessageIds.contains(message.messageId)
                        val isHighlighted = isSearchMode && searchResultIndices.contains(messages.indexOf(message))
                        
                        Box(modifier = Modifier.animateItemPlacement()) {
                            if (isSelectionMode) {
                                MessageBubble(
                                    message = message,
                                    isMe = message.senderId == currentUserId,
                                    viewModel = viewModel,
                                    isSelectionMode = true,
                                    isSelected = isSelected,
                                    onReplySwipe = {},
                                    senderBubbleIndex = senderBubbleIndex,
                                    receiverBubbleIndex = receiverBubbleIndex
                                )
                            } else {
                                SwipeToReply(onReply = { viewModel.setReplyToMessage(message) }) {
                                    MessageBubble(
                                        message = message,
                                        isMe = message.senderId == currentUserId,
                                        viewModel = viewModel,
                                        isSelectionMode = false,
                                        isSelected = isSelected,
                                        onReplySwipe = { viewModel.setReplyToMessage(message) },
                                        senderBubbleIndex = senderBubbleIndex,
                                        receiverBubbleIndex = receiverBubbleIndex
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), shape = CircleShape) {
            Text(text = date, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun SwipeToReply(onReply: () -> Unit, content: @Composable () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxSwipe = 150f
    Box(modifier = Modifier.fillMaxWidth().draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta -> offsetX = (offsetX + delta).coerceIn(0f, maxSwipe) },
        onDragStopped = { 
            if (offsetX > maxSwipe * 0.6f) onReply()
            offsetX = 0f 
        }
    )) {
        if (offsetX > 20f) {
            Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, 
                 modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).alpha((offsetX/maxSwipe).coerceIn(0f, 1f)))
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    viewModel: ChatViewModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onReplySwipe: () -> Unit,
    senderBubbleIndex: Int,
    receiverBubbleIndex: Int
) {
    var showReactions by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            // Floating Reaction Pill Overlay
            AnimatedVisibility(visible = showReactions, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        listOf("👍", "❤️", "😂", "😮", "😢", "🔥").forEach { emoji ->
                            Text(emoji, modifier = Modifier.padding(horizontal = 8.dp).clickable { 
                                viewModel.toggleReaction(message, emoji)
                                showReactions = false
                            }, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = { if (isSelectionMode) viewModel.toggleMessageSelection(message.messageId) },
                        onLongClick = { 
                            if (!isSelectionMode) {
                                showReactions = true 
                                viewModel.toggleMessageSelection(message.messageId)
                            }
                        }
                    ),
                shape = if (isMe) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    isMe -> getBubbleColor(senderBubbleIndex, true)
                    else -> getBubbleColor(receiverBubbleIndex, false)
                },
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // WhatsApp-Style Replied message highlight
                    if (message.replyToMessageId != null) {
                        val replyMsg = viewModel.messages.value.find { it.messageId == message.replyToMessageId }
                        val replyText = replyMsg?.decryptedText ?: "Original Message"
                        val replyColor = if (isMe) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(4.dp)).background(replyColor).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                             Box(modifier = Modifier.width(3.dp).height(24.dp).background(if (isMe) Color.White else MaterialTheme.colorScheme.primary, CircleShape))
                             Spacer(modifier = Modifier.width(8.dp))
                             Column {
                                 Text(if (replyMsg?.senderId == viewModel.getCurrentUserId()) "You" else "Peer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isMe) Color.White else MaterialTheme.colorScheme.primary)
                                 Text(replyText, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                             }
                        }
                    }

                    if (message.fileUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(message.fileName ?: "File", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(
                            text = message.decryptedText ?: "Decrypting...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(modifier = Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                         Text(
                             SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp.toDate()),
                             style = MaterialTheme.typography.labelSmall,
                             color = LocalContentColor.current.copy(alpha = 0.7f)
                         )
                         if (isMe) {
                             Spacer(Modifier.width(4.dp))
                             Icon(
                                 if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Check,
                                 null, modifier = Modifier.size(12.dp)
                             )
                         }
                    }
                }
            }

            // Reactions List
            if (message.reactions.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.values.distinct().forEach { emoji ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
                            Text(emoji, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun getBubbleColor(index: Int, isMe: Boolean): Color {
    val defaultMe = com.example.entangle.ui.theme.ElectricBlue
    val defaultPeer = Color(0xFF374151) // Slate 700ish
    
    val color = when (index) {
        1 -> com.example.entangle.ui.theme.BubbleBlue
        2 -> com.example.entangle.ui.theme.BubbleGreen
        3 -> com.example.entangle.ui.theme.BubbleSlate
        4 -> com.example.entangle.ui.theme.BubblePurple
        5 -> com.example.entangle.ui.theme.BubbleTeal
        6 -> com.example.entangle.ui.theme.BubbleCarbon
        else -> if (isMe) defaultMe else defaultPeer
    }
    return color
}

class ChatViewModelFactory(private val application: android.app.Application, private val peerUid: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(application, peerUid) as T
}

@Composable
fun ChatBackground(index: Int, isDark: Boolean) {
    val bgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF5F5F5)
    
    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        when (index) {
            1 -> { // Obsidian
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black, bgColor))
                    )
                )
            }
            2 -> { // Midnight
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF001219).copy(alpha = 0.5f), bgColor),
                            radius = 2000f
                        )
                    )
                )
            }
            3 -> { // Circuit
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pathColor = Color.White.copy(alpha = 0.05f)
                    for (i in 0..size.width.toInt() step 60) {
                        drawLine(pathColor, start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(i.toFloat(), size.height), strokeWidth = 1f)
                    }
                    for (i in 0..size.height.toInt() step 100) {
                        drawLine(pathColor, start = androidx.compose.ui.geometry.Offset(0f, i.toFloat()), end = androidx.compose.ui.geometry.Offset(size.width, i.toFloat()), strokeWidth = 1f)
                        drawCircle(pathColor, radius = 4f, center = androidx.compose.ui.geometry.Offset(60f, i.toFloat()))
                    }
                }
            }
            4 -> { // Grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color.White.copy(alpha = 0.03f)
                    for (i in 0..size.width.toInt() step 40) {
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(i.toFloat(), size.height))
                    }
                    for (i in 0..size.height.toInt() step 40) {
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, i.toFloat()), end = androidx.compose.ui.geometry.Offset(size.width, i.toFloat()))
                    }
                }
            }
            5 -> { // Mesh
                Box(
                    modifier = Modifier.fillMaxSize().alpha(0.1f).background(
                        Brush.linearGradient(
                            listOf(Color.Gray, Color.Transparent, Color.DarkGray),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(100f, 100f),
                            tileMode = androidx.compose.ui.graphics.TileMode.Repeated
                        )
                    )
                )
            }
            6 -> { // Noise
                // Simulated noise with small dots
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dotColor = Color.Gray.copy(alpha = 0.1f)
                    val random = java.util.Random(42)
                    repeat(2000) {
                        drawCircle(dotColor, radius = 1f, center = androidx.compose.ui.geometry.Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
                    }
                }
            }
            7 -> { // Emerald style
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF004B23).copy(alpha = 0.2f), bgColor))
                    )
                )
            }
            8 -> { // Steel
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val steelColor = Color.White.copy(alpha = 0.04f)
                    for (i in 0..size.height.toInt() step 10) {
                        drawLine(steelColor, start = androidx.compose.ui.geometry.Offset(0f, i.toFloat()), end = androidx.compose.ui.geometry.Offset(size.width, i.toFloat()))
                    }
                }
            }
            9 -> { // Carbon
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val carbonColor = Color.White.copy(alpha = 0.05f)
                    for (i in -size.height.toInt()..size.width.toInt() step 20) {
                        drawLine(carbonColor, start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(i.toFloat() + size.height, size.height))
                    }
                }
            }
        }
    }
}
