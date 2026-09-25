package ir.chardivari.feature.property

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ir.chardivari.core.common.toPersianDigits
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.designsystem.components.VerificationBadge
import ir.chardivari.core.ui.UiStateRenderer
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PropertyDetailRoute(
    modifier: Modifier = Modifier,
    viewModel: PropertyDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onLogin: () -> Unit = {},
    onOpenThread: (conversationId: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state) {
        val content = state as? ir.chardivari.core.common.UiState.Content ?: return@LaunchedEffect
        if (content.data.favoriteError == "برای ذخیره ملک وارد حساب خود شوید") {
            onLogin()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PropertyDetailEvent.OpenThread -> onOpenThread(event.conversationId)
                PropertyDetailEvent.LoginRequired -> onLogin()
            }
        }
    }

    val visitDialog =
        (state as? ir.chardivari.core.common.UiState.Content)?.data?.visitDialog
    if (visitDialog != null) {
        VisitPickerDialog(
            dialog = visitDialog,
            onDate = viewModel::setVisitDate,
            onTime = viewModel::setVisitTime,
            onDismiss = viewModel::closeVisitDialog,
            onSubmit = viewModel::submitVisit,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "بازگشت",
                    )
                }
                Row {
                    IconButton(onClick = {
                        viewModel.share()
                        val model = (state as? ir.chardivari.core.common.UiState.Content)
                            ?.data?.detail ?: return@IconButton
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, model.shareText)
                        }
                        context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری"))
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "اشتراک‌گذاری",
                        )
                    }
                }
            }
        },
    ) { padding ->
        UiStateRenderer(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            emptyTitle = "این فایل در دسترس نیست",
            emptyDescription = "ممکن است فروخته یا غیرفعال شده باشد.",
            loadingLabel = "در حال بارگذاری ملک…",
        ) { model ->
            val detail = model.detail
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AppSpacing.Xxl),
            ) {
                if (detail.photoUrls.isEmpty()) {
                    item {
                        Text(
                            text = "تصویری برای این فایل ثبت نشده است",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.Xxl),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    itemsIndexed(detail.photoUrls) { index, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier.padding(AppSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                    ) {
                        if (detail.isFixture) {
                            ir.chardivari.core.designsystem.components.DataProvenanceTag(
                                isFixture = true,
                            )
                        }
                        Text(
                            text = detail.priceLine,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = detail.pricePerSqmLine,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        detail.depositLine?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = detail.titleLine,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = detail.locationLine,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val vLabel = detail.verificationLabel
                        if (vLabel != null) {
                            VerificationBadge(label = vLabel)
                        }
                        Text(
                            text = detail.updatedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = if (detail.isFavorited) {
                                    Icons.Outlined.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = null,
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(if (detail.isFavorited) "ذخیره‌شده" else "ذخیره")
                        }
                        Button(
                            onClick = {
                                viewModel.loadContact()
                                val phone = model.contact?.phoneE164
                                if (phone != null) {
                                    viewModel.contactOpened()
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:$phone"),
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !model.contactLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("تماس")
                        }
                    }
                    model.favoriteError?.let { msg ->
                        Text(
                            text = msg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.Lg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    model.contact?.let { contact ->
                        Text(
                            text = contact.displayName?.let { "$it — ${contact.phoneE164}" }
                                ?: contact.phoneE164,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    model.contactError?.let { msg ->
                        Text(
                            text = msg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.Lg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openVisitDialog() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("درخواست بازدید")
                        }
                        OutlinedButton(
                            onClick = { viewModel.openConversation() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("پیام")
                        }
                    }
                    model.visitMessage?.let { msg ->
                        Text(
                            text = msg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.Lg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    model.conversationError?.let { msg ->
                        Text(
                            text = msg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.Lg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.Lg,
                            vertical = AppSpacing.Md,
                        ),
                    )
                    Text(
                        text = "مشخصات",
                        modifier = Modifier.padding(horizontal = AppSpacing.Lg),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(AppSpacing.Sm))
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                    ) {
                        detail.specsLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                detail.description?.let { description ->
                    item {
                        Spacer(Modifier.height(AppSpacing.Lg))
                        Text(
                            text = "توضیحات",
                            modifier = Modifier.padding(horizontal = AppSpacing.Lg),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(AppSpacing.Sm))
                        Text(
                            text = description,
                            modifier = Modifier.padding(horizontal = AppSpacing.Lg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visit-slot picker — ISO slot + fixed +03:30 (no Jalali module yet; same
 * honest date approach as the rest of the app).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitPickerDialog(
    dialog: VisitDialogUi,
    onDate: (Long?) -> Unit,
    onTime: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("درخواست بازدید") },
        text = {
            Column {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val dateLabel = dialog.dateMillis?.let {
                        buildSlotIso(it, hour = 0, minute = 0, plusHours = 0)
                            .take(10)
                            .replace('-', '/')
                            .toPersianDigits()
                    } ?: "انتخاب تاریخ"
                    Text(dateLabel)
                }
                Spacer(Modifier.height(AppSpacing.Md))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    TimeMenu(
                        label = "ساعت: " + dialog.hour.toPersianDigits().padStart(2, '\u200c'),
                        options = (8..21).toList(),
                        selected = dialog.hour,
                        format = { it.toPersianDigits() },
                        onSelect = { hour -> onTime(hour, dialog.minute) },
                    )
                    TimeMenu(
                        label = "دقیقه: " + dialog.minute.toPersianDigits(),
                        options = listOf(0, 15, 30, 45),
                        selected = dialog.minute,
                        format = { it.toPersianDigits().padStart(2, '\u200c') },
                        onSelect = { minute -> onTime(dialog.hour, minute) },
                    )
                }
                dialog.error?.let { msg ->
                    Spacer(Modifier.height(AppSpacing.Sm))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !dialog.busy,
            ) {
                Text(if (dialog.busy) "در حال ثبت…" else "ارسال درخواست")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !dialog.busy,
            ) {
                Text("انصراف")
            }
        },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dialog.dateMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDate(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    },
                ) {
                    Text("تأیید")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("انصراف")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TimeMenu(
    label: String,
    options: List<Int>,
    selected: Int,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(if (options.contains(selected)) label else label.substringBefore(':'))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(format(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
