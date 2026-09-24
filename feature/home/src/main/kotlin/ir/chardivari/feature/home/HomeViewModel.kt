package ir.chardivari.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.common.toUiState
import ir.chardivari.core.marketplace.ListingCardUi
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.StorageBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home — Phase 3: real ACTIVE feed for the "new" rail.
 * Remaining rails stay [HomeSection.isImplemented] = false ("به‌زودی"),
 * never fake listings (anti-prototype).
 */
data class HomeUiModel(
    val heroQuestion: String = "دنبال چه ملکی هستید؟",
    val searchHint: String = "مثلاً: آپارتمان ۸۰ متری در منطقه ۵",
    val newListings: List<ListingCardUi> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)

data class HomeSection(
    val id: String,
    val title: String,
    val isImplemented: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analytics: AnalyticsTracker,
    private val listings: ListingRepository,
    private val storage: StorageBaseUrl,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<HomeUiModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeUiModel>> = _uiState.asStateFlow()

    /** Favorite id set for card chrome; empty when logged out. */
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(AnalyticsEvent.ScreenView("home"))
            when (val result = listings.feed(limit = 12)) {
                is AppResult.Success -> {
                    _uiState.value = UiState.Content(
                        HomeUiModel(
                            newListings = result.data.map {
                                ListingPresenter.card(
                                    listing = it,
                                    storageBaseUrl = storage.value,
                                    favorited = false,
                                )
                            },
                            sections = plannedSections(),
                        ),
                    )
                }
                AppResult.Empty -> {
                    _uiState.value = UiState.Empty
                }
                is AppResult.Failure -> {
                    _uiState.value = result.toUiState()
                }
            }
        }
    }

    fun onSearchSubmitted(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        analytics.track(
            AnalyticsEvent.SearchCreated(
                queryLength = trimmed.length,
                filterCount = 0,
            ),
        )
    }

    private fun plannedSections(): List<HomeSection> = listOf(
        HomeSection("personalized", "پیشنهادهای شخصی", isImplemented = false),
        HomeSection("nearby", "نزدیک شما", isImplemented = false),
        HomeSection("price_drop", "کاهش قیمت", isImplemented = false),
        HomeSection("verified", "فایل‌های تأییدشده", isImplemented = false),
        HomeSection("popular", "محبوب‌ترین‌ها", isImplemented = false),
        HomeSection("ai", "پیشنهاد AI", isImplemented = false),
    )
}
