package ir.chardivari.core.common

/**
 * Unified result type for domain/data layers.
 *
 * UI never talks to raw network/database types — everything maps to [AppResult]
 * so Loading / Success / Empty / Error / Offline can be rendered consistently.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    /** Success with no content — drives Empty states, not Error. */
    data object Empty : AppResult<Nothing>

    data class Failure(
        val error: AppError,
        val cause: Throwable? = null,
    ) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    AppResult.Empty -> AppResult.Empty
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/** Fold into a single value for ViewModel state mapping. */
inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onEmpty: () -> R,
    onFailure: (AppError) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    AppResult.Empty -> onEmpty()
    is AppResult.Failure -> onFailure(error)
}
