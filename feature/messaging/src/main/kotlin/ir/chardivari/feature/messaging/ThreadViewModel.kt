package ir.chardivari.feature.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.ChatMessage
import ir.chardivari.core.marketplace.ChatRepository
import ir.chardivari.core.marketplace.MessageDto
import ir.chardivari.core.marketplace.RealtimeClient
import ir.chardivari.core.marketplace.RealtimeEvent
import ir.chardivari.core.marketplace.RealtimeSubscription
import ir.chardivari.core.marketplace.toChatMessage
import ir.chardivari.core.network.SessionTokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class ThreadUi(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val sendError: String? = null,
    val realtimeActive: Boolean = false,
)

/** One-shot navigation/login events from the thread screen. */
sealed interface ThreadEvent {
    data object LoginRequired : ThreadEvent
}

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    private val realtime: RealtimeClient,
    private val session: SessionTokenProvider,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val conversationId: String =
        checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow<UiState<ThreadUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<ThreadUi>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ThreadEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ThreadEvent> = _events.asSharedFlow()

    private var subscription: RealtimeSubscription? = null
    private var pollJob: Job? = null

    init {
        analytics.track(AnalyticsEvent.ScreenView("messages_thread"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val current = content()
            val messages = when (val result = chat.messages(conversationId)) {
                is AppResult.Success -> result.data
                AppResult.Empty -> emptyList()
                is AppResult.Failure -> {
                    _uiState.value = UiState.Error(result.error)
                    return@launch
                }
            }
            val state = current ?: ThreadUi()
            _uiState.value = UiState.Content(state.copy(messages = messages))
            chat.markRead(conversationId)
            startRealtime()
            startPolling()
        }
    }

    fun setInput(text: String) {
        update { it.copy(input = text, sendError = null) }
    }

    fun send() {
        val state = content() ?: return
        val body = state.input.trim()
        if (body.isEmpty() || state.sending) return
        update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            when (val result = chat.sendMessage(conversationId, body)) {
                is AppResult.Success -> {
                    val sent = result.data
                    update { current ->
                        current.copy(
                            sending = false,
                            input = "",
                            messages = if (current.messages.any { it.id == sent.id }) {
                                current.messages
                            } else {
                                current.messages + sent
                            },
                        )
                    }
                    analytics.track(
                        AnalyticsEvent.MessageSent(conversationId = conversationId),
                    )
                    chat.markRead(conversationId)
                }
                AppResult.Empty -> update { it.copy(sending = false) }
                is AppResult.Failure -> {
                    if (result.error == ir.chardivari.core.common.AppError.Unauthorized) {
                        _events.tryEmit(ThreadEvent.LoginRequired)
                    }
                    update {
                        it.copy(
                            sending = false,
                            sendError = "ارسال پیام انجام نشد؛ دوباره تلاش کنید",
                        )
                    }
                }
            }
        }
    }

    /** Called by the screen for realtime frames (kept internal for tests). */
    fun onRealtimeEvent(event: RealtimeEvent) {
        when (event) {
            RealtimeEvent.Connected -> update { it.copy(realtimeActive = true) }
            RealtimeEvent.Disconnected -> update { it.copy(realtimeActive = false) }
            is RealtimeEvent.MessageInserted -> appendFromRecord(event.record)
        }
    }

    internal fun appendFromRecord(record: JsonObject) {
        val dto = try {
            Json.decodeFromString(MessageDto.serializer(), record.toString())
        } catch (_: Exception) {
            return
        }
        val message = dto.toChatMessage(session.currentUserId())
        update { state ->
            if (state.messages.any { it.id == message.id }) {
                state
            } else {
                state.copy(messages = state.messages + message)
            }
        }
        viewModelScope.launch { chat.markRead(conversationId) }
    }

    private fun startRealtime() {
        subscription?.close()
        subscription = realtime.subscribeMessages(conversationId) { event ->
            onRealtimeEvent(event)
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                when (val result = chat.messages(conversationId)) {
                    is AppResult.Success -> applyMessages(result.data)
                    AppResult.Empty -> applyMessages(emptyList())
                    is AppResult.Failure -> Unit // transient — next tick
                }
            }
        }
    }

    private fun applyMessages(messages: List<ChatMessage>) {
        update { state ->
            // Realtime may have appended newer rows mid-poll; merge by id.
            val merged = (state.messages + messages)
                .distinctBy { it.id }
                .sortedBy { it.sentAt }
            state.copy(messages = merged)
        }
    }

    override fun onCleared() {
        subscription?.close()
        subscription = null
        pollJob?.cancel()
        pollJob = null
        super.onCleared()
    }

    private fun content(): ThreadUi? = (_uiState.value as? UiState.Content)?.data

    private fun update(transform: (ThreadUi) -> ThreadUi) {
        val state = _uiState.value
        if (state is UiState.Content) {
            _uiState.value = UiState.Content(transform(state.data))
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 10_000L
    }
}
