package ir.chardivari.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AdminRepository
import ir.chardivari.core.marketplace.ModerationQueue
import ir.chardivari.core.network.SessionTokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Honest gate — never renders moderation UI without an ADMIN role. */
sealed interface AdminGate {
    data object Loading : AdminGate
    data object NotLoggedIn : AdminGate
    data object NotAdmin : AdminGate
    data object Ready : AdminGate
}

sealed interface AdminGateEvent {
    data object LoginRequired : AdminGateEvent
}

@HiltViewModel
class AdminGateViewModel @Inject constructor(
    private val session: SessionTokenProvider,
    private val admin: AdminRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AdminGate>>(UiState.Content(AdminGate.Loading))
    val uiState: StateFlow<UiState<AdminGate>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AdminGateEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AdminGateEvent> = _events.asSharedFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("admin"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Content(AdminGate.Loading)
        viewModelScope.launch {
            if (session.currentUserId() == null) {
                _uiState.value = UiState.Content(AdminGate.NotLoggedIn)
                return@launch
            }
            when (val result = admin.isAdmin()) {
                is AppResult.Success -> _uiState.value = UiState.Content(
                    if (result.data) AdminGate.Ready else AdminGate.NotAdmin,
                )
                AppResult.Empty -> _uiState.value = UiState.Content(AdminGate.NotAdmin)
                is AppResult.Failure -> {
                    if (result.error == AppError.Unauthorized) {
                        _events.tryEmit(AdminGateEvent.LoginRequired)
                        _uiState.value = UiState.Content(AdminGate.NotLoggedIn)
                    } else {
                        _uiState.value = UiState.Error(result.error)
                    }
                }
            }
        }
    }

    fun requestLogin() {
        _events.tryEmit(AdminGateEvent.LoginRequired)
    }
}

data class AdminUi(
    val queue: ModerationQueue? = null,
    /** Queue item ids with an in-flight decision — disables their buttons. */
    val busyIds: Set<String> = emptySet(),
    val message: String? = null,
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val admin: AdminRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AdminUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<AdminUi>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = admin.queue()) {
                is AppResult.Success -> _uiState.value = UiState.Content(
                    AdminUi(queue = result.data),
                )
                AppResult.Empty -> _uiState.value = UiState.Content(AdminUi())
                is AppResult.Failure -> _uiState.value = UiState.Error(result.error)
            }
        }
    }

    fun resolveReport(reportId: String, status: String) {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        if (reportId in current.busyIds) return
        _uiState.value = UiState.Content(
            current.copy(busyIds = current.busyIds + reportId, message = null),
        )
        viewModelScope.launch {
            when (val result = admin.resolveReport(reportId, status)) {
                is AppResult.Success, AppResult.Empty -> {
                    analytics.track(
                        AnalyticsEvent.ModerationAction(
                            kind = "report",
                            targetId = reportId,
                            action = status,
                        ),
                    )
                    mutateQueue { queue ->
                        queue.copy(
                            reports = queue.reports.filterNot { it.id == reportId },
                            openReports = (queue.openReports - 1).coerceAtLeast(0),
                        )
                    }
                    update { it.copy(busyIds = it.busyIds - reportId, message = "گزارش بسته شد") }
                }
                is AppResult.Failure -> update {
                    it.copy(
                        busyIds = it.busyIds - reportId,
                        message = "بستن گزارش انجام نشد؛ دوباره تلاش کنید",
                    )
                }
            }
        }
    }

    fun decideVerification(listingId: String, status: String) {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        if (listingId in current.busyIds) return
        _uiState.value = UiState.Content(
            current.copy(busyIds = current.busyIds + listingId, message = null),
        )
        viewModelScope.launch {
            when (val result = admin.decideVerification(listingId, status)) {
                is AppResult.Success, AppResult.Empty -> {
                    analytics.track(
                        AnalyticsEvent.ModerationAction(
                            kind = "verification",
                            targetId = listingId,
                            action = status,
                        ),
                    )
                    mutateQueue { queue ->
                        queue.copy(
                            verifications = queue.verifications.filterNot {
                                it.listingId == listingId
                            },
                            pendingVerifications = (queue.pendingVerifications - 1)
                                .coerceAtLeast(0),
                        )
                    }
                    update {
                        it.copy(busyIds = it.busyIds - listingId, message = "تصمیم ثبت شد")
                    }
                }
                is AppResult.Failure -> update {
                    it.copy(
                        busyIds = it.busyIds - listingId,
                        message = "ثبت تصمیم انجام نشد؛ دوباره تلاش کنید",
                    )
                }
            }
        }
    }

    fun clearMessage() = update { it.copy(message = null) }

    private fun mutateQueue(transform: (ModerationQueue) -> ModerationQueue) {
        update { state ->
            val queue = state.queue ?: return@update state
            state.copy(queue = transform(queue))
        }
    }

    private fun update(transform: (AdminUi) -> AdminUi) {
        val state = _uiState.value
        if (state is UiState.Content) {
            _uiState.value = UiState.Content(transform(state.data))
        }
    }
}
