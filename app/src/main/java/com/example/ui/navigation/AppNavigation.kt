package com.example.ui.navigation

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.audio.AudioPlaybackHelper
import com.example.audio.AudioRecorderHelper
import com.example.data.ai.SmartAiService
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.remote.ChatRepository
import com.example.data.remote.GUEST_USER_ID
import com.example.network.ConnectivityObserver
import com.example.ui.components.ConversationDrawerContent
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

const val ROUTE_CHAT = "chat"
const val ROUTE_IMAGE_STUDIO = "image_studio"
const val ROUTE_LIVE_VOICE = "live_voice"
const val ROUTE_SETTINGS = "settings"

@Composable
fun AppNavigation() {
    SmartAiWorkspace(currentUserId = GUEST_USER_ID)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SmartAiWorkspace(
    currentUserId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val connectivityObserver = remember(context) { ConnectivityObserver(context) }
    DisposableEffect(connectivityObserver) {
        onDispose { connectivityObserver.unregister() }
    }

    val aiService = remember { SmartAiService() }
    val settingsStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val audioRecorder = remember(context) { AudioRecorderHelper(context.applicationContext) }
    val audioPlayback = remember(context) { AudioPlaybackHelper(context.applicationContext) }

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = AppDatabase.getInstance(app).chatDao()
                ChatViewModel(
                    repository = ChatRepository(dao, currentUserId),
                    aiService = aiService,
                    settingsDataStore = settingsStore,
                    audioRecorder = audioRecorder,
                    audioPlayback = audioPlayback,
                    connectivityObserver = connectivityObserver
                )
            }
        }
    )

    val imageStudioViewModel: ImageStudioViewModel = viewModel(
        key = "image_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ImageStudioViewModel(
                    aiService = aiService,
                    connectivityObserver = connectivityObserver
                )
            }
        }
    )

    val liveVoiceViewModel: LiveVoiceViewModel = viewModel(
        key = "live_$currentUserId",
        factory = viewModelFactory {
            initializer {
                LiveVoiceViewModel(
                    aiService = aiService,
                    settingsDataStore = settingsStore,
                    connectivityObserver = connectivityObserver
                )
            }
        }
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = AppDatabase.getInstance(app).chatDao()
                SettingsViewModel(
                    currentUserId = currentUserId,
                    settingsDataStore = settingsStore,
                    chatDao = dao
                )
            }
        }
    )

    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by chatViewModel.activeConversationId.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        BackHandler(enabled = !isWideScreen && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        val drawerContent: @Composable () -> Unit = {
            ConversationDrawerContent(
                conversations = conversations,
                activeConversationId = activeConvId,
                onNewChat = {
                    chatViewModel.startNewChat()
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(ROUTE_CHAT) { inclusive = false }
                        launchSingleTop = true
                    }
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    chatViewModel.selectConversation(id)
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(ROUTE_CHAT) { inclusive = false }
                        launchSingleTop = true
                    }
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = { id, title ->
                    chatViewModel.renameConversation(id, title)
                },
                onTogglePin = { id, pinned ->
                    chatViewModel.togglePinned(id, pinned)
                },
                onDeleteConversation = { id ->
                    chatViewModel.deleteConversation(id)
                },
                onClearAllHistory = {
                    chatViewModel.clearAllHistory()
                },
                onOpenImageStudio = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ROUTE_IMAGE_STUDIO) { launchSingleTop = true }
                },
                onOpenLiveVoice = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ROUTE_LIVE_VOICE) { launchSingleTop = true }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                }
            )
        }

        val navHost: @Composable () -> Unit = {
            NavHost(
                navController = navController,
                startDestination = ROUTE_CHAT,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(ROUTE_CHAT) {
                    ChatScreen(
                        viewModel = chatViewModel,
                        showDrawerButton = !isWideScreen,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenImageStudio = {
                            navController.navigate(ROUTE_IMAGE_STUDIO) { launchSingleTop = true }
                        },
                        onOpenLiveVoice = {
                            navController.navigate(ROUTE_LIVE_VOICE) { launchSingleTop = true }
                        },
                        onOpenSettings = {
                            navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                        }
                    )
                }
                composable(ROUTE_IMAGE_STUDIO) {
                    ImageStudioScreen(
                        viewModel = imageStudioViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(ROUTE_LIVE_VOICE) {
                    LiveVoiceScreen(
                        viewModel = liveVoiceViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(300.dp)) {
                        drawerContent()
                    }
                },
                content = navHost
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(310.dp)) {
                        drawerContent()
                    }
                },
                content = navHost
            )
        }
    }
}
