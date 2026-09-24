package ir.chardivari.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AgentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Access gate for the agent shell — never guesses the role. */
sealed interface AgentGate {
    data object Loading : AgentGate
    data object NotLoggedIn : AgentGate
    data object NotAgent : AgentGate
    data class Ready(val displayName: String?) : AgentGate
}

@HiltViewModel
class AgentGateViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AgentGate>>(UiState.Loading)
    val uiState: StateFlow<UiState<AgentGate>> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("agent"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = agentRepository.isAgent()) {
                AppResult.Empty -> _uiState.value = UiState.Content(AgentGate.NotAgent)
                is AppResult.Failure -> {
                    _uiState.value = when (result.error) {
                        AppError.Unauthorized -> UiState.Content(AgentGate.NotLoggedIn)
                        else -> UiState.Error(result.error)
                    }
                }
                is AppResult.Success -> {
                    if (!result.data) {
                        _uiState.value = UiState.Content(AgentGate.NotAgent)
                    } else {
                        val name = when (val profile = agentRepository.profile()) {
                            is AppResult.Success -> profile.data.displayName
                            else -> null
                        }
                        _uiState.value = UiState.Content(AgentGate.Ready(displayName = name))
                    }
                }
            }
        }
    }
}
