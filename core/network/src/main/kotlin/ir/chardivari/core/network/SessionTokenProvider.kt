package ir.chardivari.core.network

/**
 * Implemented by core:auth; provided into the Hilt graph from the app.
 * Keeps network free of a hard dependency on the auth module.
 */
interface SessionTokenProvider {
    /** Current access token, or null when logged out. */
    fun accessToken(): String?

    /**
     * Auth subject (GoTrue user id / JWT `sub`), or null when logged out.
     * Must match `public.users.id` for RLS-scoped writes (favorites, etc.).
     */
    fun currentUserId(): String?
}
