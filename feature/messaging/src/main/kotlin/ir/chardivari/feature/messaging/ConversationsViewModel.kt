package ir.chardivari.feature.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.ChatRepository
import ir.chardivari.core.marketplace.ConversationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<ConversationItem>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ConversationItem>>> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("messages"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = chat.conversations()) {
                is AppResult.Success -> UiState.Content(result.data)
                AppResult.Empty -> UiState.Empty
                is AppResult.Failure -> UiState.Error(result.error)
            }
        }
    }
}
