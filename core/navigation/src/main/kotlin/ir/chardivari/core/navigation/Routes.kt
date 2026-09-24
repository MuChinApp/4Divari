package ir.chardivari.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder

/**
 * Route graph for the customer app.
 *
 * Role-based navigation (Agent dashboard, Admin) swaps the root tab set
 * in Phase 5 — route definitions stay stable so deep links survive.
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_QUERY = "search?query={query}"
    const val MAP = "map"
    const val SAVED = "saved"
    const val PROFILE = "profile"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"

    const val PROPERTY_DETAIL = "property/{propertyId}"
    fun propertyDetail(propertyId: String) = "property/$propertyId"

    const val SELLER = "seller"
    const val SELLER_WIZARD = "seller/new"

    /**
     * Agent shell (Phase 5) — entered from the Profile screen; customer
     * root tabs never swap, route keys stay stable for deep links.
     */
    const val AGENT = "agent"

    fun searchWithQuery(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "search?query=$encoded"
    }
}

enum class CustomerTab(
    val route: String,
    val labelFa: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "خانه", Icons.Outlined.Home),
    SEARCH(Routes.SEARCH, "جستجو", Icons.Outlined.Search),
    MAP(Routes.MAP, "نقشه", Icons.Outlined.Map),
    SAVED(Routes.SAVED, "ذخیره‌ها", Icons.Outlined.FavoriteBorder),
    PROFILE(Routes.PROFILE, "پروفایل", Icons.Outlined.Person),
    ;

    companion object {
        /** Customer bottom nav — default. */
        val customer: List<CustomerTab> = entries.toList()
    }
}
