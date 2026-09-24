package ir.chardivari.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AgentRepository
import ir.chardivari.core.marketplace.SellerListingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentFilesViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<SellerListingItem>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<SellerListingItem>>> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("agent_files"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = agentRepository.myFiles()) {
                is AppResult.Success -> UiState.Content(result.data)
                AppResult.Empty -> UiState.Empty
                is AppResult.Failure -> UiState.Error(result.error)
            }
        }
    }
}
