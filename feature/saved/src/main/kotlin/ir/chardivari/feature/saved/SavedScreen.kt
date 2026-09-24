package ir.chardivari.feature.saved

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.chardivari.core.ui.PlaceholderScreen

/**
 * Saved / Favorites — Phase 1 placeholder.
 * Offline-cached favorites arrive with Phase 3 + local Room/DataStore layer.
 */
@Composable
fun SavedRoute(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PlaceholderScreen(
            title = "ذخیره‌ها",
            message = "ملک‌های ذخیره‌شده و جستجوهای ذخیره‌شده اینجا نمایش داده می‌شوند.\nبرای شروع، از صفحه خانه ملکی را ذخیره کنید.",
            modifier = Modifier.padding(padding),
        )
    }
}
