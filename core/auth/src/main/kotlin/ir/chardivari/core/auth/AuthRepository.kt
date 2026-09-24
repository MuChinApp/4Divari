package ir.chardivari.core.auth

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.ErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase GoTrue phone OTP endpoints (auth/v1).
 * Absolute [Url] avoids Retrofit base-url trailing-slash coupling with AppConfig.
 */
interface AuthApi {
    @POST
    suspend fun sendOtp(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: OtpRequest,
    ): Response<OtpSentResponse>

    @POST
    suspend fun verifyOtp(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: VerifyOtpRequest,
    ): Response<SessionResponse>

    @POST
    suspend fun refreshSession(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: RefreshRequest,
    ): Response<SessionResponse>

    @POST
    suspend fun signOut(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
    ): Response<Unit>
}

/**
 * Real phone-OTP authentication against Supabase GoTrue.
 *
 * Prototype Trap compliance:
 * - No hard-coded OTP, no fabricated session tokens.
 * - Missing SUPABASE_URL / anon key fails closed with a configuration error
 *   instead of inventing a successful login.
 */
interface AuthRepository {
    val session: Flow<AuthSession?>

    suspend fun sendOtp(phoneE164: String): AppResult<Unit>

    suspend fun verifyOtp(phoneE164: String, code: String): AppResult<AuthSession>

    suspend fun refresh(): AppResult<AuthSession>

    suspend fun signOut(): AppResult<Unit>
}

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val config: AppConfig,
    private val api: AuthApi,
    private val store: SessionStore,
) : AuthRepository {

    override val session: Flow<AuthSession?> get() = store.session

    override suspend fun sendOtp(phoneE164: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            when (val result = call { apiKey ->
                api.sendOtp(authUrl("otp"), apiKey, OtpRequest(phone = phoneE164))
            }) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
                AppResult.Empty -> AppResult.Empty
            }
        }

    override suspend fun verifyOtp(
        phoneE164: String,
        code: String,
    ): AppResult<AuthSession> = withContext(Dispatchers.IO) {
        val trimmed = code.trim()
        if (trimmed.length !in 4..10 || !trimmed.all { it.isDigit() }) {
            return@withContext AppResult.Failure(
                AppError.Validation(mapOf("code" to "کد تایید نامعتبر است")),
            )
        }
        val result = call { apiKey ->
            api.verifyOtp(
                url = authUrl("verify"),
                apiKey = apiKey,
                body = VerifyOtpRequest(type = "sms", phone = phoneE164, token = trimmed),
            )
        }
        when (result) {
            is AppResult.Success -> {
                val body = result.data
                    ?: return@withContext AppResult.Failure(AppError.Serialization)
                val now = System.currentTimeMillis() / 1000
                val session = runCatching {
                    AuthSession.fromResponse(body, now)
                }.getOrElse { t ->
                    return@withContext AppResult.Failure(AppError.Serialization, t)
                }
                store.write(session)
                AppResult.Success(session)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun refresh(): AppResult<AuthSession> = withContext(Dispatchers.IO) {
        val current = store.currentBlocking()
            ?: return@withContext AppResult.Failure(AppError.Unauthorized)
        val result = call { apiKey ->
            api.refreshSession(
                url = authUrl("token?grant_type=refresh_token"),
                apiKey = apiKey,
                authorization = "Bearer ${current.accessToken}",
                body = RefreshRequest(refreshToken = current.refreshToken),
            )
        }
        when (result) {
            is AppResult.Success -> {
                val body = result.data
                    ?: return@withContext AppResult.Failure(AppError.Serialization)
                val session = AuthSession.fromResponse(
                    body,
                    System.currentTimeMillis() / 1000,
                )
                store.write(session)
                AppResult.Success(session)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun signOut(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val current = store.currentBlocking()
        store.clear()
        if (current == null) return@withContext AppResult.Success(Unit)
        // Best-effort remote revoke; local session is already gone.
        runCatching {
            call { apiKey ->
                api.signOut(
                    url = authUrl("logout"),
                    apiKey = apiKey,
                    authorization = "Bearer ${current.accessToken}",
                )
            }
        }
        AppResult.Success(Unit)
    }

    override fun accessToken(): String? = store.currentBlocking()?.accessToken

    private fun requireAnonKey(): String? =
        config.supabaseAnonKey?.takeIf { it.isNotBlank() }

    private fun baseUrl(): String? =
        config.supabaseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }

    private fun authUrl(path: String): String {
        val base = baseUrl() ?: error("Supabase URL not configured")
        return "$base/auth/v1/$path"
    }

    private suspend fun <T> call(
        request: suspend (apiKey: String) -> Response<T>,
    ): AppResult<T> {
        val key = requireAnonKey()
        if (baseUrl() == null || key == null) {
            return AppResult.Failure(
                AppError.Client(
                    code = 0,
                    serverMessage = "Backend auth is not configured for this build",
                ),
            )
        }
        return try {
            ErrorMapper.responseToResult(request(key))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppResult.Failure(ErrorMapper.fromThrowable(t), t)
        }
    }
}
