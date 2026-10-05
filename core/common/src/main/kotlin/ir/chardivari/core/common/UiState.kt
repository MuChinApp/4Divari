package ir.chardivari.core.common

/**
 * Standard async screen state machine.
 *
 * Every feature ViewModel exposes [UiState] — no ad-hoc nullable flags.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>

    data object Loading : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    /** Explicit empty — distinct from error so UX can invite first action. */
    data object Empty : UiState<Nothing>

    data class Error(
        val error: AppError,
        val canRetry: Boolean = true,
    ) : UiState<Nothing>
}

fun <T> AppResult<T>.toUiState(): UiState<T> = when (this) {
    is AppResult.Success -> UiState.Content(data)
    AppResult.Empty -> UiState.Empty
    is AppResult.Failure -> UiState.Error(
        error = error,
        canRetry = error is AppError.Offline ||
            error is AppError.Server ||
            error is AppError.Unexpected ||
            error is AppError.Serialization,
    )
}
