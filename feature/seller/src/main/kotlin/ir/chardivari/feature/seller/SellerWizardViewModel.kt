package ir.chardivari.feature.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.SellerAction
import ir.chardivari.core.marketplace.SellerDraftCreated
import ir.chardivari.core.marketplace.SellerRepository
import ir.chardivari.core.network.SessionTokenProvider
import ir.chardivari.core.ui.errorDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Linear wizard order — type → location → specs → amenities → price → photos → docs → review. */
enum class WizardStep(val titleFa: String) {
    TYPE("نوع ملک و معامله"),
    LOCATION("موقعیت"),
    SPECS("مشخصات"),
    AMENITIES("امکانات"),
    PRICE("قیمت"),
    PHOTOS("تصاویر"),
    DOCS("مستندات"),
    REVIEW("بازبینی و انتشار"),
    ;
}

data class PickedMedia(val uri: String, val mimeType: String)

/** Wizard form state — every field is raw input text until validated. */
data class WizardDraft(
    val dealType: DealType? = null,
    val propertyType: PropertyType? = null,
    val province: String = "",
    val city: String = "",
    val neighborhood: String = "",
    val areaSqm: String = "",
    val bedrooms: String = "",
    val floor: String = "",
    val totalFloors: String = "",
    val buildYear: String = "",
    val hasElevator: Boolean = false,
    val hasParking: Boolean = false,
    val hasStorage: Boolean = false,
    val hasBalcony: Boolean = false,
    val priceRial: String = "",
    val depositRial: String = "",
    val rentRial: String = "",
    val description: String = "",
    val photos: List<PickedMedia> = emptyList(),
    val docs: List<PickedMedia> = emptyList(),
)

data class WizardUi(
    val step: WizardStep = WizardStep.TYPE,
    val draft: WizardDraft = WizardDraft(),
    /** Validation error for the current step (blocks «بعدی»). */
    val stepError: String? = null,
    /** Terminal publish progress — UI shows determinate indeterminate bar. */
    val publishing: Boolean = false,
    val publishError: String? = null,
    val requiresLogin: Boolean = false,
    /** Set once draft exists server-side so retries never double-create. */
    val created: SellerDraftCreated? = null,
    /** Media already uploaded (uris) — skipped on publish retry. */
    val uploadedUris: Set<String> = emptySet(),
    /** Terminal success — route navigates to manage. */
    val publishedListingId: String? = null,
)

@HiltViewModel
class SellerWizardViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
    private val mediaReader: MediaBytesReader,
    private val session: SessionTokenProvider,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUi())
    val uiState: StateFlow<WizardUi> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ScreenView("seller_wizard"))
    }

    /**
     * Called on route re-entry — clears a stale login wall after the user
     * returned from the auth screen with a fresh session.
     */
    fun refreshSession() {
        if (session.currentUserId() != null && _uiState.value.requiresLogin) {
            _uiState.value = _uiState.value.copy(requiresLogin = false)
        }
    }

    fun onStepSelected(step: WizardStep) {
        val current = _uiState.value
        if (current.publishing) return
        // Navigating backwards always allowed; forward requires validation.
        if (step.ordinal <= current.step.ordinal) {
            _uiState.value = current.copy(step = step, stepError = null, publishError = null)
        } else {
            goTo(step)
        }
    }

    fun next() {
        val current = _uiState.value
        val error = current.draft.validateStep(current.step)
        if (error != null) {
            _uiState.value = current.copy(stepError = error)
            return
        }
        val nextStep = WizardStep.entries.getOrNull(current.step.ordinal + 1) ?: return
        _uiState.value = current.copy(step = nextStep, stepError = null, publishError = null)
    }

    fun back() {
        val current = _uiState.value
        val prev = WizardStep.entries.getOrNull(current.step.ordinal - 1) ?: return
        _uiState.value = current.copy(step = prev, stepError = null, publishError = null)
    }

    private fun goTo(step: WizardStep) {
        val current = _uiState.value
        val firstInvalid = current.draft.firstInvalidStepBefore(step)
        if (firstInvalid != null) {
            _uiState.value = current.copy(
                step = firstInvalid,
                stepError = current.draft.validateStep(firstInvalid),
            )
            return
        }
        _uiState.value = current.copy(step = step, stepError = null)
    }

    // ---- field edits (validation re-checks only on next/publish) ----

    fun setDealType(value: DealType) = updateDraft { it.copy(dealType = value) }

    fun setPropertyType(value: PropertyType) = updateDraft { it.copy(propertyType = value) }

    fun setProvince(value: String) = updateDraft { it.copy(province = value) }

    fun setCity(value: String) = updateDraft { it.copy(city = value) }

    fun setNeighborhood(value: String) = updateDraft { it.copy(neighborhood = value) }

    fun setAreaSqm(value: String) = updateDraft { it.copy(areaSqm = value) }

    fun setBedrooms(value: String) = updateDraft { it.copy(bedrooms = value) }

    fun setFloor(value: String) = updateDraft { it.copy(floor = value) }

    fun setTotalFloors(value: String) = updateDraft { it.copy(totalFloors = value) }

    fun setBuildYear(value: String) = updateDraft { it.copy(buildYear = value) }

    fun setDescription(value: String) = updateDraft { it.copy(description = value) }

    fun setPriceRial(value: String) = updateDraft { it.copy(priceRial = value) }

    fun setDepositRial(value: String) = updateDraft { it.copy(depositRial = value) }

    fun setRentRial(value: String) = updateDraft { it.copy(rentRial = value) }

    fun toggleElevator() = updateDraft { it.copy(hasElevator = !it.hasElevator) }

    fun toggleParking() = updateDraft { it.copy(hasParking = !it.hasParking) }

    fun toggleStorage() = updateDraft { it.copy(hasStorage = !it.hasStorage) }

    fun toggleBalcony() = updateDraft { it.copy(hasBalcony = !it.hasBalcony) }

    fun clearPublishError() {
        _uiState.value = _uiState.value.copy(publishError = null)
    }

    /**
     * @return number of rejected entries (unsupported format or over limit).
     */
    fun addPhotos(uris: List<String>): Int {
        var rejected = 0
        updateDraft { draft ->
            val existing = draft.photos.map { it.uri }.toSet()
            val accepted = mutableListOf<PickedMedia>()
            uris.forEach { uri ->
                if (uri in existing || accepted.any { it.uri == uri }) return@forEach
                val mime = mediaReader.mimeTypeOf(uri)
                if (mime == null || mime !in SUPPORTED_IMAGE_MIMES) {
                    rejected++
                } else if (draft.photos.size + accepted.size >= MAX_PHOTOS) {
                    rejected++
                } else {
                    accepted += PickedMedia(uri = uri, mimeType = mime)
                }
            }
            draft.copy(photos = draft.photos + accepted)
        }
        val current = _uiState.value
        if (current.step == WizardStep.PHOTOS && current.stepError != null) {
            _uiState.value = current.copy(stepError = null)
        }
        return rejected
    }

    fun addDocs(uris: List<String>): Int {
        var rejected = 0
        updateDraft { draft ->
            val accepted = uris.mapNotNull { uri ->
                if (draft.docs.any { it.uri == uri }) return@mapNotNull null
                val mime = mediaReader.mimeTypeOf(uri)
                if (mime == "application/pdf") {
                    PickedMedia(uri = uri, mimeType = mime)
                } else {
                    rejected++
                    null
                }
            }
            draft.copy(docs = draft.docs + accepted)
        }
        return rejected
    }

    fun removePhoto(uri: String) = updateDraft {
        it.copy(photos = it.photos.filterNot { p -> p.uri == uri })
    }

    fun removeDoc(uri: String) = updateDraft {
        it.copy(docs = it.docs.filterNot { d -> d.uri == uri })
    }

    // ---- publish ----

    fun publish() {
        val state = _uiState.value
        if (state.publishing || state.publishedListingId != null) return

        val invalidStep = state.draft.firstInvalidStep()
        if (invalidStep != null) {
            _uiState.value = state.copy(
                step = invalidStep,
                stepError = state.draft.validateStep(invalidStep),
            )
            return
        }
        if (session.currentUserId() == null) {
            _uiState.value = state.copy(requiresLogin = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                publishing = true,
                publishError = null,
                stepError = null,
                requiresLogin = false,
            )

            val draftState = _uiState.value
            val created = draftState.created ?: when (
                val result = sellerRepository.createDraft(
                    property = draftState.draft.propertyPayload(),
                    listing = draftState.draft.listingPayload(),
                )
            ) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(created = result.data)
                    result.data
                }
                is AppResult.Failure -> return@launch fail(error = result.error, draftSaved = false)
                AppResult.Empty -> return@launch fail(error = AppError.Serialization, draftSaved = false)
            }

            for ((index, media) in draftState.draft.photos.withIndex()) {
                if (media.uri in draftState.uploadedUris) continue
                val bytes = mediaReader.read(media.uri)
                    ?: return@launch fail(error = AppError.Unexpected, draftSaved = true, created = created)
                when (
                    val upload = sellerRepository.uploadMedia(
                        propertyId = created.propertyId,
                        mimeType = media.mimeType,
                        bytes = bytes,
                        mediaType = "image",
                        sortOrder = index,
                        isCover = index == 0,
                    )
                ) {
                    is AppResult.Success -> markUploaded(media.uri)
                    is AppResult.Failure -> return@launch fail(error = upload.error, draftSaved = true, created = created)
                    AppResult.Empty -> return@launch fail(error = AppError.Unexpected, draftSaved = true, created = created)
                }
            }
            for ((offset, media) in draftState.draft.docs.withIndex()) {
                if (media.uri in draftState.uploadedUris) continue
                val bytes = mediaReader.read(media.uri)
                    ?: return@launch fail(error = AppError.Unexpected, draftSaved = true, created = created)
                when (
                    val upload = sellerRepository.uploadMedia(
                        propertyId = created.propertyId,
                        mimeType = media.mimeType,
                        bytes = bytes,
                        mediaType = "document",
                        sortOrder = draftState.draft.photos.size + offset,
                        isCover = false,
                    )
                ) {
                    is AppResult.Success -> markUploaded(media.uri)
                    is AppResult.Failure -> return@launch fail(error = upload.error, draftSaved = true, created = created)
                    AppResult.Empty -> return@launch fail(error = AppError.Unexpected, draftSaved = true, created = created)
                }
            }

            when (
                val transition = sellerRepository.setStatus(created.listingId, SellerAction.PUBLISH)
            ) {
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.ListingCreated(created.listingId))
                    _uiState.value = _uiState.value.copy(
                        publishing = false,
                        publishedListingId = created.listingId,
                    )
                }
                is AppResult.Failure -> fail(error = transition.error, draftSaved = true, created = created)
                AppResult.Empty -> fail(error = AppError.Serialization, draftSaved = true, created = created)
            }
        }
    }

    private fun markUploaded(uri: String) {
        _uiState.value = _uiState.value.copy(
            uploadedUris = _uiState.value.uploadedUris + uri,
        )
    }

    private fun fail(error: AppError, draftSaved: Boolean, created: SellerDraftCreated? = null) {
        val prefix = if (draftSaved) "پیش‌نویس ذخیره شد اما انتشار کامل نشد. " else ""
        _uiState.value = _uiState.value.copy(
            publishing = false,
            created = created ?: _uiState.value.created,
            publishError = prefix + errorDescription(error),
        )
    }

    private inline fun updateDraft(block: (WizardDraft) -> WizardDraft) {
        val state = _uiState.value
        _uiState.value = state.copy(draft = block(state.draft))
    }

    private companion object {
        const val MAX_PHOTOS = 10
        val SUPPORTED_IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
