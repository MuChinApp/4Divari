package ir.chardivari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import ir.chardivari.core.designsystem.components.FloatingSurface
import ir.chardivari.core.designsystem.theme.ChardivariTheme
import ir.chardivari.core.navigation.CustomerTab
import ir.chardivari.core.navigation.Routes
import ir.chardivari.core.ui.PlaceholderScreen
import ir.chardivari.feature.auth.AuthRoute
import ir.chardivari.feature.home.HomeRoute
import ir.chardivari.feature.profile.ProfileRoute
import ir.chardivari.feature.property.PropertyDetailRoute
import ir.chardivari.feature.saved.SavedRoute
import ir.chardivari.feature.search.SearchRoute

/**
 * Single activity. RTL enforced for Persian-first product.
 *
 * LayoutDirection.Rtl is the Phase 1 default because the product ships
 * Persian-only; when English lands, direction will follow app locale setting.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ChardivariTheme {
                    ChardivariRoot()
                }
            }
        }
    }
}

/**
 * Root composable with role-aware bottom navigation.
 * Customer tabs now; Agent tabs swap in Phase 5 without changing route keys.
 */
@Composable
private fun ChardivariRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tabs = CustomerTab.customer
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                FloatingSurface {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route ||
                                    (tab.route == Routes.SEARCH &&
                                        currentRoute?.startsWith("search") == true),
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(tab.icon, contentDescription = tab.labelFa)
                                },
                                label = { Text(tab.labelFa) },
                                colors = NavigationBarItemDefaults.colors(),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onListingClick = { id ->
                        navController.navigate(Routes.propertyDetail(id))
                    },
                    onSearchNavigate = { query ->
                        navController.navigate(Routes.searchWithQuery(query))
                    },
                )
            }
            composable(
                route = Routes.SEARCH_QUERY,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                SearchRoute(
                    onListingClick = { id ->
                        navController.navigate(Routes.propertyDetail(id))
                    },
                    onLogin = { navController.navigate(Routes.AUTH) },
                    initialQuery = entry.arguments?.getString("query"),
                )
            }
            composable(Routes.SEARCH) {
                SearchRoute(
                    onListingClick = { id ->
                        navController.navigate(Routes.propertyDetail(id))
                    },
                    onLogin = { navController.navigate(Routes.AUTH) },
                )
            }
            composable(Routes.MAP) {
                PlaceholderScreen(
                    title = "نقشه",
                    message = "جستجوی نقشه‌ای، مارکرها و رسم محدوده پس از افزودن SDK نقشه فعال می‌شود.\nفعلاً نتیجه ساختگی نمایش داده نمی‌شود.",
                )
            }
            composable(Routes.SAVED) {
                SavedRoute(
                    onListingClick = { id ->
                        navController.navigate(Routes.propertyDetail(id))
                    },
                    onLogin = { navController.navigate(Routes.AUTH) },
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(
                route = Routes.PROPERTY_DETAIL,
                arguments = listOf(
                    navArgument("propertyId") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("propertyId").orEmpty()
                PropertyDetailRoute(
                    listingId = id,
                    onBack = { navController.popBackStack() },
                    onLogin = { navController.navigate(Routes.AUTH) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileRoute(
                    onLogin = { navController.navigate(Routes.AUTH) },
                )
            }
            composable(Routes.AUTH) {
                AuthRoute(
                    onLoggedIn = { navController.popBackStack() },
                )
            }
        }
    }
}
