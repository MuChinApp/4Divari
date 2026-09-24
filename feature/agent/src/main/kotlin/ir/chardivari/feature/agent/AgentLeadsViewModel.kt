package ir.chardivari.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.common.toAsciiIntOrNull
import ir.chardivari.core.common.toAsciiLongOrNull
import ir.chardivari.core.marketplace.AgentLead
import ir.chardivari.core.marketplace.AgentRepository
import ir.chardivari.core.marketplace.BuyerRequirement
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.LeadMatch
import ir.chardivari.core.marketplace.RequirementInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** CRM stage ids — mirror of the `lead_stage` enum (server stays source of truth). */
internal val LEAD_STAGES = listOf(
    "NEW",
    "CONTACTED",
    "QUALIFIED",
    "VISIT_REQUESTED",
    "VISITED",
    "NEGOTIATING",
    "OFFER",
    "CLOSED",
    "LOST",
)

internal fun stageLabel(stage: String): String = when (stage) {
    "NEW" -> "جدید"
    "CONTACTED" -> "تماس گرفته"
    "QUALIFIED" -> "واجد شرایط"
    "VISIT_REQUESTED" -> "درخواست بازدید"
    "VISITED" -> "بازدیدشده"
    "NEGOTIATING" -> "مذاکره"
    "OFFER" -> "پیشنهاد"
    "CLOSED" -> "بسته‌شده"
    "LOST" -> "ازدست‌رفته"
    else -> stage
}

/** Requirement form — screen state; parsing happens in the ViewModel. */
internal data class RequirementFormUi(
    val leadId: String,
    val dealType: DealType,
    val budgetMin: String = "",
    val budgetMax: String = "",
    val areaMin: String = "",
    val areaMax: String = "",
    val bedrooms: Set<Int> = emptySet(),
    val citiesText: String = "",
    val features: Set<String> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
)

internal data class NotesEditorUi(
    val leadId: String,
    val text: String,
    val busy: Boolean = false,
)

internal data class AgentLeadsUi(
    val leads: List<AgentLead> = emptyList(),
    val expandedId: String? = null,
    val busyIds: Set<String> = emptySet(),
    val matches: List<LeadMatch> = emptyList(),
    val matchesLoading: Boolean = false,
    val message: String? = null,
    val form: RequirementFormUi? = null,
    val notesEditor: NotesEditorUi? = null,
)

@HiltViewModel
class AgentLeadsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AgentLeadsUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<AgentLeadsUi>> = _uiState.asStateFlow()

    /** Matches for the currently expanded lead live in Content state. */
    private var expandedMatchesLeadId: String? = null

    init {
        analytics.track(AnalyticsEvent.ScreenView("agent_leads"))
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        expandedMatchesLeadId = null
        viewModelScope.launch {
            _uiState.value = when (val result = agentRepository.leads()) {
                is AppResult.Success -> UiState.Content(AgentLeadsUi(leads = result.data))
                AppResult.Empty -> UiState.Content(AgentLeadsUi())
                is AppResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    fun consumeMessage() {
        update { it.copy(message = null) }
    }

    fun toggleExpanded(leadId: String) {
        val current = contentOrNull() ?: return
        if (current.busyIds.isNotEmpty()) return
        if (current.expandedId == leadId) {
            update {
                it.copy(expandedId = null, matches = emptyList(), matchesLoading = false)
            }
            expandedMatchesLeadId = null
            return
        }
        update {
            it.copy(expandedId = leadId, matches = emptyList(), matchesLoading = true)
        }
        expandedMatchesLeadId = leadId
        loadMatches(leadId)
    }

    fun setStage(leadId: String, stage: String) {
        updateLead(leadId = leadId, stage = stage, priority = null, notes = null)
    }

    fun setPriority(leadId: String, priority: Int) {
        if (priority !in 1..5) return
        updateLead(leadId = leadId, stage = null, priority = priority, notes = null)
    }

    fun openNotesEditor(leadId: String) {
        val lead = contentOrNull()?.leads?.find { it.id == leadId } ?: return
        update { it.copy(notesEditor = NotesEditorUi(leadId = leadId, text = lead.notes.orEmpty())) }
    }

    fun closeNotesEditor() {
        update { it.copy(notesEditor = null) }
    }

    fun setNotesText(text: String) {
        update { state ->
            state.copy(notesEditor = state.notesEditor?.copy(text = text))
        }
    }

    fun saveNotes() {
        val state = contentOrNull() ?: return
        val editor = state.notesEditor ?: return
        if (editor.busy) return
        update { it.copy(notesEditor = editor.copy(busy = true, error = null)) }
        viewModelScope.launch {
            when (agentRepository.updateLead(leadId = editor.leadId, notes = editor.text.take(4000))) {
                is AppResult.Success, AppResult.Empty -> {
                    update {
                        it.copy(
                            notesEditor = null,
                            message = "یادداشت ذخیره شد",
                        )
                    }
                    patchLocalLead(editor.leadId) { lead -> lead.copy(notes = editor.text.take(4000)) }
                }
                is AppResult.Failure -> {
                    update { it.copy(notesEditor = editor.copy(busy = false, error = "ذخیره یادداشت انجام نشد")) }
                }
            }
        }
    }

    fun openRequirementForm(leadId: String) {
        val current = contentOrNull() ?: return
        val lead = current.leads.find { it.id == leadId } ?: return
        if (current.form?.busy == true) return
        update { it.copy(form = null) }
        viewModelScope.launch {
            when (val result = agentRepository.requirementForLead(leadId)) {
                is AppResult.Success -> update {
                    it.copy(form = formFromRequirement(leadId, result.data))
                }
                AppResult.Empty -> update {
                    it.copy(form = RequirementFormUi(leadId = leadId, dealType = lead.dealType ?: DealType.SALE))
                }
                is AppResult.Failure -> update {
                    it.copy(message = "نیاز خریدار بارگذاری نشد")
                }
            }
        }
    }

    fun closeRequirementForm() {
        update { it.copy(form = null) }
    }

    fun updateForm(transform: (RequirementFormUi) -> RequirementFormUi) {
        update { state ->
            state.copy(form = state.form?.let(transform))
        }
    }

    fun saveRequirement() {
        val form = contentOrNull()?.form ?: return
        if (form.busy) return

        val budgetMin = form.budgetMin.toAsciiLongOrNull()
        val budgetMax = form.budgetMax.toAsciiLongOrNull()
        val areaMin = form.areaMin.toAsciiIntOrNull()
        val areaMax = form.areaMax.toAsciiIntOrNull()

        val validationError = when {
            form.budgetMin.isNotBlank() && budgetMin == null -> "حداقل بودجه فقط عدد است"
            form.budgetMax.isNotBlank() && budgetMax == null -> "حداکثر بودجه فقط عدد است"
            budgetMin != null && budgetMax != null && budgetMin > budgetMax -> "حداقل بودجه بیشتر از حداکثر است"
            form.areaMin.isNotBlank() && (areaMin == null || areaMin <= 0) -> "حداقل متراژ باید عدد مثبت باشد"
            form.areaMax.isNotBlank() && (areaMax == null || areaMax <= 0) -> "حداکثر متراژ باید عدد مثبت باشد"
            areaMin != null && areaMax != null && areaMin > areaMax -> "حداقل متراژ بیشتر از حداکثر است"
            else -> null
        }
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }

        val cities = form.citiesText
            .split('،', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val features = buildJsonObject {
            form.features.forEach { key ->
                put(key, JsonPrimitive(true))
            }
        }

        val input = RequirementInput(
            leadId = form.leadId,
            dealType = form.dealType,
            budgetMinRial = budgetMin,
            budgetMaxRial = budgetMax,
            areaMin = areaMin,
            areaMax = areaMax,
            bedrooms = form.bedrooms.sorted().takeIf { it.isNotEmpty() },
            cities = cities.takeIf { it.isNotEmpty() },
            features = features.takeIf { it.isNotEmpty() },
        )

        updateForm { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val saved = agentRepository.upsertRequirement(input)) {
                is AppResult.Success, AppResult.Empty -> {
                    val refreshed = agentRepository.refreshMatches(form.leadId)
                    val message = when (refreshed) {
                        is AppResult.Success ->
                            "نیاز خریدار ثبت شد؛ ${refreshed.data} تطبیق محاسبه شد"
                        AppResult.Empty -> "نیاز خریدار ثبت شد"
                        is AppResult.Failure -> "نیاز ثبت شد؛ محاسبه تطبیق انجام نشد"
                    }
                    update { it.copy(form = null, message = message) }
                    if (form.leadId == expandedMatchesLeadId) {
                        loadMatches(form.leadId)
                    }
                }
                is AppResult.Failure -> {
                    val msg = if (saved.error is AppError.Client) {
                        "ثبت نیاز انجام نشد؛ ورودی‌ها را بررسی کنید"
                    } else {
                        "ثبت نیاز انجام نشد"
                    }
                    updateForm { it.copy(busy = false, error = msg) }
                }
            }
        }
    }

    fun refreshMatches(leadId: String) {
        val state = contentOrNull() ?: return
        if (state.matchesLoading) return
        update { it.copy(matchesLoading = true, message = null) }
        viewModelScope.launch {
            when (val result = agentRepository.refreshMatches(leadId)) {
                is AppResult.Success, AppResult.Empty -> loadMatches(leadId)
                is AppResult.Failure -> update {
                    it.copy(
                        matchesLoading = false,
                        message = "محاسبه تطبیق انجام نشد",
                    )
                }
            }
        }
    }

    private fun loadMatches(leadId: String) {
        viewModelScope.launch {
            val matches = when (val result = agentRepository.leadMatches(leadId)) {
                is AppResult.Success -> result.data
                else -> emptyList()
            }
            val state = contentOrNull() ?: return@launch
            if (state.expandedId != leadId) return@launch
            update { it.copy(matches = matches, matchesLoading = false) }
        }
    }

    private fun updateLead(leadId: String, stage: String?, priority: Int?, notes: String?) {
        val before = contentOrNull() ?: return
        if (leadId in before.busyIds) return
        val previous = before.leads.find { it.id == leadId } ?: return
        update { it.copy(busyIds = it.busyIds + leadId, message = null) }
        viewModelScope.launch {
            when (agentRepository.updateLead(leadId, stage, priority, notes)) {
                is AppResult.Success, AppResult.Empty -> {
                    patchLocalLead(leadId) { lead ->
                        lead.copy(
                            stage = stage ?: lead.stage,
                            priority = priority ?: lead.priority,
                            notes = notes ?: lead.notes,
                        )
                    }
                    update { it.copy(busyIds = it.busyIds - leadId) }
                }
                is AppResult.Failure -> {
                    patchLocalLead(leadId) { previous }
                    update {
                        it.copy(
                            busyIds = it.busyIds - leadId,
                            message = "تغییر انجام نشد؛ دوباره تلاش کنید",
                        )
                    }
                }
            }
        }
    }

    private fun patchLocalLead(leadId: String, transform: (AgentLead) -> AgentLead) {
        update { state ->
            state.copy(
                leads = state.leads.map { lead ->
                    if (lead.id == leadId) transform(lead) else lead
                },
            )
        }
    }

    private fun contentOrNull(): AgentLeadsUi? = (_uiState.value as? UiState.Content)?.data

    private fun update(transform: (AgentLeadsUi) -> AgentLeadsUi) {
        val state = _uiState.value
        if (state is UiState.Content) {
            _uiState.value = UiState.Content(transform(state.data))
        }
    }

    private fun formFromRequirement(
        leadId: String,
        requirement: BuyerRequirement,
    ) = RequirementFormUi(
        leadId = leadId,
        dealType = requirement.dealType,
        budgetMin = requirement.budgetMinRial?.toString().orEmpty(),
        budgetMax = requirement.budgetMaxRial?.toString().orEmpty(),
        areaMin = requirement.areaMin?.toString().orEmpty(),
        areaMax = requirement.areaMax?.toString().orEmpty(),
        bedrooms = requirement.bedrooms.orEmpty().toSet(),
        citiesText = requirement.cities.orEmpty().joinToString("، "),
        features = requirement.features
            ?.filterValues { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" }
            ?.keys
            .orEmpty()
            .toSet(),
)
}
