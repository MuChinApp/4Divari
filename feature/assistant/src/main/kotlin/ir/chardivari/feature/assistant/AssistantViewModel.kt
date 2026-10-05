package ir.chardivari.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.chardivari.core.ai.AssistantEngine
import ir.chardivari.core.ai.AssistantTurn
import ir.chardivari.core.ai.PriorTurn
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AssistantTurnUi {
    data class User(val text: String) : AssistantTurnUi
    data class Answer(val turn: AssistantTurn) : AssistantTurnUi
}

enum class AssistantErrorKind { LOGIN, OFFLINE, NOT_CONFIGURED, UNKNOWN }

data class AssistantSendError(
    val kind: AssistantErrorKind,
    val messageFa: String,
)

data class AssistantUiModel(
    val turns: List<AssistantTurnUi> = emptyList(),
    val draft: String = "",
    val busy: Boolean = false,
    val error: AssistantSendError? = null,
)

/**
 * Conversation session state — in-memory by design (rotation-safe); the
 * assistant produces decision support, not a persisted correspondence.
 * Failures restore the draft so no user text is ever lost.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val engine: AssistantEngine,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiModel())
    val uiState: StateFlow<AssistantUiModel> = _uiState.asStateFlow()

    private val history = mutableListOf<PriorTurn>()
    private var retryText: String? = null

    init {
        analytics.track(AnalyticsEvent.ScreenView("assistant"))
    }

    fun onDraftChange(value: String) {
        retryText = null
        _uiState.value = _uiState.value.copy(draft = value, error = null)
    }

    fun send() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty()) return
        submit(text)
    }

    fun retry() {
        val text = retryText ?: return
        submit(text)
    }

    fun dismissError() {
        retryText = null
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun submit(text: String) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(draft = "", busy = true, error = null)
        analytics.track(AnalyticsEvent.AssistantQuestionAsked(messageLength = text.length))
        viewModelScope.launch {
            when (val result = engine.ask(history.toList(), text)) {
                is AppResult.Success -> onAnswer(text, result.data)
                AppResult.Empty -> onFailure(text, AppError.Unexpected)
                is AppResult.Failure -> onFailure(text, result.error)
            }
        }
    }

    private fun onAnswer(userText: String, turn: AssistantTurn) {
        history += PriorTurn(userText = userText, assistantText = turn.text)
        turn.toolsUsed.forEach { tool ->
            analytics.track(AnalyticsEvent.AssistantToolUsed(tool = tool))
        }
        if (turn.fairnessNotice != null) {
            analytics.track(AnalyticsEvent.AssistantFairnessBlocked(matchedCriteria = 1))
        }
        _uiState.value = _uiState.value.copy(
            turns = _uiState.value.turns +
                AssistantTurnUi.User(userText) +
                AssistantTurnUi.Answer(turn),
            busy = false,
            error = null,
        )
        retryText = null
    }

    private fun onFailure(userText: String, error: AppError) {
        analytics.track(
            AnalyticsEvent.AssistantFailed(reason = error.javaClass.simpleName),
        )
        retryText = userText
        _uiState.value = _uiState.value.copy(
            busy = false,
            error = mapError(error),
            draft = userText,
        )
    }

    private fun mapError(error: AppError): AssistantSendError = when (error) {
        AppError.Unauthorized -> AssistantSendError(
            kind = AssistantErrorKind.LOGIN,
            messageFa = "برای استفاده از دستیار وارد شوید.",
        )
        AppError.Offline -> AssistantSendError(
            kind = AssistantErrorKind.OFFLINE,
            messageFa = "اتصال اینترنت برقرار نیست؛ دوباره تلاش کنید.",
        )
        is AppError.Client -> {
            if (error.serverMessage == "AI assistant is not configured for this build") {
                AssistantSendError(
                    kind = AssistantErrorKind.NOT_CONFIGURED,
                    messageFa = "دستیار در این ساخت فعال نیست.",
                )
            } else {
                AssistantSendError(
                    kind = AssistantErrorKind.UNKNOWN,
                    messageFa = "خطا در دریافت پاسخ؛ دوباره تلاش کنید.",
                )
            }
        }
        else -> AssistantSendError(
            kind = AssistantErrorKind.UNKNOWN,
            messageFa = "خطا در دریافت پاسخ؛ دوباره تلاش کنید.",
        )
    }
}
