package ir.chardivari.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.common.UiState
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.AgentDashboard
import ir.chardivari.core.ui.UiStateRenderer

@Composable
fun AgentDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: AgentDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "داشبورد مشاور",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
        )
        UiStateRenderer(
            state = state,
            onRetry = viewModel::load,
            emptyTitle = "هنوز داده‌ای ثبت نشده است",
            loadingLabel = "در حال دریافت آمار…",
            content = { stats -> DashboardGrid(stats = stats) },
        )
    }
}

@Composable
private fun DashboardGrid(stats: AgentDashboard) {
    val rows = listOf(
        Triple(stats.filesActive.toString(), "فایل فعال", stats.filesPaused.toString() to "فایل متوقف"),
        Triple(stats.leadsNew.toString(), "سرنخ جدید", stats.leadsInProgress.toString() to "سرنخ در جریان"),
        Triple(stats.visitsPending.toString(), "بازدید در انتظار", stats.matchesToday.toString() to "تطبیق محاسبه‌شده"),
        Triple(stats.requirementsActive.toString(), "نیاز فعال", "" to ""),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.Lg),
    ) {
        rows.forEach { (leftValue, leftLabel, rightPair) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
            ) {
                MetricCell(
                    value = leftValue,
                    label = leftLabel,
                    modifier = Modifier.weight(1f),
                )
                val (rightValue, rightLabel) = rightPair
                if (rightLabel.isEmpty()) {
                    SpacerWeight()
                } else {
                    MetricCell(
                        value = rightValue,
                        label = rightLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SpacerWeight() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun MetricCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = AppSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ir.chardivari.core.common.Format.toPersianDigits(value.toIntOrNull() ?: 0),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
