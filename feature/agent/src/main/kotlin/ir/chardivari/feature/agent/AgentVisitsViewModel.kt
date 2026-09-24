package ir.chardivari.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AgentRepository
import ir.chardivari.core.marketplace.AgentVisit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Client-side visit transitions (Phase 6 owns scheduling UX; server has no
 * transition map — parties may set any valid status under RLS).
 */
internal object VisitTransitions {
    private val allowed: Map<String, Set<String>> = mapOf(
        "REQUESTED" to setOf("CONFIRMED", "CANCELLED"),
        "CONFIRMED" to setOf("COMPLETED", "CANCELLED"),
        "RESCHEDULED" to setOf("CONFIRMED", "CANCELLED"),
    )

    fun canTransition(from: String, to: String): Boolean = to in allowed[from].orEmpty()

    fun available(status: String): List<String> = allowed[status].orEmpty().toList()
}

internal fun visitStatusLabel(status: String): String = when (status) {
    "REQUESTED" -> "در انتظار تأیید"
    "CONFIRMED" -> "تأییدشده"
    "RESCHEDULED" -> "زمان‌بندی مجدد"
    "COMPLETED" -> "انجام‌شده"
    "CANCELLED" -> "لغوشده"
    else -> status
}

data class AgentVisitsUi(
    val visits: List<AgentVisit> = emptyList(),
    val busyIds: Set<String> = emptySet(),
    val message: String? = null,
)

@HiltViewModel
class AgentVisitsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AgentVisitsUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<AgentVisitsUi>> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("agent_visits"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = agentRepository.visits()) {
                is AppResult.Success -> UiState.Content(AgentVisitsUi(visits = result.data))
                AppResult.Empty -> UiState.Content(AgentVisitsUi())
                is AppResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    fun consumeMessage() {
        update { it.copy(message = null) }
    }

    fun setStatus(visitId: String, to: String) {
        val state = contentOrNull() ?: return
        if (visitId in state.busyIds) return
        val visit = state.visits.find { it.id == visitId } ?: return
        if (!VisitTransitions.canTransition(visit.status, to)) {
            update { it.copy(message = "این تغییر وضعیت مجاز نیست") }
            return
        }
        update { it.copy(busyIds = it.busyIds + visitId, message = null) }
        viewModelScope.launch {
            when (agentRepository.setVisitStatus(visitId, to)) {
                is AppResult.Success, AppResult.Empty -> {
                    update { current ->
                        current.copy(
                            busyIds = current.busyIds - visitId,
                            visits = current.visits.map {
                                if (it.id == visitId) it.copy(status = to) else it
                            },
                            message = "وضعیت بازدید به‌روزرسانی شد",
                        )
                    }
                }
                is AppResult.Failure -> {
                    update {
                        it.copy(
                            busyIds = it.busyIds - visitId,
                            message = "تغییر وضعیت انجام نشد",
                        )
                    }
                }
            }
        }
    }

    private fun contentOrNull(): AgentVisitsUi? = (_uiState.value as? UiState.Content)?.data

    private fun update(transform: (AgentVisitsUi) -> AgentVisitsUi) {
        val state = _uiState.value
        if (state is UiState.Content) {
            _uiState.value = UiState.Content(transform(state.data))
        }
    }
}
