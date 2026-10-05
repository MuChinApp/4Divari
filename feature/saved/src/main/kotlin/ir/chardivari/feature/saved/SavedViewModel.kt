package ir.chardivari.feature.saved

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
import ir.chardivari.core.marketplace.ListingCardUi
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.SavedSearchItem
import ir.chardivari.core.marketplace.SavedSearchRepository
import ir.chardivari.core.marketplace.StorageBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SavedTab { FAVORITES, SEARCHES }

data class SavedUiModel(
    val tab: SavedTab = SavedTab.FAVORITES,
    val favorites: List<ListingCardUi> = emptyList(),
    val savedSearches: List<SavedSearchItem> = emptyList(),
    val requiresLogin: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val savedSearchRepository: SavedSearchRepository,
    private val listings: ListingRepository,
    private val storage: StorageBaseUrl,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SavedUiModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<SavedUiModel>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(AnalyticsEvent.ScreenView("saved"))
            when (val idsResult = favoritesRepository.favoriteIds()) {
                is AppResult.Failure -> {
                    if (idsResult.error == AppError.Unauthorized) {
                        _uiState.value = UiState.Content(
                            SavedUiModel(requiresLogin = true),
                        )
                    } else {
                        _uiState.value = idsResult.toUiState()
                    }
                }
                AppResult.Empty -> _uiState.value = UiState.Content(SavedUiModel())
                is AppResult.Success -> {
                    val ids = idsResult.data
                    if (ids.isEmpty()) {
                        _uiState.value = UiState.Content(SavedUiModel())
                    } else {
                        // Hydrate each favorited listing (small MVP set).
                        val cards = ids.mapNotNull { id ->
                            when (val r = listings.byId(id)) {
                                is AppResult.Success -> ListingPresenter.card(
                                    r.data,
                                    storage.value,
                                    favorited = true,
                                )
                                else -> null
                            }
                        }
                        _uiState.value = UiState.Content(
                            SavedUiModel(favorites = cards),
                        )
                    }
                    loadSearchesIntoCurrent()
                }
            }
        }
    }

    fun selectTab(tab: SavedTab) {
        val current = (_uiState.value as? UiState.Content)?.data ?: SavedUiModel()
        _uiState.value = UiState.Content(current.copy(tab = tab))
        if (tab == SavedTab.SEARCHES && current.savedSearches.isEmpty()) {
            loadSearchesIntoCurrent()
        }
    }

    fun removeSearch(id: String) {
        viewModelScope.launch {
            when (savedSearchRepository.delete(id)) {
                is AppResult.Success, AppResult.Empty -> {
                    update {
                        it.copy(
                            savedSearches = it.savedSearches.filterNot { s -> s.id == id },
                            message = "جستجو حذف شد",
                        )
                    }
                }
                is AppResult.Failure -> update {
                    it.copy(message = "حذف ممکن نشد")
                }
            }
        }
    }

    fun removeFavorite(listingId: String) {
        viewModelScope.launch {
            when (favoritesRepository.setFavorite(listingId, false)) {
                is AppResult.Success, AppResult.Empty -> {
                    analytics.track(
                        AnalyticsEvent.PropertyFavorite(
                            propertyId = listingId,
                            favorited = false,
                        ),
                    )
                    update {
                        it.copy(
                            favorites = it.favorites.filterNot { c -> c.listingId == listingId },
                            message = "از ذخیره‌ها حذف شد",
                        )
                    }
                }
                is AppResult.Failure -> update { it.copy(message = "حذف ممکن نشد") }
            }
        }
    }

    fun clearMessage() = update { it.copy(message = null) }

    private fun loadSearchesIntoCurrent() {
        viewModelScope.launch {
            when (val result = savedSearchRepository.list()) {
                is AppResult.Success -> update {
                    it.copy(savedSearches = result.data, requiresLogin = false)
                }
                AppResult.Empty -> update { it.copy(savedSearches = emptyList()) }
                is AppResult.Failure -> {
                    if (result.error == AppError.Unauthorized) {
                        update { it.copy(requiresLogin = true) }
                    }
                }
            }
        }
    }

    private inline fun update(transform: (SavedUiModel) -> SavedUiModel) {
        val content = _uiState.value as? UiState.Content ?: return
        _uiState.value = UiState.Content(transform(content.data))
    }
}
