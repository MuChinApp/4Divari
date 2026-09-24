package ir.chardivari.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home content model.
 *
 * Phase 1: every rail is [HomeSection.isImplemented] = false until Phase 3
 * wires real marketplace queries. UI must show "به‌زودی", never fake listings.
 */
data class HomeUiModel(
    val heroQuestion: String = "دنبال چه ملکی هستید؟",
    val searchHint: String = "مثلاً: آپارتمان ۸۰ متری در منطقه ۵",
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<HomeUiModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeUiModel>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(AnalyticsEvent.ScreenView("home"))
            _uiState.value = UiState.Content(
                HomeUiModel(
                    sections = plannedSections(),
                ),
            )
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
        HomeSection("new", "ملک‌های جدید", isImplemented = false),
        HomeSection("nearby", "نزدیک شما", isImplemented = false),
        HomeSection("price_drop", "کاهش قیمت", isImplemented = false),
        HomeSection("verified", "فایل‌های تأییدشده", isImplemented = false),
        HomeSection("popular", "محبوب‌ترین‌ها", isImplemented = false),
        HomeSection("ai", "پیشنهاد AI", isImplemented = false),
    )
}
