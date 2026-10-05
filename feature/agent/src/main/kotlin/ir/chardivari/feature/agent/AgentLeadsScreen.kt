package ir.chardivari.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.common.Format
import ir.chardivari.core.common.UiState
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.ui.UiStateRenderer

private val FEATURE_LABELS = listOf(
    "has_elevator" to "آسانسور",
    "has_parking" to "پارکینگ",
    "has_storage" to "انباری",
    "has_balcony" to "بالکنه",
)

@Composable
fun AgentLeadsScreen(
    modifier: Modifier = Modifier,
    viewModel: AgentLeadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "سرنخ‌های من",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
        )
        UiStateRenderer(
            state = state,
            onRetry = viewModel::load,
            emptyTitle = "هنوز سرنخی ثبت نشده است",
            emptyDescription = "سرنخ‌ها از تماس خریداران با فایل‌های شما ساخته می‌شوند.",
            loadingLabel = "در حال دریافت سرنخ‌ها…",
            content = { data ->
                Column(modifier = Modifier.fillMaxSize()) {
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
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items = data.leads, key = { it.id }) { lead ->
                            LeadRow(
                                lead = lead,
                                expanded = data.expandedId == lead.id,
                                busy = lead.id in data.busyIds,
                                matches = if (data.expandedId == lead.id) data.matches else emptyList(),
                                matchesLoading = data.matchesLoading,
                                onToggle = { viewModel.toggleExpanded(lead.id) },
                                onStageChange = { viewModel.setStage(lead.id, it) },
                                onPriorityChange = { viewModel.setPriority(lead.id, it) },
                                onEditNotes = { viewModel.openNotesEditor(lead.id) },
                                onOpenRequirement = { viewModel.openRequirementForm(lead.id) },
                                onRefreshMatches = { viewModel.refreshMatches(lead.id) },
                            )
                        }
                    }
                }
                data.form?.let { form ->
                    RequirementDialog(
                        form = form,
                        onDismiss = viewModel::closeRequirementForm,
                        onUpdate = viewModel::updateForm,
                        onSave = viewModel::saveRequirement,
                    )
                }
                data.notesEditor?.let { editor ->
                    NotesDialog(
                        editor = editor,
                        onDismiss = viewModel::closeNotesEditor,
                        onTextChange = viewModel::setNotesText,
                        onSave = viewModel::saveNotes,
                    )
                }
            },
        )
    }
}

@Composable
private fun LeadRow(
    lead: ir.chardivari.core.marketplace.AgentLead,
    expanded: Boolean,
    busy: Boolean,
    matches: List<ir.chardivari.core.marketplace.LeadMatch>,
    matchesLoading: Boolean,
    onToggle: () -> Unit,
    onStageChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onEditNotes: () -> Unit,
    onOpenRequirement: () -> Unit,
    onRefreshMatches: () -> Unit,
) {
    var stageMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onToggle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lead.address ?: "بدون آدرس",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(lead.buyerName ?: "خریدار")
                        append(" · ")
                        append(lead.buyerPhone ?: "بدون شماره")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                lead.priceRial?.let {
                    Text(
                        text = Format.price(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                AssistChip(
                    onClick = { if (!busy) stageMenu = true },
                    label = { Text(stageLabel(lead.stage)) },
                    enabled = !busy,
                )
                DropdownMenu(
                    expanded = stageMenu,
                    onDismissRequest = { stageMenu = false },
                ) {
                    LEAD_STAGES.forEach { stage ->
                        DropdownMenuItem(
                            text = { Text(stageLabel(stage)) },
                            onClick = {
                                stageMenu = false
                                onStageChange(stage)
                            },
                        )
                    }
                }
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Lg)
                    .padding(bottom = AppSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "اولویت: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = lead.priority == level,
                            onClick = { if (!busy) onPriorityChange(level) },
                            label = { Text(level.toString()) },
                            modifier = Modifier.padding(end = 4.dp),
                            enabled = !busy,
                        )
                    }
                }

                Text(
                    text = lead.notes?.takeIf { it.isNotBlank() } ?: "یادداشتی ثبت نشده است",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lead.notes.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    OutlinedButton(onClick = onEditNotes, enabled = !busy) {
                        Text("ویرایش یادداشت")
                    }
                    OutlinedButton(onClick = onOpenRequirement, enabled = !busy) {
                        Text("نیاز خریدار")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "تطبیق با فایل‌ها",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefreshMatches, enabled = !matchesLoading && !busy) {
                        Text(if (matchesLoading) "در حال محاسبه…" else "محاسبه مجدد")
                    }
                }
                when {
                    matchesLoading -> Text(
                        text = "در حال محاسبه تطبیق…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    matches.isEmpty() -> Text(
                        text = "تطبیقی ثبت نشده است؛ «نیاز خریدار» را کامل و سپس محاسبه کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> matches.forEach { match ->
                        MatchRow(match = match)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MatchRow(match: ir.chardivari.core.marketplace.LeadMatch) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = match.address ?: "بدون آدرس",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${Format.toPersianDigits(match.score.toInt())}٪",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val detail = buildList {
            match.priceRial?.let { add(Format.price(it)) }
            match.explanation?.budget?.let { add("بودجه: ${fitLabel(it)}") }
            match.explanation?.area?.let { add("متراژ: ${fitLabel(it)}") }
        }.joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun fitLabel(value: String): String = when (value) {
    "exact" -> "منطبق"
    "tolerant" -> "نزدیک"
    "miss" -> "نامطابق"
    "absent" -> "—"
    else -> value
}

@Composable
private fun RequirementDialog(
    form: RequirementFormUi,
    onDismiss: () -> Unit,
    onUpdate: (transform: (RequirementFormUi) -> RequirementFormUi) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!form.busy) onDismiss() },
        title = { Text("نیاز خریدار") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                Text(
                    text = "نوع معامله طبق فایل: ${ListingPresenter.dealTypeLabel(form.dealType)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = form.budgetMin,
                    onValueChange = { v -> onUpdate { it.copy(budgetMin = v) } },
                    label = { Text("حداقل بودجه (ریال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !form.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.budgetMax,
                    onValueChange = { v -> onUpdate { it.copy(budgetMax = v) } },
                    label = { Text("حداکثر بودجه (ریال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !form.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    OutlinedTextField(
                        value = form.areaMin,
                        onValueChange = { v -> onUpdate { it.copy(areaMin = v) } },
                        label = { Text("حداقل متراژ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !form.busy,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.areaMax,
                        onValueChange = { v -> onUpdate { it.copy(areaMax = v) } },
                        label = { Text("حداکثر متراژ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !form.busy,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "تعداد خواب",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    (1..5).forEach { count ->
                        FilterChip(
                            selected = count in form.bedrooms,
                            onClick = {
                                if (!form.busy) {
                                    onUpdate {
                                        it.copy(
                                            bedrooms = if (count in it.bedrooms) {
                                                it.bedrooms - count
                                            } else {
                                                it.bedrooms + count
                                            },
                                        )
                                    }
                                }
                            },
                            label = { Text(Format.toPersianDigits(count)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.citiesText,
                    onValueChange = { v -> onUpdate { it.copy(citiesText = v) } },
                    label = { Text("شهرها (با «،» جدا کنید)") },
                    singleLine = true,
                    enabled = !form.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "امکانات",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FEATURE_LABELS.forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = key in form.features,
                            onClick = {
                                if (!form.busy) {
                                    onUpdate {
                                        it.copy(
                                            features = if (key in it.features) {
                                                it.features - key
                                            } else {
                                                it.features + key
                                            },
                                        )
                                    }
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                form.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !form.busy) {
                Text(if (form.busy) "در حال ذخیره…" else "ذخیره و محاسبه تطبیق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.busy) {
                Text("انصراف")
            }
        },
    )
}

@Composable
private fun NotesDialog(
    editor: NotesEditorUi,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.busy) onDismiss() },
        title = { Text("یادداشت سرنخ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                OutlinedTextField(
                    value = editor.text,
                    onValueChange = onTextChange,
                    label = { Text("یادداشت (حداکثر ۴۰۰۰ نویسه)") },
                    enabled = !editor.busy,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                editor.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.busy) {
                Text(if (editor.busy) "در حال ذخیره…" else "ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.busy) {
                Text("انصراف")
            }
        },
    )
}
