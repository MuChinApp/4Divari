package ir.chardivari.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.chardivari.core.ui.PlaceholderScreen

/**
 * Search — Phase 1 placeholder.
 *
 * Flagged PLACEHOLDER in the data-source sense: no fake results are shown.
 * Full filters + NL search land in Phase 3.
 */
@Composable
fun SearchRoute(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PlaceholderScreen(
            title = "جستجو",
            message = "فیلترها، جستجوی نقشه‌ای و جستجوی طبیعی در فاز بعدی اضافه می‌شوند.\nفعلاً هیچ نتیجه ساختگی نمایش داده نمی‌شود.",
            modifier = Modifier.padding(padding),
        )
    }
}
