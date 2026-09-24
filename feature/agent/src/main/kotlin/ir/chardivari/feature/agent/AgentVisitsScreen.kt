package ir.chardivari.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import ir.chardivari.core.common.UiState
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.ui.UiStateRenderer

@Composable
fun AgentVisitsScreen(
    modifier: Modifier = Modifier,
    viewModel: AgentVisitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "بازدیدها",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
        )
        UiStateRenderer(
            state = state,
            onRetry = viewModel::load,
            emptyTitle = "بازدیدی ثبت نشده است",
            emptyDescription = "وقت‌هایی که خریداران برای فایل‌های شما درخواست می‌دهند اینجا دیده می‌شوند.",
            loadingLabel = "در حال دریافت بازدیدها…",
            content = { data ->
                Column {
                    data.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.consumeMessage() }
                                .padding(horizontal = AppSpacing.Lg),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = data.visits, key = { it.id }) { visit ->
                            VisitRow(
                                visit = visit,
                                busy = visit.id in data.busyIds,
                                onStatus = { viewModel.setStatus(visit.id, it) },
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun VisitRow(
    visit: ir.chardivari.core.marketplace.AgentVisit,
    busy: Boolean,
    onStatus: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slotLabel(visit.slotStart, visit.slotEnd),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = buildString {
                        append(visit.address ?: "بدون آدرس")
                        append(" · ")
                        append(visit.buyerName ?: "خریدار")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                visit.priceRial?.let {
                    Text(
                        text = Format.price(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(visitStatusLabel(visit.status)) },
                enabled = false,
            )
        }
        val actions = VisitTransitions.available(visit.status)
        if (actions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                actions.forEach { to ->
                    OutlinedButton(
                        onClick = { onStatus(to) },
                        enabled = !busy,
                    ) {
                        Text(visitStatusLabel(to))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * ISO-8601 slice — Jalali conversion lands with the locale module (same
 * honest approach as [ir.chardivari.core.marketplace.ListingPresenter]).
 */
internal fun slotLabel(startIso: String, endIso: String): String {
    val date = startIso.take(10)
    val startTime = startIso.substringAfter('T', "").take(5)
    val endTime = endIso.substringAfter('T', "").take(5)
    return when {
        startTime.isEmpty() -> date
        endTime.isEmpty() -> "$date، $startTime"
        else -> "$date، $startTime–$endTime"
    }.toPersianDigits()
}
