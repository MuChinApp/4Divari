package ir.chardivari.core.common

/**
 * Actionable, user-facing error taxonomy.
 *
 * Rule: no screen may end with "Something went wrong".
 * Every [AppError] maps to a Persian message + a suggested recovery action.
 */
sealed interface AppError {
    /** No network / timeout / DNS — offer Retry + offline hint. */
    data object Offline : AppError

    /** Server responded 5xx or unreachable — offer Retry. */
    data object Server : AppError

    /** 4xx that is not auth — show message, no blind retry. */
    data class Client(val code: Int, val serverMessage: String? = null) : AppError

    /** 401/403 — trigger re-auth or permission explanation. */
    data object Unauthorized : AppError

    /** Local validation failure (e.g. empty required field). */
    data class Validation(val fieldErrors: Map<String, String> = emptyMap()) : AppError

    /** Parsing/serialization mismatch — bug signal, offer Retry + report. */
    data object Serialization : AppError

    /** Unexpected local failure. */
    data object Unexpected : AppError

    /** Rate limited (HTTP 429) — show wait guidance. */
    data object RateLimited : AppError
}
