package ir.chardivari.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.common.Format
import ir.chardivari.core.common.toPersianDigits
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.SellerListingItem
import ir.chardivari.core.ui.UiStateRenderer

@Composable
fun AgentFilesScreen(
    onListingClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgentFilesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "فایل‌های من",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
        )
        UiStateRenderer(
            state = state,
            onRetry = viewModel::load,
            emptyTitle = "فایلی به شما واگذار نشده است",
            emptyDescription = "فایل‌های فعال و متوقفی که مالک آن‌ها را به شما سپرده باشد اینجا نمایش داده می‌شوند.",
            loadingLabel = "در حال دریافت فایل‌ها…",
            content = { items ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = items, key = { it.id }) { item ->
                        FileRow(
                            item = item,
                            onClick = { onListingClick(item.id) },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun FileRow(
    item: SellerListingItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(item.city).joinToString("، ").ifEmpty { "بدون آدرس" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    item.areaSqm?.let { append(Format.area(it)) } ?: append("—")
                    item.propertyType?.let {
                        append(" · ")
                        append(ir.chardivari.core.marketplace.ListingPresenter.propertyTypeLabel(it))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Format.price(item.priceRial),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (item.status == "ACTIVE") "فعال" else "متوقف",
                style = MaterialTheme.typography.labelMedium,
                color = if (item.status == "ACTIVE") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
