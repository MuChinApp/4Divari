package ir.chardivari.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.designsystem.components.PropertyCard
import ir.chardivari.core.designsystem.components.SectionHeader
import ir.chardivari.core.designsystem.tokens.AppShapes
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.ListingCardUi
import ir.chardivari.core.ui.UiStateRenderer

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onListingClick: (String) -> Unit = {},
    onSearchNavigate: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        UiStateRenderer(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            emptyTitle = "هنوز فایلی منتشر نشده است",
            emptyDescription = "به‌زودی اولین ملک‌ها اینجا نمایش داده می‌شوند.",
            loadingLabel = "در حال آماده‌سازی خانه…",
        ) { model ->
            HomeContent(
                model = model,
                onSearchSubmit = { query ->
                    viewModel.onSearchSubmitted(query)
                    onSearchNavigate(query)
                },
                onListingClick = onListingClick,
            )
        }
    }
}

@Composable
private fun HomeContent(
    model: HomeUiModel,
    onSearchSubmit: (String) -> Unit,
    onListingClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xl),
            ) {
                Text(
                    text = model.heroQuestion,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(AppSpacing.Lg))
                SearchBar(
                    hint = model.searchHint,
                    onSubmit = onSearchSubmit,
                )
                Spacer(Modifier.height(AppSpacing.Lg))
                TransactionActions()
            }
        }

        if (model.newListings.isNotEmpty()) {
            item {
                SectionHeader(title = "ملک‌های جدید")
                Spacer(Modifier.height(AppSpacing.Sm))
            }
            items(model.newListings, key = { "new-${it.listingId}" }) { listing ->
                ListingCardRow(listing = listing, onClick = onListingClick)
            }
        }

        items(model.sections, key = { it.id }) { section ->
            SectionRail(section)
        }
    }
}

@Composable
private fun ListingCardRow(
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

@Composable
private fun SearchBar(
    hint: String,
    onSubmit: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
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
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
    )
}

@Composable
private fun TransactionActions() {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
        TransactionChip("خرید", selected = true)
        TransactionChip("اجاره", selected = false)
        TransactionChip("رهن و اجاره", selected = false)
    }
}

@Composable
private fun RowScope.TransactionChip(label: String, selected: Boolean) {
    Surface(
        shape = AppShapes.Pill,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
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
private fun SectionRail(section: HomeSection) {
    Column {
        SectionHeader(title = section.title)
        Spacer(Modifier.height(AppSpacing.Sm))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Lg),
            shape = AppShapes.Medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "به‌زودی — هنوز داده‌ای به این بخش وصل نیست",
                modifier = Modifier.padding(AppSpacing.Lg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
