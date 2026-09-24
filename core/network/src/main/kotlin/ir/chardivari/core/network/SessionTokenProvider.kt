package ir.chardivari.core.network

/**
 * Implemented by core:auth; provided into the Hilt graph from the app.
 * Keeps network free of a hard dependency on the auth module.
 */
interface SessionTokenProvider {
    /** Current access token, or null when logged out. */
    fun accessToken(): String?
}
