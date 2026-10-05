package ir.chardivari.feature.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.ui.UiStateRenderer

/**
 * Map tab — replaces the former placeholder. Real listings with real
 * coordinates only; loading / empty / error states via [UiStateRenderer].
 */
@Composable
fun MapRoute(
    onListingClick: (String) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    UiStateRenderer(
        state = state,
        modifier = Modifier.fillMaxSize(),
        onRetry = viewModel::load,
        emptyTitle = "ملکی با مختصات ثبت نشده است",
        emptyDescription = "فایل‌های منتشرشده‌ای که مختصات دارند روی نقشه نمایش داده می‌شوند.",
        loadingLabel = "در حال بارگذاری نقشه…",
    ) { model ->
        OsmMapView(
            pins = model.pins,
            onPinClick = { listingId ->
                viewModel.onPinOpen()
                onListingClick(listingId)
            },
        )
    }
}
