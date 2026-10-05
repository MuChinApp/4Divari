package ir.chardivari.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.designsystem.components.PropertyCard
import ir.chardivari.core.designsystem.components.SectionHeader
import ir.chardivari.core.designsystem.tokens.AppShapes
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.ListingCardUi
import ir.chardivari.core.ui.UiStateRenderer

@Composable
fun SearchRoute(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onListingClick: (String) -> Unit = {},
    onLogin: () -> Unit = {},
    initialQuery: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
        }
    }

    LaunchedEffect(state) {
        val content = (state as? ir.chardivari.core.common.UiState.Content)?.data
        val message = content?.saveMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveMessage()
            if (message.contains("وارد")) onLogin()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            UiStateRenderer(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onRetry = {
                    val current = (state as? ir.chardivari.core.common.UiState.Content)
                        ?.data?.filters
                    viewModel.search(current ?: ir.chardivari.core.marketplace.SearchFilters.EMPTY)
                },
                emptyTitle = "نتیجه‌ای یافت نشد",
                emptyDescription = "فیلترها را تغییر دهید یا عبارت دیگری بنویسید.",
                loadingLabel = "در حال جستجو…",
            ) { model ->
                SearchContent(
                    model = model,
                    onQueryChange = viewModel::onQueryChange,
                    onDealType = viewModel::onDealTypeSelected,
                    onCityChange = viewModel::onCityChange,
                    onClear = viewModel::onClearFilters,
                    onSave = { viewModel.saveCurrentSearch(null) },
                    onListingClick = onListingClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchContent(
    model: SearchUiModel,
    onQueryChange: (String) -> Unit,
    onDealType: (DealType?) -> Unit,
    onCityChange: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onListingClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "جستجو",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (model.filters.isActive) {
                        Row {
                            TextButton(onClick = onSave) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkAdd,
                                    contentDescription = null,
                                )
                                Text("ذخیره")
                            }
                            TextButton(onClick = onClear) {
                                Text("پاک‌کردن")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = model.filters.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "شهر، محله یا توضیحات…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    shape = AppShapes.Medium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    FilterChip(
                        selected = model.filters.dealType == null,
                        onClick = { onDealType(null) },
                        label = { Text("همه") },
                    )
                    FilterChip(
                        selected = model.filters.dealType == DealType.SALE,
                        onClick = { onDealType(DealType.SALE) },
                        label = { Text("فروش") },
                    )
                    FilterChip(
                        selected = model.filters.dealType == DealType.RENT,
                        onClick = { onDealType(DealType.RENT) },
                        label = { Text("اجاره") },
                    )
                    FilterChip(
                        selected = model.filters.dealType == DealType.RENT_WITH_DEPOSIT,
                        onClick = { onDealType(DealType.RENT_WITH_DEPOSIT) },
                        label = { Text("رهن و اجاره") },
                    )
                }

                OutlinedTextField(
                    value = model.filters.city ?: "",
                    onValueChange = onCityChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("شهر (مثلاً تهران)") },
                    shape = AppShapes.Medium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                Text(
                    text = "نقشه و فیلترهای پیشرفته (مساحت، قیمت، خواب) در ادامه همین فاز به فیلترها اضافه می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }

        if (model.results.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.Xxl),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "نتیجه‌ای یافت نشد",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(AppSpacing.Sm))
                    Text(
                        text = "فیلترها را ساده‌تر کنید یا شهر دیگری را امتحان کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item {
                SectionHeader(title = "نتایج")
                Spacer(Modifier.height(AppSpacing.Sm))
            }
            items(model.results, key = { it.listingId }) { listing ->
                ResultCard(listing = listing, onClick = onListingClick)
            }
        }
    }
}

@Composable
private fun ResultCard(
    listing: ListingCardUi,
    onClick: (String) -> Unit,
) {
    PropertyCard(
        photoUrl = listing.photoUrl,
        priceLine = listing.priceLine,
        pricePerSqmLine = listing.pricePerSqmLine,
        titleLine = listing.titleLine,
        locationLine = listing.locationLine,
        amenitiesLine = listing.amenitiesLine,
        verificationLabel = listing.verificationLabel,
        updatedLabel = listing.updatedLabel,
        modifier = Modifier.padding(horizontal = AppSpacing.Lg),
        isFixtureData = listing.isFixture,
        onClick = { onClick(listing.listingId) },
    )
}
