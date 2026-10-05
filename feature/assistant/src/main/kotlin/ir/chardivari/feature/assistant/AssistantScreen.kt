package ir.chardivari.feature.assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.ai.AiConfidence
import ir.chardivari.core.ai.AssistantTurn
import ir.chardivari.core.ai.ListingComparison
import ir.chardivari.core.ai.PriceRangeEstimate
import ir.chardivari.core.common.Format
import ir.chardivari.core.designsystem.components.PropertyCard
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.SearchFilters

/**
 * Assistant chat — every rendered number/filter/listing comes from tool
 * executions ([AssistantTurn] artifacts); the prose is the LLM layer.
 */
@Composable
fun AssistantRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onListingClick: (String) -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AssistantScreen(
        state = state,
        onBack = onBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
        onLogin = onLogin,
        onSuggestion = { suggestion ->
            viewModel.onDraftChange(suggestion)
            viewModel.send()
        },
        onListingClick = onListingClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    state: AssistantUiModel,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onLogin: () -> Unit,
    onSuggestion: (String) -> Unit,
    onListingClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دستیار چاردیواری") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
            ) {
                if (state.turns.isEmpty()) {
                    item {
                        EmptyConversation(onSuggestion = onSuggestion)
                    }
                }
                items(state.turns.size) { index ->
                    when (val turn = state.turns[index]) {
                        is AssistantTurnUi.User -> UserBubble(turn.text)
                        is AssistantTurnUi.Answer -> AnswerBlock(
                            turn = turn.turn,
                            onListingClick = onListingClick,
                        )
                    }
                }
                if (state.busy) {
                    item { BusyRow() }
                }
            }

            state.error?.let { error ->
                ErrorRow(
                    error = error,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    onLogin = onLogin,
                )
            }

            InputBar(
                draft = state.draft,
                busy = state.busy,
                onDraftChange = onDraftChange,
                onSend = onSend,
            )
        }
    }
}

@Composable
private fun EmptyConversation(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        "آپارتمان ۸۰ متری در تهران زیر ۵ میلیارد",
        "اجارهٔ دوخوابه در اصفهان",
        "قیمت خانه در شیراز چند است؟",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AppSpacing.Xl))
        Icon(
            imageVector = Icons.Outlined.Assistant,
            contentDescription = null,
            modifier = Modifier.size(AppSpacing.Section),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(AppSpacing.Md))
        Text(
            text = "دستیار جست‌وجوی چاردیواری",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = "بپرسید تا با ابزارهای دادهٔ واقعی جست‌وجو کنم؛ " +
                "بازهٔ قیمت، مقایسه و نتایج همگی از ملک‌های واقعی محاسبه می‌شوند.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.Lg))
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            suggestions.forEach { suggestion ->
                OutlinedButton(
                    onClick = { onSuggestion(suggestion) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(suggestion)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStartPercent = 16, topEndPercent = 16, bottomStartPercent = 16, bottomEndPercent = 4),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(AppSpacing.Md),
            )
        }
    }
}

@Composable
private fun AnswerBlock(
    turn: AssistantTurn,
    onListingClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
        if (turn.text.isNotBlank()) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        turn.fairnessNotice?.let { notice -> FairnessBanner(notice) }

        turn.intent?.let { intent ->
            val chips = filterChips(intent.filters)
            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                ) {
                    chips.forEach { chip -> Pill(label = chip) }
                }
            }
            Text(
                text = "${Format.toPersianDigits(intent.resultCount)} نتیجهٔ واقعی",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            intent.cards.forEach { card ->
                PropertyCard(
                    photoUrl = card.photoUrl,
                    priceLine = card.priceLine,
                    pricePerSqmLine = card.pricePerSqmLine,
                    titleLine = card.titleLine,
                    locationLine = card.locationLine,
                    amenitiesLine = card.amenitiesLine,
                    verificationLabel = card.verificationLabel,
                    updatedLabel = card.updatedLabel,
                    isFixtureData = card.isFixture,
                    onClick = { onListingClick(card.listingId) },
                )
            }
        }

        turn.priceRange?.let { band -> PriceBand(band) }

        turn.comparison?.let { comparison -> ComparisonTable(comparison) }

        if (turn.sources.isNotEmpty()) {
            Text(
                text = "منابع: " + turn.sources.joinToString("، ") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Pill(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 50),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = AppSpacing.Md,
                vertical = AppSpacing.Xs,
            ),
        )
    }
}

@Composable
private fun FairnessBanner(notice: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(topStartPercent = 8, topEndPercent = 8, bottomStartPercent = 8, bottomEndPercent = 8),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(AppSpacing.Lg),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun PriceBand(band: PriceRangeEstimate) {
    val confidenceLabel = when (band.confidence) {
        AiConfidence.LOW -> "اطمینان کم"
        AiConfidence.MEDIUM -> "اطمینان متوسط"
        AiConfidence.HIGH -> "اطمینان زیاد"
    }
    val basisLabel = if (band.basis == "deposit_rial") "پیش‌پرداخت" else "قیمت"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStartPercent = 8, topEndPercent = 8, bottomStartPercent = 8, bottomEndPercent = 8),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            Text(
                text = "بازهٔ تخمینی $basisLabel",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "از ${Format.priceCompact(band.minRial)} تا ${Format.priceCompact(band.maxRial)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "میانه ${Format.priceCompact(band.medianRial)} · " +
                    "چارک ۲۵ تا ۷۵: ${Format.priceCompact(band.p25Rial)} تا " +
                    Format.priceCompact(band.p75Rial),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "بر اساس ${Format.toPersianDigits(band.sampleSize)} نمونهٔ واقعی · $confidenceLabel" +
                    if (band.insufficientSamples) " · زیر ۸ نمونه؛ با احتیاط استفاده کنید" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (band.insufficientSamples) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            band.city?.let { city ->
                Text(
                    text = "شهر: $city · ${ListingPresenter.dealTypeLabel(band.dealType)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComparisonTable(comparison: ListingComparison) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStartPercent = 8, topEndPercent = 8, bottomStartPercent = 8, bottomEndPercent = 8),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            Text(
                text = "مقایسه",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                comparison.columns.forEach { column ->
                    Text(
                        text = column,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            comparison.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    row.values.forEach { value ->
                        Text(
                            text = value ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BusyRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(AppSpacing.Lg),
            strokeWidth = AppSpacing.Xxs,
        )
        Text(
            text = "در حال جست‌وجو در داده‌های واقعی…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorRow(
    error: AssistantSendError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.Md)) {
            Text(
                text = error.messageFa,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                if (error.kind == AssistantErrorKind.LOGIN) {
                    TextButton(onClick = onLogin) {
                        Text("ورود")
                    }
                } else {
                    TextButton(onClick = onRetry) {
                        Text("ارسال دوباره")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("بستن")
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    draft: String,
    busy: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("پیام خود را بنویسید…") },
            singleLine = true,
            enabled = !busy,
        )
        IconButton(
            onClick = onSend,
            enabled = !busy && draft.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Send,
                contentDescription = "ارسال",
            )
        }
    }
}

private fun filterChips(filters: SearchFilters): List<String> = buildList {
    filters.normalizedQuery().takeIf { it.isNotBlank() }?.let { add("«$it»") }
    filters.dealType?.let { add(ListingPresenter.dealTypeLabel(it)) }
    filters.city?.takeIf { it.isNotBlank() }?.let { add(it) }
    filters.minPriceRial?.let { add("از ${Format.priceCompact(it)}") }
    filters.maxPriceRial?.let { add("تا ${Format.priceCompact(it)}") }
    filters.minAreaSqm?.let { add("از ${Format.area(it)}") }
    filters.maxAreaSqm?.let { add("تا ${Format.area(it)}") }
    filters.minBedrooms?.let { add("${Format.toPersianDigits(it)} خوابه") }
}
