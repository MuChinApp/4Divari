package ir.chardivari.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.common.Format
import ir.chardivari.core.common.UiState
import ir.chardivari.core.common.toPersianDigits
import ir.chardivari.core.designsystem.components.EmptyState
import ir.chardivari.core.designsystem.components.ErrorState
import ir.chardivari.core.designsystem.components.LoadingState
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.QueueReport
import ir.chardivari.core.marketplace.QueueVerification
import ir.chardivari.core.marketplace.RiskFlag
import ir.chardivari.core.ui.errorDescription
import ir.chardivari.core.ui.errorTitle

/**
 * Admin moderation — honest gate first, then the two queues. Risk chips show
 * server-computed flags with evidence; nothing here invents a score.
 */
@Composable
fun AdminRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onLogin: () -> Unit = {},
    gateViewModel: AdminGateViewModel = hiltViewModel(),
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val gateState by gateViewModel.uiState.collectAsStateWithLifecycle()

    when (val gate = gateState) {
        UiState.Idle, UiState.Loading, UiState.Empty -> LoadingState(
            modifier = Modifier.fillMaxSize(),
            label = "در حال بررسی دسترسی…",
        )
        is UiState.Error -> ErrorState(
            modifier = Modifier.fillMaxSize(),
            title = errorTitle(gate.error),
            description = errorDescription(gate.error),
            canRetry = gate.canRetry,
            onRetry = gateViewModel::load,
        )
        is UiState.Content -> when (gate.data) {
            AdminGate.Loading -> LoadingState(
                modifier = Modifier.fillMaxSize(),
                label = "در حال بررسی دسترسی…",
            )
            AdminGate.NotLoggedIn -> GateMessage(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.Flag,
                title = "ورود لازم است",
                description = "برای دسترسی به مدیریت محتوا وارد حساب خود شوید.",
                primaryActionLabel = "ورود / ثبت‌نام",
                onPrimaryAction = { onLogin() },
                onBack = onBack,
            )
            AdminGate.NotAdmin -> GateMessage(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.Flag,
                title = "دسترسی مدیر ندارید",
                description = "این بخش فقط برای حساب‌های مدیر فعال است.",
                primaryActionLabel = null,
                onPrimaryAction = null,
                onBack = onBack,
            )
            AdminGate.Ready -> AdminContent(
                modifier = modifier,
                viewModel = viewModel,
                onBack = onBack,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminContent(
    modifier: Modifier,
    viewModel: AdminViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("مدیریت محتوا") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::load) {
                        Text("تازه‌سازی")
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        when {
            current is UiState.Error -> ErrorState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title = errorTitle(current.error),
                description = errorDescription(current.error),
                canRetry = current.canRetry,
                onRetry = viewModel::load,
            )
            current is UiState.Content && current.data.queue != null -> QueueBody(
                ui = current.data,
                padding = padding,
                onResolve = viewModel::resolveReport,
                onDecide = viewModel::decideVerification,
                onClearMessage = viewModel::clearMessage,
            )
            else -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun QueueBody(
    ui: AdminUi,
    padding: PaddingValues,
    onResolve: (reportId: String, status: String) -> Unit,
    onDecide: (listingId: String, status: String) -> Unit,
    onClearMessage: () -> Unit,
) {
    val queue = ui.queue ?: return
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = {
                    Text("گزارش‌ها (${queue.openReports.toString().toPersianDigits()})")
                },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = {
                    Text("تأیید آگهی (${queue.pendingVerifications.toString().toPersianDigits()})")
                },
            )
        }

        ui.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearMessage) {
                    Text("بستن")
                }
            }
        }

        when (tab) {
            0 -> if (queue.reports.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = "گزارش بازی نیست",
                    description = "گزارش‌های کاربران اینجا جمع می‌شوند.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AppSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    items(queue.reports, key = { it.id }) { report ->
                        ReportRow(
                            report = report,
                            busy = report.id in ui.busyIds,
                            onResolve = onResolve,
                        )
                    }
                }
            }
            else -> if (queue.verifications.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = "آگهی در انتظار تأیید نیست",
                    description = "درخواست‌های تأیید فروشندگان اینجا نمایش داده می‌شوند.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AppSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    items(queue.verifications, key = { it.listingId }) { item ->
                        VerificationRow(
                            item = item,
                            busy = item.listingId in ui.busyIds,
                            onDecide = onDecide,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    report: QueueReport,
    busy: Boolean,
    onResolve: (reportId: String, status: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(AppSpacing.Sm))
                Text(
                    text = report.reason,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            report.detail?.let { detail ->
                Spacer(Modifier.height(AppSpacing.Xxs))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(AppSpacing.Xs))
            Text(
                text = listOfNotNull(
                    report.listing?.address ?: "ملک حذف‌شده",
                    report.listing?.priceRial?.let { Format.price(it) },
                ).joinToString(" — "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            RiskChipRow(risks = report.risk)
            Spacer(Modifier.height(AppSpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                FilledTonalButton(
                    onClick = { onResolve(report.id, "actioned") },
                    enabled = !busy,
                ) {
                    Text("اقدام شد")
                }
                OutlinedButton(
                    onClick = { onResolve(report.id, "dismissed") },
                    enabled = !busy,
                ) {
                    Text("رد گزارش")
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun VerificationRow(
    item: QueueVerification,
    busy: Boolean,
    onDecide: (listingId: String, status: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(AppSpacing.Sm))
                Text(
                    text = item.listing?.address ?: "ملک",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(AppSpacing.Xs))
            Text(
                text = listOfNotNull(
                    item.listing?.priceRial?.let { Format.price(it) },
                    item.listing?.freshness?.let { freshnessLabel(it) },
                ).joinToString(" — "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            RiskChipRow(risks = item.risk)
            Spacer(Modifier.height(AppSpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                FilledTonalButton(
                    onClick = { onDecide(item.listingId, "verified") },
                    enabled = !busy,
                ) {
                    Text("تأیید")
                }
                OutlinedButton(
                    onClick = { onDecide(item.listingId, "rejected") },
                    enabled = !busy,
                ) {
                    Text("رد")
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RiskChipRow(risks: List<RiskFlag>) {
    if (risks.isEmpty()) {
        Text(
            text = "نشانهٔ ریسکی ندارد",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
        risks.forEach { risk ->
            RiskChip(risk = risk)
        }
    }
}

@Composable
private fun RiskChip(risk: RiskFlag) {
    val container = when (risk.severity) {
        "high" -> MaterialTheme.colorScheme.errorContainer
        "medium" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when (risk.severity) {
        "high" -> MaterialTheme.colorScheme.onErrorContainer
        "medium" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = riskLabel(risk.code),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xxs),
    )
}

/** Server risk codes → Persian (backend stays language-neutral). */
internal fun riskLabel(code: String): String = when (code) {
    "OPEN_REPORTS" -> "گزارش باز"
    "STALE_DATA" -> "داده قدیمی"
    "PRICE_OUTLIER_HIGH" -> "قیمت بسیار بالاتر از میانگین"
    "PRICE_OUTLIER_LOW" -> "قیمت بسیار پایین‌تر از میانگین"
    "PRICE_UNKNOWN" -> "داده کافی برای قیمت نیست"
    else -> code
}

internal fun freshnessLabel(freshness: String): String = when (freshness) {
    "fresh" -> "به‌روز"
    "aging" -> "در حال قدیمی‌شدن"
    "stale" -> "قدیمی"
    else -> freshness
}

@Composable
private fun GateMessage(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    primaryActionLabel: String?,
    onPrimaryAction: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier.padding(AppSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.width(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.Lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.Xl))
        if (primaryActionLabel != null && onPrimaryAction != null) {
            OutlinedButton(onClick = onPrimaryAction) {
                Text(primaryActionLabel)
            }
            Spacer(Modifier.height(AppSpacing.Md))
        }
        TextButton(onClick = onBack) {
            Text("بازگشت")
        }
    }
}
