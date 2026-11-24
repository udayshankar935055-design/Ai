package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiMode
import com.example.data.local.AppDatabase
import com.example.data.remote.ChatRepository
import com.example.data.remote.GUEST_USER_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(db.chatDao(), GUEST_USER_ID)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart AI", appName)
    }

    @Test
    fun `guest mode stores and deletes local chat history without sign in`() = runTest {
        val convId = repository.createConversation("New Chat", AiMode.GENERAL).getOrThrow()

        repository.addMessage(
            conversationId = convId,
            role = "user",
            text = "What is photosynthesis?",
            modelId = AiMode.GENERAL.modelId,
            updatedTitleIfFirst = "What is photosynthesis?",
            newMessageCount = 1L
        ).getOrThrow()

        repository.addMessage(
            conversationId = convId,
            role = "model",
            text = "Photosynthesis is how plants convert sunlight into energy.",
            modelId = AiMode.GENERAL.modelId,
            newMessageCount = 2L
        ).getOrThrow()

        val conversations = repository.observeLocalConversations().first()
        assertEquals(1, conversations.size)
        assertEquals("What is photosynthesis?", conversations.first().title)
        assertEquals(2L, conversations.first().messageCount)

        val messages = repository.observeLocalMessages(convId).first()
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("model", messages[1].role)

        repository.renameConversation(convId, "Biology Notes").getOrThrow()
        assertEquals("Biology Notes", repository.observeLocalConversations().first().first().title)

        repository.clearAllLocalHistory().getOrThrow()
        assertTrue(repository.observeLocalConversations().first().isEmpty())
        assertTrue(repository.observeLocalMessages(convId).first().isEmpty())
    }

    @Test
    fun `export conversation formats and writes text file cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val convId = repository.createConversation("Quantum Notes!", AiMode.DEEP_THINKING).getOrThrow()
        repository.addMessage(
            conversationId = convId,
            role = "user",
            text = "What is a qubit?",
            modelId = AiMode.DEEP_THINKING.modelId,
            newMessageCount = 1L
        ).getOrThrow()
        repository.addMessage(
            conversationId = convId,
            role = "model",
            text = "A qubit can exist in a superposition of 0 and 1.",
            thoughtText = "Keep analogy simple and accurate.",
            modelId = AiMode.DEEP_THINKING.modelId,
            sources = listOf("https://example.org/quantum"),
            newMessageCount = 2L
        ).getOrThrow()

        val messages = repository.observeLocalMessages(convId).first()
        val fileName = com.example.utils.buildSuggestedTxtFileName("Quantum Notes!", 1700000000000L)
        assertTrue(fileName.startsWith("Quantum_Notes_"))
        assertTrue(fileName.endsWith(".txt"))

        val formatted = com.example.utils.formatConversationAsText("Quantum Notes!", messages)
        assertTrue(formatted.contains("Title: Quantum Notes!"))
        assertTrue(formatted.contains("You:"))
        assertTrue(formatted.contains("What is a qubit?"))
        assertTrue(formatted.contains("SMART AI (${AiMode.DEEP_THINKING.modelId}):"))
        assertTrue(formatted.contains("Reasoning Summary"))
        assertTrue(formatted.contains("https://example.org/quantum"))

        val tempFile = java.io.File(context.cacheDir, fileName)
        val uri = android.net.Uri.fromFile(tempFile)
        val writeResult = com.example.utils.writeTextToUri(context, uri, formatted)
        assertTrue(writeResult.isSuccess)
        assertEquals(formatted, tempFile.readText(Charsets.UTF_8))
        tempFile.delete()
    }

    @Test
    fun `share response builds valid ACTION_SEND text intent`() {
        val sampleText = "Photosynthesis converts light energy into chemical energy."
        val intent = com.example.utils.buildShareTextIntent(sampleText)
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(sampleText, intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
    }
}
