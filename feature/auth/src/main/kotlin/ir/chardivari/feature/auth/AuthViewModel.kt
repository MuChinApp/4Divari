package ir.chardivari.feature.auth

import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.auth.AuthRepository
import ir.chardivari.core.auth.AuthSession
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phone → OTP → session. Every state is honest:
 * - Idle until the user acts
 * - OtpSent only after a real GoTrue 2xx
 * - Error surfaces the mapped [AppError]; no fake success
 */
data class AuthUiModel(
    val step: AuthStep = AuthStep.Phone,
    val phoneE164: String = "",
    val phoneValid: Boolean = false,
    val loading: Boolean = false,
)

enum class AuthStep { Phone, Otp }

sealed interface AuthEvent {
    data class OtpSent(val phoneE164: String) : AuthEvent
    data class Verified(val session: AuthSession) : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiModel())
    val uiState: StateFlow<AuthUiModel> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun onPhoneChanged(raw: String) {
        _error.value = null
        _uiState.update {
            it.copy(
                phoneE164 = raw,
                phoneValid = IranianPhone.isValidIranMobile(raw),
            )
        }
    }

    fun sendOtp() {
        val state = _uiState.value
        if (!state.phoneValid || state.loading) return
        val e164 = IranianPhone.normalizeToE164(state.phoneE164) ?: run {
            _error.value = AppError.Validation(mapOf("phone" to "شماره موبایل معتبر نیست"))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            when (val result = repository.sendOtp(e164)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(loading = false, step = AuthStep.Otp, phoneE164 = e164)
                    }
                    _error.value = null
                    _events.tryEmit(AuthEvent.OtpSent(e164))
                    analytics.track(AnalyticsEvent.AuthOtpRequested)
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false) }
                    _error.value = result.error
                    analytics.track(
                        AnalyticsEvent.AuthLoginFailed(result.error::class.simpleName ?: "unknown"),
                    )
                }
                AppResult.Empty -> {
                    _uiState.update { it.copy(loading = false, step = AuthStep.Otp) }
                    _events.tryEmit(AuthEvent.OtpSent(e164))
                    analytics.track(AnalyticsEvent.AuthOtpRequested)
                }
            }
        }
    }

    fun verifyOtp(code: String) {
        val trimmed = code.trim()
        if (trimmed.length !in 4..10 || !trimmed.all { it.isDigit() }) {
            _error.value = AppError.Validation(mapOf("code" to "کد تایید نامعتبر است"))
            return
        }
        val phone = _uiState.value.phoneE164
        if (_uiState.value.loading || phone.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            when (val result = repository.verifyOtp(phone, trimmed)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(loading = false) }
                    _error.value = null
                    _events.tryEmit(AuthEvent.Verified(result.data))
                    analytics.track(AnalyticsEvent.AuthLoginSucceeded)
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false) }
                    _error.value = result.error
                    analytics.track(
                        AnalyticsEvent.AuthLoginFailed(result.error::class.simpleName ?: "unknown"),
                    )
                }
                AppResult.Empty -> {
                    _uiState.update { it.copy(loading = false) }
                    _error.value = AppError.Serialization
                    analytics.track(AnalyticsEvent.AuthLoginFailed("Serialization"))
                }
            }
        }
    }

    fun backToPhone() {
        _error.value = null
        _uiState.update { it.copy(step = AuthStep.Phone, loading = false) }
    }
}
