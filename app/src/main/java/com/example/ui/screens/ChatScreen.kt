package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AiMode
import com.example.data.ai.PendingAttachment
import com.example.data.local.LocalMessageEntity
import com.example.ui.components.MarkdownText
import com.example.ui.components.MessageBubble
import com.example.ui.components.TypingIndicator
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.viewmodel.ChatViewModel
import com.example.utils.buildSuggestedTxtFileName
import com.example.utils.formatConversationAsText
import com.example.utils.loadScaledBitmapFromUri
import com.example.utils.loadVideoPayloadFromUri
import com.example.utils.queryDisplayName
import com.example.utils.shareConversationTextFile
import com.example.utils.writeTextToUri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenLiveVoice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val streamingState by viewModel.streamingState.collectAsStateWithLifecycle()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val ttsLoadingId by viewModel.ttsLoadingMessageId.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    val activeTitle = remember(activeConvId, conversations) {
        conversations.firstOrNull { it.id == activeConvId }?.title ?: "SMART AI"
    }

    var overflowExpanded by remember { mutableStateOf(false) }
    var mediaMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    loadScaledBitmapFromUri(context, uri)
                        .onSuccess { bmp ->
                            val label = queryDisplayName(context, uri) ?: "Photo"
                            viewModel.attachImage(bmp, label)
                        }
                        .onFailure { err ->
                            snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to load image")
                        }
                }
            }
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    loadVideoPayloadFromUri(context, uri)
                        .onSuccess { video ->
                            viewModel.attachVideo(video.bytes, video.mimeType, video.displayName)
                        }
                        .onFailure { err ->
                            snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to load video")
                        }
                }
            }
        }
    )

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.toggleVoiceRecording()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.mic_permission_required))
                }
            }
        }
    )

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                val exportedText = formatConversationAsText(activeTitle, messages)
                scope.launch {
                    writeTextToUri(context, uri, exportedText)
                        .onSuccess {
                            snackbarHostState.showSnackbar(context.getString(R.string.export_success))
                        }
                        .onFailure { err ->
                            snackbarHostState.showSnackbar(
                                err.localizedMessage ?: "Failed to export conversation."
                            )
                        }
                }
            }
        }
    )

    LaunchedEffect(messages.size, streamingState.isGenerating) {
        val targetIndex = (messages.size + if (streamingState.isGenerating) 1 else 0) - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(streamingState.answerText.length, streamingState.thoughtText.length) {
        if (streamingState.isGenerating) {
            val targetIndex = messages.size
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("history_drawer_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.chat_history))
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.smart_ai_icon),
                            contentDescription = "Smart AI Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                        )
                        Column {
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selectedMode.label} • ${selectedMode.modelId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenLiveVoice,
                        modifier = Modifier.testTag("top_live_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.live_voice),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.startNewChat() },
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = stringResource(R.string.new_chat)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag("chat_overflow_button")
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Image Studio") },
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                                modifier = Modifier.testTag("overflow_image_studio_menu_item"),
                                onClick = {
                                    overflowExpanded = false
                                    onOpenImageStudio()
                                }
                            )
                            if (messages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_save_txt)) },
                                    leadingIcon = { Icon(Icons.Rounded.Description, null) },
                                    modifier = Modifier.testTag("export_save_txt_menu_item"),
                                    onClick = {
                                        overflowExpanded = false
                                        exportTxtLauncher.launch(buildSuggestedTxtFileName(activeTitle))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_share_txt)) },
                                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                    modifier = Modifier.testTag("export_share_txt_menu_item"),
                                    onClick = {
                                        overflowExpanded = false
                                        val fileName = buildSuggestedTxtFileName(activeTitle)
                                        val exportedText = formatConversationAsText(activeTitle, messages)
                                        scope.launch {
                                            shareConversationTextFile(context, fileName, exportedText)
                                                .onFailure { err ->
                                                    snackbarHostState.showSnackbar(
                                                        err.localizedMessage ?: "Failed to share text file."
                                                    )
                                                }
                                        }
                                    }
                                )
                            }
                            if (activeConvId != null) {
                                DropdownMenuItem(
                                    text = { Text("Clear current chat") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                    onClick = {
                                        overflowExpanded = false
                                        viewModel.clearCurrentChat()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            AnimatedVisibility(visible = !isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.offline_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !errorBanner.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(
                            text = errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (!streamingState.isGenerating && messages.lastOrNull()?.role == "user") {
                            TextButton(
                                onClick = {
                                    viewModel.dismissError()
                                    viewModel.regenerateLastResponse()
                                },
                                modifier = Modifier.testTag("error_retry_button")
                            ) {
                                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // AI Model Mode Selector Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                AiMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setAiMode(mode) },
                        label = {
                            Text(
                                text = mode.shortLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = modeIcon(mode),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("mode_chip_${mode.name.lowercase()}")
                    )
                }
            }

            // Main Chat Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !streamingState.isGenerating) {
                    WelcomeEmptyState(
                        selectedMode = selectedMode,
                        onPromptSelected = { prompt, suggestedMode ->
                            if (suggestedMode != null) {
                                viewModel.setAiMode(suggestedMode)
                            }
                            viewModel.sendMessage(prompt)
                        },
                        onOpenImageStudio = onOpenImageStudio,
                        onOpenLiveVoice = onOpenLiveVoice,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .align(Alignment.Center)
                    )
                } else {
                    val lastModelIdx = messages.indexOfLast { it.role == "model" }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp)
                            .align(Alignment.Center)
                    ) {
                        itemsIndexed(messages, key = { _, item -> item.id }) { index, msg ->
                            MessageBubble(
                                message = msg,
                                isLastModelMessage = index == lastModelIdx && !streamingState.isGenerating,
                                isTtsLoading = ttsLoadingId == msg.id,
                                isPlayingAudio = playingMessageId == msg.id,
                                onCopyMessage = { toast ->
                                    scope.launch { snackbarHostState.showSnackbar(toast) }
                                },
                                onSpeakMessage = { target ->
                                    viewModel.speakMessage(target.id, target.text)
                                },
                                onRegenerate = { viewModel.regenerateLastResponse() }
                            )
                        }

                        if (streamingState.isGenerating) {
                            item(key = "streaming_bubble") {
                                StreamingAssistantSection(
                                    answerText = streamingState.answerText,
                                    thoughtText = streamingState.thoughtText,
                                    modelId = streamingState.modelId,
                                    modeLabel = selectedMode.label
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Composer Card
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 780.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                    AnimatedVisibility(visible = pendingAttachment != null) {
                        pendingAttachment?.let { attachment ->
                            AttachmentPreviewChip(
                                attachment = attachment,
                                onClear = { viewModel.clearAttachment() },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isRecordingVoice || isTranscribing) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (isTranscribing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Transcribing audio with Gemini 3.5 Transcribe…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Listening… Tap the microphone again when finished speaking.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box {
                            IconButton(
                                onClick = { mediaMenuExpanded = true },
                                enabled = !streamingState.isGenerating,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("attach_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Attach image or video",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = mediaMenuExpanded,
                                onDismissRequest = { mediaMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Analyze Photo (Gemini 4 Neon)") },
                                    leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                    onClick = {
                                        mediaMenuExpanded = false
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Analyze Video (Gemini 4 Neon)") },
                                    leadingIcon = { Icon(Icons.Rounded.VideoLibrary, null) },
                                    onClick = {
                                        mediaMenuExpanded = false
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    }
                                )
                            }
                        }

                        TextField(
                            value = inputText,
                            onValueChange = { viewModel.setInputText(it) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.input_placeholder),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field")
                        )

                        IconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.toggleVoiceRecording()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !streamingState.isGenerating && !isTranscribing,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (isRecordingVoice) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else Color.Transparent,
                                    shape = CircleShape
                                )
                                .testTag("voice_input_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = if (isRecordingVoice) "Stop recording" else "Voice input",
                                tint = if (isRecordingVoice) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (streamingState.isGenerating) {
                            IconButton(
                                onClick = { viewModel.stopGenerating() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = CircleShape
                                    )
                                    .testTag("stop_generating_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop generating",
                                    tint = Color.White
                                )
                            }
                        } else {
                            val canSend = inputText.isNotBlank() || pendingAttachment != null
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        brush = if (canSend) {
                                            Brush.linearGradient(listOf(ElectricBlue, RoyalPurple))
                                        } else {
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                )
                                            )
                                        },
                                        shape = CircleShape
                                    )
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send message",
                                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun WelcomeEmptyState(
    selectedMode: AiMode,
    onPromptSelected: (String, AiMode?) -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenLiveVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                Image(
                    painter = painterResource(id = R.drawable.img_smart_ai_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.42f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.smart_ai_icon),
                        contentDescription = "Smart AI Emblem",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.welcome_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickActionCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Image Studio",
                subtitle = "Create & edit AI art",
                onClick = onOpenImageStudio,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Rounded.GraphicEq,
                title = "Live Voice",
                subtitle = "Real-time voice chat",
                onClick = onOpenLiveVoice,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "TRY ASKING SMART AI (${selectedMode.shortLabel.uppercase()})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        val starterPrompts = listOf(
            Triple(
                "Explain Quantum Computing simply",
                "Explain how quantum computing works in simple, everyday language with an easy analogy.",
                AiMode.GENERAL
            ),
            Triple(
                "हिन्दी में AI क्या है समझाएं",
                "आर्टिफिशियल इंटेलिजेंस (AI) क्या है और यह रोज़मर्रा की ज़िंदगी में कैसे मदद करता है? आसान हिन्दी में समझाएं।",
                AiMode.GENERAL
            ),
            Triple(
                "Deep STEM Math & Reasoning",
                "Prove step-by-step why the sum of the first n odd positive integers equals n squared, and show a Python verification.",
                AiMode.DEEP_THINKING
            ),
            Triple(
                "Search Latest Tech News",
                "What are the biggest breakthroughs in space exploration and AI this month?",
                AiMode.SEARCH
            )
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            starterPrompts.forEach { (title, prompt, suggestedMode) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPromptSelected(prompt, suggestedMode) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = modeIcon(suggestedMode),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantSection(
    answerText: String,
    thoughtText: String,
    modelId: String,
    modeLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        TypingIndicator(
            label = if (thoughtText.isNotBlank() && answerText.isBlank()) {
                "Deep reasoning ($modelId)…"
            } else {
                "$modeLabel ($modelId) is responding…"
            }
        )

        if (thoughtText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Thinking…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = thoughtText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (answerText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                MarkdownText(
                    markdown = answerText,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentPreviewChip(
    attachment: PendingAttachment,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> {
                    Image(
                        bitmap = attachment.bitmap.asImageBitmap(),
                        contentDescription = attachment.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                is PendingAttachment.VideoAttachment -> {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = attachment.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun modeIcon(mode: AiMode): ImageVector = when (mode) {
    AiMode.GENERAL -> Icons.Rounded.AutoAwesome
    AiMode.DEEP_THINKING -> Icons.Rounded.Psychology
    AiMode.FAST -> Icons.Rounded.Bolt
    AiMode.SEARCH -> Icons.Rounded.TravelExplore
    AiMode.MULTIMODAL -> Icons.Rounded.Image
}
