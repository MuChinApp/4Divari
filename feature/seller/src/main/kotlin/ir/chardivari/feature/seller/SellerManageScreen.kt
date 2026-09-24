package ir.chardivari.feature.seller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ir.chardivari.core.common.Format
import ir.chardivari.core.designsystem.components.EmptyState
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.SellerAction
import ir.chardivari.core.marketplace.SellerListingItem
import ir.chardivari.core.ui.UiStateRenderer

/**
 * Manage screen — list own listings; pause / resume / mark sold / publish.
 * Loading / empty / error / login-required are all first-class states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNewListing: () -> Unit = {},
    onLogin: () -> Unit = {},
    viewModel: SellerManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // Re-entry after auth — retry a stale login-required state.
        val current = viewModel.uiState.value
        if (current is ir.chardivari.core.common.UiState.Content &&
            current.data.requiresLogin
        ) {
            viewModel.load()
        }
    }

    LaunchedEffect(state) {
        val message = (state as? ir.chardivari.core.common.UiState.Content)?.data?.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("آگهی‌های من") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewListing) {
                        Icon(
                            imageVector = Icons.Outlined.Publish,
                            contentDescription = "ثبت ملک جدید",
                        )
                    }
                },
            )
        },
    ) { padding ->
        UiStateRenderer(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            loadingLabel = "در حال بارگذاری آگهی‌ها…",
        ) { ui ->
            when {
                ui.requiresLogin -> LoginRequiredPane(onLogin = onLogin)

                ui.items.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = "هنوز آگهی ثبت نکرده‌اید",
                    description = "ملک خود را در چند مرحله ثبت کنید تا خریداران ببینند.",
                    actionLabel = "ثبت ملک جدید",
                    onAction = onNewListing,
                    icon = Icons.Outlined.Publish,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AppSpacing.Lg,
                        vertical = AppSpacing.Sm,
                    ),
                ) {
                    items(ui.items, key = { it.id }) { item ->
                        SellerRow(
                            item = item,
                            coverUrl = viewModel.storage.value?.let { base ->
                                item.coverPath?.let { path ->
                                    base.trimEnd('/') +
                                        "/object/public/property-media/" +
                                        path.trimStart('/')
                                }
                            },
                            busy = item.id in ui.busyIds,
                            onAction = { action -> viewModel.performAction(item.id, action) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginRequiredPane(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.Lg))
        Text(
            text = "برای ثبت و مدیریت آگهی وارد شوید",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = "با شماره موبایل و کد یک‌بارمصرف.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.Xxl))
        Button(onClick = onLogin) {
            Text("ورود / ثبت‌نام")
        }
    }
}

@Composable
private fun SellerRow(
    item: SellerListingItem,
    coverUrl: String?,
    busy: Boolean,
    onAction: (SellerAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(AppSpacing.Md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (item.dealType) {
                        ir.chardivari.core.marketplace.DealType.SALE ->
                            Format.priceCompact(item.priceRial)
                        ir.chardivari.core.marketplace.DealType.RENT ->
                            "${Format.priceCompact(item.rentRial ?: item.priceRial)}/ماه"
                        ir.chardivari.core.marketplace.DealType.RENT_WITH_DEPOSIT ->
                            "رهن ${Format.priceCompact(item.depositRial ?: item.priceRial)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = statusLabel(item.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (item.status) {
                        "ACTIVE" -> MaterialTheme.colorScheme.primary
                        "SOLD", "RENTED" -> MaterialTheme.colorScheme.tertiary
                        "REJECTED", "EXPIRED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(AppSpacing.Xxs))
            Text(
                text = buildString {
                    item.city?.let { append(it) }
                    item.areaSqm?.let {
                        if (isNotEmpty()) append(" · ")
                        append(Format.area(it))
                    }
                    if (isNotEmpty()) append(" · ")
                    append(dealLabel(item.dealType))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val actions = allowedActions(item.status)
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.Xs))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    actions.forEach { action ->
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            OutlinedButton(
                                onClick = { onAction(action) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = AppSpacing.Md,
                                    vertical = AppSpacing.Xs,
                                ),
                            ) {
                                Text(actionLabel(action))
                            }
                        }
                    }
                }
            }
        }
    }
}
