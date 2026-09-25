package ir.chardivari.feature.property

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.common.toUiState
import ir.chardivari.core.marketplace.ChatRepository
import ir.chardivari.core.marketplace.FavoritesRepository
import ir.chardivari.core.marketplace.ListingContact
import ir.chardivari.core.marketplace.ListingContactRepository
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.PropertyDetailUi
import ir.chardivari.core.marketplace.StorageBaseUrl
import ir.chardivari.core.marketplace.VisitsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** Visit-slot picker dialog state — ISO slot pickers, Jalali module comes later. */
data class VisitDialogUi(
    val dateMillis: Long? = null,
    val hour: Int = 10,
    val minute: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
)

data class PropertyDetailModel(
    val detail: PropertyDetailUi,
    val contact: ListingContact? = null,
    val contactLoading: Boolean = false,
    val contactError: String? = null,
    val favoriteError: String? = null,
    val visitDialog: VisitDialogUi? = null,
    val visitMessage: String? = null,
    val conversationError: String? = null,
)

/** One-shot events (navigation / login) emitted by [PropertyDetailViewModel]. */
sealed interface PropertyDetailEvent {
    data class OpenThread(val conversationId: String) : PropertyDetailEvent
    data object LoginRequired : PropertyDetailEvent
}

/**
 * Builds an ISO-8601 slot with Tehran's fixed +03:30 offset from a
 * DatePicker UTC-midnight millis + wall clock. java.util (not java.time)
 * because minSdk 24 without desugaring.
 */
internal fun buildSlotIso(
    dateMillis: Long,
    hour: Int,
    minute: Int,
    plusHours: Int,
): String {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateMillis
    }
    val year = utc.get(Calendar.YEAR)
    val month = utc.get(Calendar.MONTH)
    val day = utc.get(Calendar.DAY_OF_MONTH)
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
    cal.clear()
    cal.set(year, month, day, hour, minute, 0)
    cal.add(Calendar.HOUR_OF_DAY, plusHours)
    return String.format(
        Locale.US,
        "%04d-%02d-%02dT%02d:%02d:%02d+03:30",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
    )
}

@HiltViewModel
class PropertyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listings: ListingRepository,
    private val favorites: FavoritesRepository,
    private val contacts: ListingContactRepository,
    private val chat: ChatRepository,
    private val visits: VisitsRepository,
    private val storage: StorageBaseUrl,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val listingId: String = checkNotNull(savedStateHandle["propertyId"])

    private val _uiState = MutableStateFlow<UiState<PropertyDetailModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<PropertyDetailModel>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PropertyDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PropertyDetailEvent> = _events.asSharedFlow()

    private var favorited: Boolean = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(AnalyticsEvent.PropertyView)
            when (val result = listings.byId(listingId)) {
                is AppResult.Success -> {
                    val detail = ListingPresenter.detail(
                        listing = result.data,
                        storageBaseUrl = storage.value,
                        favorited = favorited,
                    )
                    _uiState.value = UiState.Content(
                        PropertyDetailModel(detail = detail),
                    )
                    loadContact()
                }
                AppResult.Empty -> _uiState.value = UiState.Empty
                is AppResult.Failure -> _uiState.value = result.toUiState()
            }
        }
    }

    fun toggleFavorite() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        val next = !current.detail.isFavorited
        viewModelScope.launch {
            when (val result = favorites.setFavorite(listingId, next)) {
                is AppResult.Success -> {
                    favorited = next
                    analytics.track(
                        AnalyticsEvent.PropertyFavorite(
                            propertyId = listingId,
                            favorited = next,
                        ),
                    )
                    _uiState.value = UiState.Content(
                        current.copy(
                            detail = current.detail.copy(isFavorited = next),
                            favoriteError = null,
                        ),
                    )
                }
                AppResult.Empty -> Unit
                is AppResult.Failure -> {
                    val message = when (result.error) {
                        AppError.Unauthorized -> "برای ذخیره ملک وارد حساب خود شوید"
                        else -> "ذخیره انجام نشد؛ دوباره تلاش کنید"
                    }
                    _uiState.value = UiState.Content(
                        current.copy(favoriteError = message),
                    )
                }
            }
        }
    }

    fun share() {
        analytics.track(AnalyticsEvent.PropertyShare(propertyId = listingId))
    }

    fun loadContact() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        if (current.contactLoading || current.contact != null) return
        _uiState.value = UiState.Content(current.copy(contactLoading = true, contactError = null))
        viewModelScope.launch {
            when (val result = contacts.contact(listingId)) {
                is AppResult.Success -> {
                    _uiState.value = UiState.Content(
                        current.copy(contact = result.data, contactLoading = false),
                    )
                    if (result.data.leadCreated) {
                        analytics.track(
                            AnalyticsEvent.ContactLeadCreated(listingId = listingId),
                        )
                    }
                }
                AppResult.Empty -> {
                    _uiState.value = UiState.Content(
                        current.copy(
                            contactLoading = false,
                            contactError = "شماره تماس برای این فایل ثبت نشده است",
                        ),
                    )
                }
                is AppResult.Failure -> {
                    _uiState.value = UiState.Content(
                        current.copy(
                            contactLoading = false,
                            contactError = "دریافت شماره تماس ممکن نشد",
                        ),
                    )
                }
            }
        }
    }

    fun contactOpened() {
        analytics.track(
            AnalyticsEvent.ContactAgent(
                propertyId = listingId,
                channel = "phone",
            ),
        )
    }

    // ---- Phase 6: chat entry + visit scheduling ----

    fun openConversation() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        viewModelScope.launch {
            when (val result = chat.startConversation(listingId)) {
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.ContactAgent(
                            propertyId = listingId,
                            channel = "chat",
                        ),
                    )
                    _events.emit(PropertyDetailEvent.OpenThread(result.data))
                }
                AppResult.Empty -> _uiState.value = UiState.Content(
                    current.copy(conversationError = "شروع گفتگو ممکن نشد؛ دوباره تلاش کنید"),
                )
                is AppResult.Failure -> {
                    if (result.error == AppError.Unauthorized) {
                        _events.emit(PropertyDetailEvent.LoginRequired)
                    } else {
                        _uiState.value = UiState.Content(
                            current.copy(
                                conversationError = "شروع گفتگو ممکن نشد؛ دوباره تلاش کنید",
                            ),
                        )
                    }
                }
            }
        }
    }

    fun openVisitDialog() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        _uiState.value = UiState.Content(
            current.copy(visitDialog = VisitDialogUi(), visitMessage = null),
        )
    }

    fun closeVisitDialog() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        if (current.visitDialog?.busy == true) return
        _uiState.value = UiState.Content(current.copy(visitDialog = null))
    }

    fun setVisitDate(dateMillis: Long?) {
        updateVisitDialog { it.copy(dateMillis = dateMillis, error = null) }
    }

    fun setVisitTime(hour: Int, minute: Int) {
        updateVisitDialog { it.copy(hour = hour, minute = minute, error = null) }
    }

    fun confirmVisitDialog(dateMillis: Long) {
        updateVisitDialog { it.copy(dateMillis = dateMillis) }
    }

    fun submitVisit() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        val dialog = current.visitDialog ?: return
        val dateMillis = dialog.dateMillis
        if (dateMillis == null) {
            updateVisitDialog { it.copy(error = "تاریخ بازدید را انتخاب کنید") }
            return
        }
        if (dialog.busy) return
        updateVisitDialog { it.copy(busy = true, error = null) }
        val startIso = buildSlotIso(dateMillis, dialog.hour, dialog.minute, plusHours = 0)
        val endIso = buildSlotIso(dateMillis, dialog.hour, dialog.minute, plusHours = 1)
        viewModelScope.launch {
            when (val result = visits.requestVisit(listingId, startIso, endIso)) {
                is AppResult.Success, AppResult.Empty -> {
                    analytics.track(AnalyticsEvent.VisitRequested(propertyId = listingId))
                    _uiState.value = UiState.Content(
                        (_uiState.value as? UiState.Content)?.data
                            ?.copy(
                                visitDialog = null,
                                visitMessage = "درخواست بازدید ثبت شد؛ پس از تأیید مشاور به شما اطلاع داده می‌شود",
                            )
                            ?: current,
                    )
                }
                is AppResult.Failure -> {
                    val error = result.error
                    val message = when {
                        error is AppError.Client && error.code == 409 ->
                            "این بازدید همین حالا برای شخص دیگری رزرو شد؛ بازه دیگری انتخاب کنید"
                        error == AppError.Unauthorized -> {
                            _events.emit(PropertyDetailEvent.LoginRequired)
                            null
                        }
                        else -> "ثبت درخواست بازدید انجام نشد؛ دوباره تلاش کنید"
                    }
                    if (message == null) {
                        closeVisitDialogInternal()
                    } else {
                        updateVisitDialog { it.copy(busy = false, error = message) }
                    }
                }
            }
        }
    }

    private fun closeVisitDialogInternal() {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        _uiState.value = UiState.Content(current.copy(visitDialog = null))
    }

    private fun updateVisitDialog(transform: (VisitDialogUi) -> VisitDialogUi) {
        val current = (_uiState.value as? UiState.Content)?.data ?: return
        val dialog = current.visitDialog ?: return
        _uiState.value = UiState.Content(
            current.copy(visitDialog = transform(dialog)),
        )
    }
}
