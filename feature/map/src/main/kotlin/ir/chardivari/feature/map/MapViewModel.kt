package ir.chardivari.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.SearchFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One mappable listing — only rows with real coordinates reach the map. */
data class MapPin(
    val listingId: String,
    val latitude: Double,
    val longitude: Double,
    /** Marker title: price line (+ dev-fixture label, anti-prototype rule). */
    val label: String,
    val isFixture: Boolean,
)

data class MapUiModel(
    val pins: List<MapPin> = emptyList(),
)

/**
 * Map (Phase 3 deliverable, DoD closure): real ACTIVE listings with
 * coordinates. Listings without lat/lng are honestly absent from the map
 * (empty state says so) — never fabricated positions.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val listings: ListingRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<MapUiModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<MapUiModel>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            analytics.track(AnalyticsEvent.ScreenView("map"))
            _uiState.value = when (
                val result = listings.search(SearchFilters.EMPTY, limit = MAP_LOAD_LIMIT)
            ) {
                is AppResult.Success -> {
                    val pins = result.data.mapNotNull { it.toPin() }
                    if (pins.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Content(MapUiModel(pins = pins))
                    }
                }
                AppResult.Empty -> UiState.Empty
                is AppResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    fun onPinOpen() {
        analytics.track(AnalyticsEvent.MapInteraction(kind = "pin_open"))
    }

    private fun Listing.toPin(): MapPin? {
        val latitude = property.latitude ?: return null
        val longitude = property.longitude ?: return null
        val fixtureLabel = if (isFixture) " · دادهٔ نمونه" else ""
        return MapPin(
            listingId = id,
            latitude = latitude,
            longitude = longitude,
            label = "${property.city} · ${ListingPresenter.priceLine(this)}$fixtureLabel",
            isFixture = isFixture,
        )
    }

    companion object {
        const val MAP_LOAD_LIMIT = 100
    }
}
