package ir.chardivari.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.chardivari.core.network.SessionTokenProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory + disk-persisted session. Single user per install for MVP. */
@kotlinx.serialization.Serializable
data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds; 0 = unknown (treated as not expired until refresh fails). */
    val expiresAtEpochSeconds: Long,
    val phoneE164: String? = null,
) {
    fun isExpired(nowEpochSeconds: Long, skewSeconds: Long = 60): Boolean =
        expiresAtEpochSeconds > 0 && nowEpochSeconds >= (expiresAtEpochSeconds - skewSeconds)

    companion object {
        fun fromResponse(response: SessionResponse, nowEpochSeconds: Long): AuthSession {
            val userId = response.user?.id
                ?: throw IllegalStateException("Session response missing user id")
            val expiresAt = response.expiresAt
                ?: response.expiresIn?.let { nowEpochSeconds + it }
                ?: (nowEpochSeconds + TimeUnit.HOURS.toSeconds(1))
            return AuthSession(
                userId = userId,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAtEpochSeconds = expiresAt,
                phoneE164 = response.user?.phone,
            )
        }
    }
}

interface SessionStore {
    val session: Flow<AuthSession?>
    suspend fun write(session: AuthSession)
    suspend fun clear()
    /** Synchronous snapshot for interceptors on the OkHttp thread. */
    fun currentBlocking(): AuthSession?
}

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

@Singleton
class DataStoreSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : SessionStore {

    private val keySession = stringPreferencesKey("session_json")

    private val _session = MutableStateFlow<AuthSession?>(null)

    init {
        // Seed blocking cache from disk once.
        runBlocking {
            context.authDataStore.data
                .map { prefs -> prefs[keySession] }
                .collectOnce { raw ->
                    _session.value = raw?.let {
                        runCatching { json.decodeFromString<AuthSession>(it) }.getOrNull()
                    }
                }
        }
    }

    override val session: Flow<AuthSession?> = _session.asStateFlow()

    override suspend fun write(session: AuthSession) {
        withContext(Dispatchers.IO) {
            _session.value = session
            context.authDataStore.edit { prefs ->
                prefs[keySession] = json.encodeToString(session)
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            _session.value = null
            context.authDataStore.edit { it.remove(keySession) }
        }
    }

    override fun currentBlocking(): AuthSession? = _session.value

    private suspend fun <T> Flow<T>.collectOnce(action: suspend (T) -> Unit) {
        action(first())
    }
}

/**
 * Supplies Bearer tokens to core:network without creating a module cycle.
 * When no session exists, [accessToken] returns null (public requests only).
 */
@Singleton
class AuthSessionTokenProvider @Inject constructor(
    private val store: SessionStore,
) : SessionTokenProvider {
    override fun accessToken(): String? = store.currentBlocking()?.accessToken

    override fun currentUserId(): String? = store.currentBlocking()?.userId
}
