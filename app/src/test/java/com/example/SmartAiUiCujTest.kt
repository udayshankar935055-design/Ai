package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlaybackHelper
import com.example.audio.AudioRecorderHelper
import com.example.data.ai.AiMode
import com.example.data.ai.GeneratedAudioData
import com.example.data.ai.ImageAspectRatio
import com.example.data.ai.ImageQualityTier
import com.example.data.ai.PendingAttachment
import com.example.data.ai.SmartAiService
import com.example.data.ai.StreamChunkUpdate
import com.example.data.local.AppDatabase
import com.example.data.local.LocalMessageEntity
import com.example.data.local.SettingsDataStore
import com.example.data.remote.ChatRepository
import com.example.data.remote.GUEST_USER_ID
import com.example.network.ConnectivityObserver
import com.example.ui.components.ConversationDrawerContent
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SmartAiTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartAiUiCujTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ChatRepository
    private lateinit var settingsStore: SettingsDataStore
    private lateinit var fakeConnectivity: FakeConnectivityObserver

    private class FakeConnectivityObserver(
        context: Context,
        initialOnline: Boolean = true
    ) : ConnectivityObserver(context) {
        val mutableOnline = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = mutableOnline
    }

    private class FakeAiService : SmartAiService() {
        var shouldThrowError = false
        var responsePrefix = "Smart AI answer"
        var callCount = 0

        override fun streamChatResponse(
            mode: AiMode,
            preferredLanguage: String,
            history: List<LocalMessageEntity>,
            userPrompt: String,
            attachment: PendingAttachment?
        ): Flow<StreamChunkUpdate> = flow {
            callCount++
            if (shouldThrowError) {
                throw IllegalStateException("Simulated API error 503")
            }
            emit(
                StreamChunkUpdate(
                    answerText = "$responsePrefix #$callCount to: $userPrompt",
                    thoughtText = if (mode == AiMode.DEEP_THINKING) "Step-by-step reasoning." else "",
                    sources = if (mode == AiMode.SEARCH) listOf("https://example.org/info") else emptyList(),
                    modelId = mode.modelId
                )
            )
        }

        override suspend fun generateOrEditImage(
            prompt: String,
            aspectRatio: ImageAspectRatio,
            qualityTier: ImageQualityTier,
            sourceBitmap: Bitmap?
        ): Bitmap {
            return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        }

        override suspend fun synthesizeSpeech(text: String, voiceName: String): GeneratedAudioData? = null
    }

    private lateinit var testDataStoreDir: java.io.File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(db.chatDao(), GUEST_USER_ID)
        testDataStoreDir = java.io.File(context.cacheDir, "test_ds_${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val testDataStore = PreferenceDataStoreFactory.create {
            java.io.File(testDataStoreDir, "settings.preferences_pb")
        }
        settingsStore = SettingsDataStore(context, testDataStore)
        fakeConnectivity = FakeConnectivityObserver(context, initialOnline = true)
    }

    @After
    fun tearDown() {
        fakeConnectivity.unregister()
        db.close()
        testDataStoreDir.deleteRecursively()
    }

    @Test
    fun `chat screen buttons send copy share export regenerate mode switch and new chat all work`() {
        val fakeAi = FakeAiService()
        val viewModel = ChatViewModel(
            repository = repository,
            aiService = fakeAi,
            settingsDataStore = settingsStore,
            audioRecorder = AudioRecorderHelper(context),
            audioPlayback = AudioPlaybackHelper(context),
            connectivityObserver = fakeConnectivity
        )

        composeRule.setContent {
            SmartAiTheme(themeMode = ThemeMode.DARK) {
                ChatScreen(
                    viewModel = viewModel,
                    showDrawerButton = true,
                    onOpenDrawer = {},
                    onOpenImageStudio = {},
                    onOpenLiveVoice = {},
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Send button starts disabled when input is empty
        composeRule.onNodeWithTag("send_message_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("voice_input_button").assertIsEnabled()

        // Switch AI mode chip to Deep Think
        composeRule.onNodeWithTag("mode_chip_deep_thinking").performClick()
        composeRule.waitForIdle()
        assertEquals(AiMode.DEEP_THINKING, viewModel.selectedMode.value)

        // Enter prompt and tap Send
        composeRule.onNodeWithTag("chat_input_field").performTextInput("Explain gravity simply")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send_message_button").assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("Smart AI answer #1 to: Explain gravity simply")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // User prompt appears in both TopAppBar title and user bubble
        assertTrue(
            composeRule.onAllNodesWithText("Explain gravity simply")
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithText("Smart AI answer #1 to: Explain gravity simply").assertIsDisplayed()

        // Copy answer button copies to Android ClipboardManager
        composeRule.onNodeWithTag("copy_answer_button").performClick()
        composeRule.waitForIdle()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "Smart AI answer #1 to: Explain gravity simply",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )

        // Share button fires Intent.ACTION_SEND chooser
        val appShadow = shadowOf(context as android.app.Application)
        composeRule.onNodeWithTag("share_answer_button").performClick()
        composeRule.waitForIdle()
        val startedShareIntent = appShadow.nextStartedActivity
        assertNotNull(startedShareIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedShareIntent.action)

        // Overflow menu exposes Save .txt and Share .txt export actions
        composeRule.onNodeWithTag("chat_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_save_txt_menu_item").assertIsDisplayed()
        composeRule.onNodeWithTag("export_share_txt_menu_item").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            appShadow.peekNextStartedActivity() != null
        }
        val exportedIntent = appShadow.nextStartedActivity
        assertNotNull(exportedIntent)
        assertEquals(Intent.ACTION_CHOOSER, exportedIntent.action)

        // Regenerate button calls AI again and updates message
        composeRule.onNodeWithTag("regenerate_answer_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("Smart AI answer #2 to: Explain gravity simply")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Smart AI answer #2 to: Explain gravity simply").assertIsDisplayed()

        // New chat button clears active view while keeping saved history
        composeRule.onNodeWithTag("new_chat_button").performClick()
        composeRule.waitForIdle()
        assertEquals(null, viewModel.activeConversationId.value)
        assertEquals(1, runBlocking { repository.observeLocalConversations().first().size })
    }

    @Test
    fun `chat screen surfaces offline and api error banners and retries on failure`() {
        val fakeAi = FakeAiService().apply { shouldThrowError = true }
        val viewModel = ChatViewModel(
            repository = repository,
            aiService = fakeAi,
            settingsDataStore = settingsStore,
            audioRecorder = AudioRecorderHelper(context),
            audioPlayback = AudioPlaybackHelper(context),
            connectivityObserver = fakeConnectivity
        )

        composeRule.setContent {
            SmartAiTheme(themeMode = ThemeMode.DARK) {
                ChatScreen(
                    viewModel = viewModel,
                    showDrawerButton = true,
                    onOpenDrawer = {},
                    onOpenImageStudio = {},
                    onOpenLiveVoice = {},
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Offline indicator appears when disconnected
        fakeConnectivity.mutableOnline.value = false
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithText(context.getString(R.string.offline_banner))
                .fetchSemanticsNodes().isNotEmpty()
        }
        fakeConnectivity.mutableOnline.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chat_input_field").performTextInput("Test retry")
        composeRule.onNodeWithTag("send_message_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("error_retry_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Simulated API error 503").assertIsDisplayed()
        composeRule.onNodeWithTag("error_retry_button").assertIsDisplayed()

        // Recover on retry
        fakeAi.shouldThrowError = false
        composeRule.onNodeWithTag("error_retry_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("Smart AI answer #2 to: Test retry")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Smart AI answer #2 to: Test retry").assertIsDisplayed()
    }

    @Test
    fun `drawer history supports rename pin search and delete conversation`() {
        val convId = runBlocking {
            repository.createConversation("Original Chat Title", AiMode.GENERAL).getOrThrow()
        }
        val fakeAi = FakeAiService()
        val viewModel = ChatViewModel(
            repository = repository,
            aiService = fakeAi,
            settingsDataStore = settingsStore,
            audioRecorder = AudioRecorderHelper(context),
            audioPlayback = AudioPlaybackHelper(context),
            connectivityObserver = fakeConnectivity
        )

        composeRule.setContent {
            DrawerTestHost(viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithText("Original Chat Title").fetchSemanticsNodes().isNotEmpty()
        }

        // Rename conversation
        composeRule.onNodeWithTag("conversation_options_button").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithText("Rename").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithTag("rename_conversation_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("rename_conversation_input").performTextReplacement("Renamed Study Chat")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithText("Renamed Study Chat").fetchSemanticsNodes().isNotEmpty()
        }

        // Pin conversation
        composeRule.onNodeWithTag("conversation_options_button").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithText("Pin to top").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Pin to top").performClick()
        runBlocking {
            var pinned = false
            repeat(30) {
                if (repository.observeLocalConversations().first().firstOrNull { it.id == convId }?.pinned == true) {
                    pinned = true
                    return@repeat
                }
                delay(50)
            }
            assertTrue("Expected conversation $convId to be pinned in Room", pinned)
        }

        // Search filter hides non-matching item, then clearing search restores it
        composeRule.onNodeWithText("Search chat history…").performTextInput("zzzz")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No matching chats.").assertIsDisplayed()
        composeRule.onNodeWithText("zzzz").performTextReplacement("")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Renamed Study Chat").assertIsDisplayed()

        // Delete conversation
        composeRule.onNodeWithTag("conversation_options_button").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithTag("confirm_delete_conversation_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("confirm_delete_conversation_button").performClick()

        composeRule.waitUntil(timeoutMillis = 3000L) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            viewModel.conversations.value.isEmpty()
        }
        assertTrue(runBlocking { repository.observeLocalConversations().first().isEmpty() })
    }

    @Test
    fun `settings screen toggles dark and light mode and deletes local history`() {
        runBlocking {
            repository.createConversation("Saved Chat", AiMode.GENERAL).getOrThrow()
        }
        val settingsViewModel = SettingsViewModel(GUEST_USER_ID, settingsStore, db.chatDao())

        composeRule.setContent {
            SmartAiTheme(themeMode = ThemeMode.DARK) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("theme_chip_light").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            settingsViewModel.settings.value.themeMode == ThemeMode.LIGHT
        }
        composeRule.onNodeWithTag("theme_chip_light").assertIsSelected()

        composeRule.waitForIdle()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        composeRule.onNodeWithTag("theme_chip_dark").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            settingsViewModel.settings.value.themeMode == ThemeMode.DARK
        }
        composeRule.onNodeWithTag("theme_chip_dark").assertIsSelected()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_speak_switch").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            settingsViewModel.settings.value.autoSpeakResponses
        }

        composeRule.onNodeWithTag("clear_all_history_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete All").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("All local chat history deleted").fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking {
            var remaining = repository.observeLocalConversations().first()
            var attempts = 0
            while (remaining.isNotEmpty() && attempts < 20) {
                delay(50)
                remaining = repository.observeLocalConversations().first()
                attempts++
            }
            assertTrue(remaining.isEmpty())
        }
    }

    @Test
    fun `image studio and live voice screens render and validate controls`() {
        val fakeAi = FakeAiService()
        val imageVm = ImageStudioViewModel(fakeAi, fakeConnectivity)
        val liveVm = LiveVoiceViewModel(fakeAi, settingsStore, fakeConnectivity)

        composeRule.setContent {
            SmartAiTheme {
                ImageStudioScreen(viewModel = imageVm, onBack = {})
            }
        }
        composeRule.waitForIdle()

        // Empty prompt shows validation error
        composeRule.onNodeWithTag("generate_image_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Please enter an image prompt or editing instruction.")
            .performScrollTo()
            .assertIsDisplayed()

        // Selecting 16:9 ratio & generating image succeeds
        composeRule.onNodeWithTag("aspect_ratio_16_9").performScrollTo().performClick()
        composeRule.onNodeWithTag("image_studio_prompt_input").performScrollTo().performTextInput("Blue crystal city")
        composeRule.onNodeWithTag("generate_image_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("Save to Gallery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Save to Gallery").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Share").performScrollTo().assertIsDisplayed()

        assertEquals(0, liveVm.transcripts.value.size)
    }

    @Test
    fun `live voice screen renders controls and handles back navigation`() {
        val fakeAi = FakeAiService()
        val liveVm = LiveVoiceViewModel(fakeAi, settingsStore, fakeConnectivity)
        var backClicked = false

        composeRule.setContent {
            SmartAiTheme {
                LiveVoiceScreen(viewModel = liveVm, onBack = { backClicked = true })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("start_live_voice_button").assertIsDisplayed()
        composeRule.onNodeWithTag("live_voice_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue(backClicked)
    }
}

@androidx.compose.runtime.Composable
private fun DrawerTestHost(viewModel: ChatViewModel) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    SmartAiTheme {
        ConversationDrawerContent(
            conversations = conversations,
            activeConversationId = activeConvId,
            onNewChat = { viewModel.startNewChat() },
            onSelectConversation = { viewModel.selectConversation(it) },
            onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
            onTogglePin = { id, pinned -> viewModel.togglePinned(id, pinned) },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onClearAllHistory = { viewModel.clearAllHistory() },
            onOpenImageStudio = {},
            onOpenLiveVoice = {},
            onOpenSettings = {}
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityStartupPhoneTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `phone startup opens chat directly and navigates drawer settings live voice and image studio`() {
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("chat_input_field").assertIsDisplayed()

        // Navigate to Settings via drawer
        activityRule.onNodeWithTag("history_drawer_button").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("drawer_settings_button").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithTag("drawer_settings_button").performScrollTo().performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("settings_back_button").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithTag("settings_back_button").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("top_live_voice_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Live Voice and back
        activityRule.onNodeWithTag("top_live_voice_button").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("live_voice_back_button").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithTag("live_voice_back_button").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("chat_overflow_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Image Studio via overflow menu and back
        activityRule.onNodeWithTag("chat_overflow_button").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("overflow_image_studio_menu_item").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithTag("overflow_image_studio_menu_item").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("image_studio_back_button").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithTag("image_studio_back_button").performClick()
        activityRule.waitUntil(timeoutMillis = 3000L) {
            activityRule.onAllNodesWithTag("chat_input_field").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithTag("chat_input_field").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w900dp-h1200dp")
class MainActivityStartupTabletTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `main activity launches directly to chat screen on tablet without login`() {
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("chat_input_field").assertIsDisplayed()
        activityRule.onNodeWithTag("send_message_button").assertIsDisplayed()
        activityRule.onNodeWithTag("voice_input_button").assertIsDisplayed()
        activityRule.onNodeWithTag("new_chat_button").assertIsDisplayed()
        activityRule.onNodeWithTag("drawer_new_chat_button").assertIsDisplayed()
        activityRule.onNodeWithTag("drawer_settings_button").assertIsDisplayed()
    }
}
