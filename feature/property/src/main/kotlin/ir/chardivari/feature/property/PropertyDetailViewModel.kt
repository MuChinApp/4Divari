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
import ir.chardivari.core.marketplace.FavoritesRepository
import ir.chardivari.core.marketplace.ListingContact
import ir.chardivari.core.marketplace.ListingContactRepository
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.PropertyDetailUi
import ir.chardivari.core.marketplace.StorageBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PropertyDetailModel(
    val detail: PropertyDetailUi,
    val contact: ListingContact? = null,
    val contactLoading: Boolean = false,
    val contactError: String? = null,
    val favoriteError: String? = null,
)

@HiltViewModel
class PropertyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listings: ListingRepository,
    private val favorites: FavoritesRepository,
    private val contacts: ListingContactRepository,
    private val storage: StorageBaseUrl,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val listingId: String = checkNotNull(savedStateHandle["propertyId"])

    private val _uiState = MutableStateFlow<UiState<PropertyDetailModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<PropertyDetailModel>> = _uiState.asStateFlow()

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
}
