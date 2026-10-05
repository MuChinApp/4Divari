package ir.chardivari.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.designsystem.components.EmptyState
import ir.chardivari.core.designsystem.components.PropertyCard
import ir.chardivari.core.designsystem.tokens.AppShapes
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.ui.UiStateRenderer

@Composable
fun SavedRoute(
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = hiltViewModel(),
    onListingClick: (String) -> Unit = {},
    onLogin: () -> Unit = {},
    onOpenSearch: (String?) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state) {
        val content = (state as? ir.chardivari.core.common.UiState.Content)?.data
        content?.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        UiStateRenderer(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            emptyTitle = "هنوز چیزی ذخیره نکرده‌اید",
            emptyDescription = "ملک یا جستجویی را ذخیره کنید تا اینجا ببینید.",
            loadingLabel = "در حال بارگذاری ذخیره‌ها…",
        ) { model ->
            if (model.requiresLogin) {
                EmptyState(
                    title = "برای ذخیره‌ها وارد شوید",
                    description = "ملک‌های ذخیره‌شده و جستجوهای شما به حساب کاربری‌تان متصل است.",
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = "ورود / ثبت‌نام",
                    onAction = onLogin,
                    icon = Icons.Outlined.FavoriteBorder,
                )
            } else {
                SavedContent(
                    model = model,
                    onTab = viewModel::selectTab,
                    onRemoveSearch = viewModel::removeSearch,
                    onRemoveFavorite = viewModel::removeFavorite,
                    onListingClick = onListingClick,
                    onOpenSearch = onOpenSearch,
                )
            }
        }
    }
}

@Composable
private fun SavedContent(
    model: SavedUiModel,
    onTab: (SavedTab) -> Unit,
    onRemoveSearch: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onListingClick: (String) -> Unit,
    onOpenSearch: (String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                SavedTabChip(
                    label = "ملک‌ها",
                    selected = model.tab == SavedTab.FAVORITES,
                    onClick = { onTab(SavedTab.FAVORITES) },
                )
                SavedTabChip(
                    label = "جستجوها",
                    selected = model.tab == SavedTab.SEARCHES,
                    onClick = { onTab(SavedTab.SEARCHES) },
                )
            }
        }

        when (model.tab) {
            SavedTab.FAVORITES -> {
                if (model.favorites.isEmpty()) {
                    item {
                        EmptyState(
                            title = "ملکی ذخیره نشده است",
                            description = "از صفحه خانه یا جستجو، روی آیکن ذخیره بزنید.",
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.FavoriteBorder,
                        )
                    }
                } else {
                    items(model.favorites, key = { it.listingId }) { card ->
                        Column {
                            PropertyCard(
                                photoUrl = card.photoUrl,
                                priceLine = card.priceLine,
                                pricePerSqmLine = card.pricePerSqmLine,
                                titleLine = card.titleLine,
                                locationLine = card.locationLine,
                                amenitiesLine = card.amenitiesLine,
                                verificationLabel = card.verificationLabel,
                                updatedLabel = card.updatedLabel,
                                modifier = Modifier.padding(horizontal = AppSpacing.Lg),
                                isFixtureData = card.isFixture,
                                onClick = { onListingClick(card.listingId) },
                                onFavorite = { onRemoveFavorite(card.listingId) },
                            )
                        }
                    }
                }
            }
            SavedTab.SEARCHES -> {
                if (model.savedSearches.isEmpty()) {
                    item {
                        EmptyState(
                            title = "جستجویی ذخیره نشده است",
                            description = "در صفحه جستجو، «ذخیره» بزنید تا بعداً سریع دوباره اجرا کنید.",
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Search,
                        )
                    }
                } else {
                    items(model.savedSearches, key = { it.id }) { item ->
                        SavedSearchRow(
                            item = item,
                            onOpen = { onOpenSearch(null) },
                            onDelete = { onRemoveSearch(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = AppShapes.Pill,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SavedSearchRow(
    item: ir.chardivari.core.marketplace.SavedSearchItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Lg),
        shape = AppShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.Lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name ?: describeFilters(item.filters),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(AppSpacing.Xxs))
                Text(
                    text = filterSummary(item.filters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun describeFilters(f: ir.chardivari.core.marketplace.SearchFilters): String {
    if (!f.isActive) return "جستجوی بدون فیلتر"
    return f.normalizedQuery().ifBlank {
        f.city ?: when (f.dealType) {
            DealType.SALE -> "فروش"
            DealType.RENT -> "اجاره"
            DealType.RENT_WITH_DEPOSIT -> "رهن و اجاره"
            null -> "جستجوی ذخیره‌شده"
        }
    }
}

private fun filterSummary(f: ir.chardivari.core.marketplace.SearchFilters): String =
    buildList {
        f.city?.let { add("شهر: $it") }
        f.dealType?.let {
            add(
                when (it) {
                    DealType.SALE -> "فروش"
                    DealType.RENT -> "اجاره"
                    DealType.RENT_WITH_DEPOSIT -> "رهن و اجاره"
                },
            )
        }
        if (f.minAreaSqm != null || f.maxAreaSqm != null) {
            add(
                "مساحت ${f.minAreaSqm ?: "—"} تا ${f.maxAreaSqm ?: "—"} متر",
            )
        }
        if (f.minBedrooms != null) {
            add("${f.minBedrooms}+ خواب")
        }
    }.joinToString(" • ").ifBlank { "بدون فیلتر اضافه" }

@Suppress("unused")
private val textAlignStart = TextAlign.Start
