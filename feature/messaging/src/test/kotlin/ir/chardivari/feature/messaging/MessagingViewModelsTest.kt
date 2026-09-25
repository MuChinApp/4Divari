package ir.chardivari.feature.messaging

import app.cash.turbine.test
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AppNotification
import ir.chardivari.core.marketplace.ChatMessage
import ir.chardivari.core.marketplace.ChatRepository
import ir.chardivari.core.marketplace.ConversationItem
import ir.chardivari.core.marketplace.NotificationsRepository
import ir.chardivari.core.network.SessionTokenProvider
import ir.chardivari.core.marketplace.MessageDto
import ir.chardivari.core.marketplace.RealtimeEvent
import ir.chardivari.core.marketplace.RealtimeSubscription
import ir.chardivari.core.marketplace.RealtimeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagingViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow
        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private class FakeSession : SessionTokenProvider {
        var uid: String? = "user-1"
        var token: String? = "jwt-token"
        override fun currentUserId(): String? = uid
        override fun accessToken(): String? = token
    }

    private class FakeChat(
        var conversationsResult: AppResult<List<ConversationItem>> =
            AppResult.Success(listOf(ConversationItemFactory.item())),
        var messagesResult: AppResult<List<ChatMessage>> = AppResult.Empty,
        var sendResult: AppResult<ChatMessage> = AppResult.Empty,
        var markReadCount: Int = 0,
    ) : ChatRepository {
        var lastSended: Pair<String, String>? = null
        override suspend fun conversations() = conversationsResult
        override suspend fun startConversation(listingId: String): AppResult<String> =
            AppResult.Success("c1")
        override suspend fun messages(conversationId: String) = messagesResult
        override suspend fun sendMessage(
            conversationId: String,
            body: String,
        ): AppResult<ChatMessage> {
            lastSended = conversationId to body
            return sendResult
        }
        override suspend fun markRead(conversationId: String): AppResult<Unit> {
            markReadCount++
            return AppResult.Success(Unit)
        }
    }

    /** Factory to keep FakeChat's default constructor readable. */
    private object ConversationItemFactory {
        fun item() = ConversationItem(
            conversationId = "c1",
            listingId = "l1",
            counterpartId = "u2",
            unreadCount = 0,
            lastMessageBody = null,
            lastMessageAt = null,
            listingStatus = "ACTIVE",
            dealType = null,
            priceRial = null,
            depositRial = null,
            rentRial = null,
            address = null,
            areaSqm = null,
        )
    }

    private class FakeRealtime : RealtimeClient {
        var lastListener: ((RealtimeEvent) -> Unit)? = null
        var subscribeCount = 0
        override fun subscribeMessages(
            conversationId: String,
            onEvent: (RealtimeEvent) -> Unit,
        ): RealtimeSubscription {
            subscribeCount++
            lastListener = onEvent
            return RealtimeSubscription { }
        }
    }

    private class FakeNotifications(
        var result: AppResult<List<AppNotification>> = AppResult.Success(
            listOf(
                AppNotification(
                    id = "n1",
                    type = "new_message",
                    body = "پیام جدید",
                    data = null,
                    isRead = false,
                    createdAt = "2026-01-01T10:00:00+00:00",
                ),
            ),
        ),
        var markReadIds: MutableList<String> = mutableListOf(),
    ) : NotificationsRepository {
        override suspend fun notifications() = result
        override suspend fun markRead(notificationId: String): AppResult<Unit> {
            markReadIds += notificationId
            return AppResult.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun conversations_success_emitsContent() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val vm = ConversationsViewModel(FakeChat(), tracker)
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Content)
            cancel()
        }
        assertTrue(tracker.recorded.any { it.name == "screen_view" })
    }

    @Test
    fun conversations_empty_emitsEmpty() = runTest(dispatcher.scheduler) {
        val vm = ConversationsViewModel(
            FakeChat(conversationsResult = AppResult.Empty),
            RecordingTracker(),
        )
        vm.uiState.test {
            assertEquals(UiState.Empty, expectMostRecentItem())
            cancel()
        }
    }

    @Test
    fun conversations_failure_emitsError() = runTest(dispatcher.scheduler) {
        val vm = ConversationsViewModel(
            FakeChat(conversationsResult = AppResult.Failure(AppError.Server)),
            RecordingTracker(),
        )
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Error)
            cancel()
        }
    }

    @Test
    fun notifications_markRead_flipsLocalAndCallsServer() = runTest(dispatcher.scheduler) {
        val notifications = FakeNotifications()
        val vm = NotificationsViewModel(notifications, RecordingTracker())

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        vm.markRead("n1")

        val content = vm.uiState.value as UiState.Content
        assertTrue(content.data.first { it.id == "n1" }.isRead)
        val join = System.currentTimeMillis() + 2000
        while (notifications.markReadIds.isEmpty() && System.currentTimeMillis() < join) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals(listOf("n1"), notifications.markReadIds)
    }

    @Test
    fun thread_init_loadsMessagesAndMarksRead() = runTest(dispatcher.scheduler) {
        val message = MessageDto(
            id = "m1",
            conversationId = "c1",
            senderId = "user-2",
            body = "سلام",
            attachmentPath = null,
            sentAt = "2026-01-01T10:00:00+00:00",
            deletedAt = null,
        )
        val chat = FakeChat(
            messagesResult = AppResult.Success(
                listOf(message.toChatMessageForTest("user-1")),
            ),
        )
        val vm = threadVm(chat)
        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        val content = vm.uiState.value as UiState.Content
        assertEquals(1, content.data.messages.size)
        assertFalse(content.data.messages.first().isMine)
        val join = System.currentTimeMillis() + 2000
        while (chat.markReadCount == 0 && System.currentTimeMillis() < join) {
            kotlinx.coroutines.delay(10)
        }
        assertTrue(chat.markReadCount > 0)
    }

    @Test
    fun thread_send_success_appendsAndClearsInput() = runTest(dispatcher.scheduler) {
        val sent = MessageDto(
            id = "m9",
            conversationId = "c1",
            senderId = "user-1",
            body = "درود",
            attachmentPath = null,
            sentAt = "2026-01-01T11:00:00+00:00",
            deletedAt = null,
        ).toChatMessageForTest("user-1")
        val chat = FakeChat(sendResult = AppResult.Success(sent))
        val vm = threadVm(chat)

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        vm.setInput("درود")
        vm.send()

        val join = System.currentTimeMillis() + 2000
        while ((vm.uiState.value as? UiState.Content)?.data?.input != "" &&
            System.currentTimeMillis() < join
        ) {
            kotlinx.coroutines.delay(10)
        }
        val content = vm.uiState.value as UiState.Content
        assertEquals("", content.data.input)
        assertEquals("c1", chat.lastSended?.first)
        assertTrue(content.data.messages.any { it.id == "m9" })
        assertFalse(content.data.sending)
    }

    @Test
    fun thread_send_failure_setsErrorAndKeepsInput() = runTest(dispatcher.scheduler) {
        val chat = FakeChat(sendResult = AppResult.Failure(AppError.Server))
        val vm = threadVm(chat)

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        vm.setInput("متن")
        vm.send()

        val join = System.currentTimeMillis() + 2000
        while ((vm.uiState.value as? UiState.Content)?.data?.sendError == null &&
            System.currentTimeMillis() < join
        ) {
            kotlinx.coroutines.delay(10)
        }
        val content = vm.uiState.value as UiState.Content
        assertEquals("متن", content.data.input)
        assertNotNullSendError(content.data.sendError)
    }

    @Test
    fun thread_realtimeEvent_appendsDeduplicated() = runTest(dispatcher.scheduler) {
        val chat = FakeChat()
        val realtime = FakeRealtime()
        val vm = threadVm(chat, realtime)

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }

        val record = kotlinx.serialization.json.Json.parseToJsonElement(
            """
            {"id":"m5","conversation_id":"c1","sender_id":"user-2",
             "body":"سلام دوباره","sent_at":"2026-01-01T12:00:00+00:00","deleted_at":null}
            """.trimIndent(),
        ) as kotlinx.serialization.json.JsonObject

        vm.onRealtimeEvent(RealtimeEvent.MessageInserted(record))
        vm.onRealtimeEvent(RealtimeEvent.MessageInserted(record))

        val content = vm.uiState.value as UiState.Content
        assertEquals(1, content.data.messages.count { it.id == "m5" })
        assertEquals(1, realtime.subscribeCount)
    }

    @Test
    fun thread_send_emptyInput_doesNothing() = runTest(dispatcher.scheduler) {
        val chat = FakeChat()
        val vm = threadVm(chat)
        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        vm.setInput("   ")
        vm.send()
        assertNull(chat.lastSended)
    }

    private fun assertNotNullSendError(error: String?) {
        assertTrue(error != null)
    }

    private fun threadVm(
        chat: FakeChat,
        realtime: FakeRealtime = FakeRealtime(),
    ): ThreadViewModel = ThreadViewModel(
        savedStateHandle = androidx.lifecycle.SavedStateHandle(
            mapOf("conversationId" to "c1"),
        ),
        chat = chat,
        realtime = realtime,
        session = FakeSession(),
        analytics = RecordingTracker(),
    )
}

private fun MessageDto.toChatMessageForTest(uid: String): ChatMessage =
    ir.chardivari.core.marketplace.toChatMessage(this, uid)
