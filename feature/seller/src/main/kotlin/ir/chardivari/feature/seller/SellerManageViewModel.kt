package ir.chardivari.feature.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.common.toAsciiDigits
import ir.chardivari.core.marketplace.AgentRepository
import ir.chardivari.core.marketplace.SellerAction
import ir.chardivari.core.marketplace.SellerListingItem
import ir.chardivari.core.marketplace.SellerRepository
import ir.chardivari.core.marketplace.StorageBaseUrl
import ir.chardivari.core.ui.errorDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssignDialogUi(
    val listingId: String,
    val phone: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

data class SellerManageUi(
    val items: List<SellerListingItem> = emptyList(),
    val requiresLogin: Boolean = false,
    /** Listing ids with an in-flight lifecycle action — disables their buttons. */
    val busyIds: Set<String> = emptySet(),
    val message: String? = null,
    /** Non-null while the «واگذاری به مشاور» dialog is open. */
    val assign: AssignDialogUi? = null,
)

/**
 * Manage screen: list own listings + pause / resume / mark-sold / publish-draft.
 * Every status change is server-confirmed before the row updates.
 */
@HiltViewModel
class SellerManageViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
    private val agentRepository: AgentRepository,
    val storage: StorageBaseUrl,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SellerManageUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<SellerManageUi>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(AnalyticsEvent.ScreenView("seller_manage"))
            when (val result = sellerRepository.myListings()) {
                is AppResult.Failure -> {
                    if (result.error == AppError.Unauthorized) {
                        _uiState.value = UiState.Content(
                            SellerManageUi(requiresLogin = true),
                        )
                    } else {
                        _uiState.value = UiState.Error(result.error)
                    }
                }
                AppResult.Empty -> _uiState.value = UiState.Content(SellerManageUi())
                is AppResult.Success -> _uiState.value = UiState.Content(
                    SellerManageUi(items = result.data),
                )
            }
        }
    }

    fun performAction(listingId: String, action: SellerAction) {
        val state = (_uiState.value as? UiState.Content)?.data ?: return
        if (listingId in state.busyIds) return
        _uiState.value = UiState.Content(
            state.copy(busyIds = state.busyIds + listingId, message = null),
        )
        viewModelScope.launch {
            when (val result = sellerRepository.setStatus(listingId, action)) {
                is AppResult.Success, AppResult.Empty -> {
                    val current = (_uiState.value as? UiState.Content)?.data
                        ?: SellerManageUi()
                    _uiState.value = UiState.Content(
                        current.copy(
                            items = current.items.map { item ->
                                if (item.id == listingId) {
                                    item.copy(status = action.toStatus)
                                } else {
                                    item
                                }
                            },
                            busyIds = current.busyIds - listingId,
                            message = "وضعیت آگهی به‌روزرسانی شد",
                        ),
                    )
                }
                is AppResult.Failure -> {
                    val current = (_uiState.value as? UiState.Content)?.data
                        ?: SellerManageUi()
                    val message = if (result.error == AppError.Unauthorized) {
                        "برای این عملیات وارد شوید"
                    } else {
                        "تغییر وضعیت انجام نشد: ${errorDescription(result.error)}"
                    }
                    _uiState.value = UiState.Content(
                        current.copy(
                            busyIds = current.busyIds - listingId,
                            message = message,
                        ),
                    )
                }
            }
        }
    }

    fun clearMessage() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        _uiState.value = UiState.Content(current.copy(message = null))
    }

    // ---- واگذاری به مشاور (00094 assign_agent) ----

    fun openAssign(listingId: String) {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        _uiState.value = UiState.Content(
            current.copy(assign = AssignDialogUi(listingId = listingId), message = null),
        )
    }

    fun closeAssign() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        if (current.assign?.busy == true) return
        _uiState.value = UiState.Content(current.copy(assign = null))
    }

    fun setAssignPhone(phone: String) {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        val dialog = current.assign ?: return
        _uiState.value = UiState.Content(
            current.copy(assign = dialog.copy(phone = phone, error = null)),
        )
    }

    fun submitAssign() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        val dialog = current.assign ?: return
        if (dialog.busy) return

        val raw = dialog.phone.trim()
        val phone: String? = if (raw.isEmpty()) {
            null // unassign — server clears agent_id
        } else {
            val digits = raw.toAsciiDigits().filter { it.isDigit() || it == '+' }
            if (digits.length < 10) {
                _uiState.value = UiState.Content(
                    current.copy(assign = dialog.copy(error = "شماره موبایل معتبر وارد کنید")),
                )
                return
            }
            digits
        }

        _uiState.value = UiState.Content(
            current.copy(assign = dialog.copy(busy = true, error = null)),
        )
        viewModelScope.launch {
            when (val result = agentRepository.assignAgent(dialog.listingId, phone)) {
                is AppResult.Success, AppResult.Empty -> {
                    val updated = (_uiState.value as? UiState.Content)?.data
                        ?: SellerManageUi()
                    _uiState.value = UiState.Content(
                        updated.copy(
                            assign = null,
                            message = if (phone == null) {
                                "واگذاری مشاور برداشته شد"
                            } else {
                                "فایل به مشاور واگذار شد"
                            },
                        ),
                    )
                }
                is AppResult.Failure -> {
                    val message = when {
                        result.error == AppError.Unauthorized -> "برای این عملیات وارد شوید"
                        result.error is AppError.Client ->
                            "واگذاری انجام نشد؛ شماره باید متعلق به مشاور فعال باشد"
                        else -> "واگذاری انجام نشد: ${errorDescription(result.error)}"
                    }
                    _uiState.value = UiState.Content(
                        updatedWithAssignError(message),
                    )
                }
            }
        }
    }

    private fun updatedWithAssignError(message: String): SellerManageUi {
        val current = (_uiState.value as? UiState.Content)?.data ?: SellerManageUi()
        return current.copy(
            assign = current.assign?.copy(busy = false, error = message),
        )
    }
}
