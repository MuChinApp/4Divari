package ir.chardivari.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.common.toUiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingCardUi
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.SavedSearchRepository
import ir.chardivari.core.marketplace.SearchFilters
import ir.chardivari.core.marketplace.StorageBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiModel(
    val filters: SearchFilters = SearchFilters.EMPTY,
    val results: List<ListingCardUi> = emptyList(),
    val saveMessage: String? = null,
)

/**
 * Search + structured filters against real PostgREST listings.
 * Empty query with no filters still loads the ACTIVE feed.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val listings: ListingRepository,
    private val savedSearches: SavedSearchRepository,
    private val storage: StorageBaseUrl,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SearchUiModel>>(UiState.Idle)
    val uiState: StateFlow<UiState<SearchUiModel>> = _uiState.asStateFlow()

    private var filters: SearchFilters = SearchFilters.EMPTY

    init {
        search(filters)
    }

    fun search(next: SearchFilters) {
        filters = next
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(
                AnalyticsEvent.SearchCreated(
                    queryLength = next.normalizedQuery().length,
                    filterCount = next.activeCount,
                ),
            )
            when (val result = listings.search(next, limit = 40)) {
                is AppResult.Success -> {
                    _uiState.value = UiState.Content(
                        SearchUiModel(
                            filters = next,
                            results = result.data.map {
                                ListingPresenter.card(it, storage.value)
                            },
                        ),
                    )
                }
                AppResult.Empty -> {
                    _uiState.value = UiState.Content(
                        SearchUiModel(filters = next, results = emptyList()),
                    )
                }
                is AppResult.Failure -> _uiState.value = result.toUiState()
            }
        }
    }

    fun onQueryChange(query: String) {
        search(filters.copy(query = query))
    }

    fun onDealTypeSelected(dealType: DealType?) {
        analytics.track(AnalyticsEvent.FilterUsed("deal_type"))
        search(filters.copy(dealType = dealType))
    }

    fun onCityChange(city: String) {
        analytics.track(AnalyticsEvent.FilterUsed("city"))
        search(filters.copy(city = city.takeIf { it.isNotBlank() }))
    }

    fun onClearFilters() {
        search(SearchFilters.EMPTY)
    }

    fun saveCurrentSearch(name: String?) {
        if (!filters.isActive) return
        viewModelScope.launch {
            when (val result = savedSearches.save(name, filters)) {
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.SearchSaved(
                            savedSearchId = result.data.id,
                        ),
                    )
                    updateMessage { it.copy(saveMessage = "جستجو ذخیره شد") }
                }
                AppResult.Empty -> updateMessage { it.copy(saveMessage = "ذخیره انجام نشد") }
                is AppResult.Failure -> {
                    val msg = when (result.error) {
                        ir.chardivari.core.common.AppError.Unauthorized ->
                            "برای ذخیره جستجو وارد شوید"
                        else -> "ذخیره جستجو ممکن نشد"
                    }
                    updateMessage { it.copy(saveMessage = msg) }
                }
            }
        }
    }

    fun clearSaveMessage() {
        updateMessage { it.copy(saveMessage = null) }
    }

    private inline fun updateMessage(transform: (SearchUiModel) -> SearchUiModel) {
        val content = _uiState.value as? UiState.Content ?: return
        _uiState.value = UiState.Content(transform(content.data))
    }
}
