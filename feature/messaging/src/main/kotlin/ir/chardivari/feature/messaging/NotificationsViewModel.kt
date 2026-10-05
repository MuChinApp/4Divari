package ir.chardivari.feature.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AppNotification
import ir.chardivari.core.marketplace.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifications: NotificationsRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<AppNotification>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<AppNotification>>> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("notifications"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = notifications.notifications()) {
                is AppResult.Success -> UiState.Content(result.data)
                AppResult.Empty -> UiState.Empty
                is AppResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    fun markRead(id: String) {
        val state = _uiState.value
        if (state !is UiState.Content) return
        val target = state.data.firstOrNull { it.id == id } ?: return
        if (target.isRead) return
        // optimistic local flip — read receipt is idempotent server-side
        _uiState.value = UiState.Content(
            state.data.map { if (it.id == id) it.copy(isRead = true) else it },
        )
        viewModelScope.launch {
            notifications.markRead(id)
        }
    }
}
