package ir.chardivari.feature.seller

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ir.chardivari.core.common.Format
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.PropertyType

/**
 * Listing wizard — type → location → specs → amenities → price → photos →
 * docs → review → publish. Linear, validated at every step; publish runs the
 * real backend pipeline (draft RPC → media upload → ACTIVE transition).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerWizardRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPublished: (String) -> Unit = {},
    onLogin: () -> Unit = {},
    viewModel: SellerWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var photosRejected by remember { mutableIntStateOf(0) }
    var docsRejected by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Re-entry after auth clears a stale login wall.
        viewModel.refreshSession()
    }
    LaunchedEffect(state.publishedListingId) {
        state.publishedListingId?.let(onPublished)
    }

    if (state.requiresLogin) {
        WizardLoginPane(
            modifier = modifier,
            onBack = onBack,
            onLogin = onLogin,
        )
        return
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            photosRejected = viewModel.addPhotos(uris.map { it.toString() })
        }
    }
    val docLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            docsRejected = viewModel.addDocs(uris.map { it.toString() })
        }
    }

    val step = state.step
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${step.titleFa} — " +
                            "گام ${Format.toPersianDigits(step.ordinal + 1)} " +
                            "از ${Format.toPersianDigits(WizardStep.entries.size)}",
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (step == WizardStep.TYPE) onBack() else viewModel.back()
                        },
                    ) {
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
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / WizardStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
            ) {
                when (step) {
                    WizardStep.TYPE -> TypeStep(state, viewModel)
                    WizardStep.LOCATION -> LocationStep(state, viewModel)
                    WizardStep.SPECS -> SpecsStep(state, viewModel)
                    WizardStep.AMENITIES -> AmenitiesStep(state, viewModel)
                    WizardStep.PRICE -> PriceStep(state, viewModel)
                    WizardStep.PHOTOS -> PhotosStep(
                        state = state,
                        viewModel = viewModel,
                        rejected = photosRejected,
                        onPick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    )
                                    .build(),
                            )
                        },
                    )
                    WizardStep.DOCS -> DocsStep(
                        state = state,
                        viewModel = viewModel,
                        rejected = docsRejected,
                        onPick = { docLauncher.launch(arrayOf("application/pdf")) },
                    )
                    WizardStep.REVIEW -> ReviewStep(state)
                }
                state.stepError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.publishing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "در حال انتشار…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            WizardBottomBar(
                state = state,
                onNext = viewModel::next,
                onPublish = viewModel::publish,
            )
        }
    }
}

@Composable
private fun WizardBottomBar(
    state: WizardUi,
    onNext: () -> Unit,
    onPublish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = if (state.step == WizardStep.REVIEW) onPublish else onNext,
            enabled = !state.publishing,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                when {
                    state.step != WizardStep.REVIEW -> "بعدی"
                    state.created != null -> "تلاش دوباره برای انتشار"
                    else -> "انتشار آگهی"
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardLoginPane(modifier: Modifier, onBack: () -> Unit, onLogin: () -> Unit) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("ثبت ملک") },
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
                .padding(padding)
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
                text = "برای ثبت ملک وارد شوید",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            Text(
                text = "آگهی شما با شماره موبایل‌تان ذخیره می‌شود.",
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
}

// ---- Step contents ----

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TypeStep(state: WizardUi, viewModel: SellerWizardViewModel) {
    Text("نوع معامله", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
        DealType.entries.forEach { deal ->
            FilterChip(
                selected = state.draft.dealType == deal,
                onClick = { viewModel.setDealType(deal) },
                label = { Text(dealLabel(deal)) },
            )
        }
    }
    Spacer(Modifier.height(AppSpacing.Sm))
    Text("نوع ملک", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
        PropertyType.entries.forEach { type ->
            FilterChip(
                selected = state.draft.propertyType == type,
                onClick = { viewModel.setPropertyType(type) },
                label = { Text(propertyTypeLabel(type)) },
            )
        }
    }
}

@Composable
private fun LocationStep(state: WizardUi, viewModel: SellerWizardViewModel) {
    OutlinedTextField(
        value = state.draft.province,
        onValueChange = viewModel::setProvince,
        label = { Text("استان") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.draft.city,
        onValueChange = viewModel::setCity,
        label = { Text("شهر") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.draft.neighborhood,
        onValueChange = viewModel::setNeighborhood,
        label = { Text("محله (اختیاری)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SpecsStep(state: WizardUi, viewModel: SellerWizardViewModel) {
    OutlinedTextField(
        value = state.draft.areaSqm,
        onValueChange = viewModel::setAreaSqm,
        label = { Text("متراژ (متر مربع)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        value = state.draft.bedrooms,
        onValueChange = viewModel::setBedrooms,
        label = { Text("تعداد خواب") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
        OutlinedTextField(
            value = state.draft.floor,
            onValueChange = viewModel::setFloor,
            label = { Text("طبقه") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = state.draft.totalFloors,
            onValueChange = viewModel::setTotalFloors,
            label = { Text("تعداد طبقات") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    OutlinedTextField(
        value = state.draft.buildYear,
        onValueChange = viewModel::setBuildYear,
        label = { Text("سال ساخت شمسی (اختیاری)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        value = state.draft.description,
        onValueChange = viewModel::setDescription,
        label = { Text("توضیحات (اختیاری)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6,
    )
}

@Composable
private fun AmenitiesStep(state: WizardUi, viewModel: SellerWizardViewModel) {
    val draft = state.draft
    SwitchRow("آسانسور", draft.hasElevator, viewModel::toggleElevator)
    SwitchRow("پارکینگ", draft.hasParking, viewModel::toggleParking)
    SwitchRow("انباری", draft.hasStorage, viewModel::toggleStorage)
    SwitchRow("بالکن", draft.hasBalcony, viewModel::toggleBalcony)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun PriceStep(state: WizardUi, viewModel: SellerWizardViewModel) {
    val draft = state.draft
    when (draft.dealType) {
        DealType.SALE -> OutlinedTextField(
            value = draft.priceRial,
            onValueChange = viewModel::setPriceRial,
            label = { Text("قیمت فروش (ریال)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        DealType.RENT -> OutlinedTextField(
            value = draft.rentRial,
            onValueChange = viewModel::setRentRial,
            label = { Text("اجاره ماهانه (ریال)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        DealType.RENT_WITH_DEPOSIT -> {
            OutlinedTextField(
                value = draft.depositRial,
                onValueChange = viewModel::setDepositRial,
                label = { Text("رهن (ریال)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = draft.rentRial,
                onValueChange = viewModel::setRentRial,
                label = { Text("اجاره ماهانه (ریال)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        null -> Text(
            text = "ابتدا در گام نخست نوع معامله را انتخاب کنید.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "مبالغ به ریال ثبت می‌شوند.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotosStep(
    state: WizardUi,
    viewModel: SellerWizardViewModel,
    rejected: Int,
    onPick: () -> Unit,
) {
    Text(
        text = "حداقل یک تصویر (JPEG، PNG یا WebP) — تا " +
            "${Format.toPersianDigits(10)} تصویر.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        state.draft.photos.forEach { photo ->
            Box(modifier = Modifier.size(88.dp)) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = { viewModel.removePhoto(photo.uri) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "حذف تصویر",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.background,
                    )
                }
            }
        }
        OutlinedButton(onClick = onPick) {
            Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Text("افزودن تصویر")
        }
    }
    if (rejected > 0) {
        Text(
            text = "${Format.toPersianDigits(rejected)} فایل پذیرفته نشد " +
                "(فرمت پشتیبانی نمی‌شود یا از سقف گذشته است).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DocsStep(
    state: WizardUi,
    viewModel: SellerWizardViewModel,
    rejected: Int,
    onPick: () -> Unit,
) {
    Text(
        text = "سند مالکیت یا پلان (PDF، اختیاری) — برای بررسی سریع‌تر آگهی.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.draft.docs.forEach { doc ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = doc.uri.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { viewModel.removeDoc(doc.uri) }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "حذف سند",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    if (rejected > 0) {
        Text(
            text = "${Format.toPersianDigits(rejected)} فایل پذیرفته نشد — فقط PDF.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedButton(onClick = onPick) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AppSpacing.Xs))
        Text("افزودن سند PDF")
    }
}

@Composable
private fun ReviewStep(state: WizardUi) {
    val draft = state.draft
    Text("بازبینی اطلاعات", style = MaterialTheme.typography.titleSmall)
    ReviewRow("نوع معامله", draft.dealType?.let { dealLabel(it) } ?: "—")
    ReviewRow("نوع ملک", draft.propertyType?.let { propertyTypeLabel(it) } ?: "—")
    ReviewRow(
        "موقعیت",
        listOf(draft.province, draft.city, draft.neighborhood)
            .filter { it.isNotBlank() }
            .joinToString("، ")
            .ifBlank { "—" },
    )
    ReviewRow(
        "متراژ",
        draft.areaSqm.trim().toIntOrNull()?.let { Format.area(it) } ?: "—",
    )
    if (draft.bedrooms.isNotBlank()) {
        ReviewRow("خواب", draft.bedrooms.trim().toIntOrNull()?.let { Format.bedrooms(it) } ?: "—")
    }
    when (draft.dealType) {
        DealType.SALE -> ReviewRow(
            "قیمت فروش",
            draft.priceRial.trim().toLongOrNull()?.let { "${Format.price(it)} ریال" } ?: "—",
        )
        DealType.RENT -> ReviewRow(
            "اجاره ماهانه",
            draft.rentRial.trim().toLongOrNull()?.let { "${Format.price(it)} ریال" } ?: "—",
        )
        DealType.RENT_WITH_DEPOSIT -> {
            ReviewRow(
                "رهن",
                draft.depositRial.trim().toLongOrNull()?.let { "${Format.price(it)} ریال" } ?: "—",
            )
            ReviewRow(
                "اجاره ماهانه",
                draft.rentRial.trim().toLongOrNull()?.let { "${Format.price(it)} ریال" } ?: "—",
            )
        }
        null -> Unit
    }
    ReviewRow(
        "امکانات",
        listOfNotNull(
            "آسانسور".takeIf { draft.hasElevator },
            "پارکینگ".takeIf { draft.hasParking },
            "انباری".takeIf { draft.hasStorage },
            "بالکن".takeIf { draft.hasBalcony },
        ).joinToString("، ").ifBlank { "—" },
    )
    ReviewRow("تصاویر", "${Format.toPersianDigits(draft.photos.size)} فایل")
    ReviewRow("مستندات", "${Format.toPersianDigits(draft.docs.size)} فایل")
    state.publishError?.let { error ->
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
