package ir.chardivari.core.ai

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SafeApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM conversation seam. The production implementation talks to the
 * Supabase Edge Function `assistant`; tests script this interface.
 */
interface AiTransport {
    suspend fun chat(request: AssistantWireRequest): AppResult<AssistantWireResponse>
}

/** POST surface of the `assistant` edge function (absolute @Url, AuthApi pattern). */
interface AiApi {
    @POST
    suspend fun chat(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: AssistantWireRequest,
    ): Response<AssistantWireResponse>
}

fun aiConfigError(): AppResult.Failure = AppResult.Failure(
    AppError.Client(
        code = 0,
        serverMessage = "AI assistant is not configured for this build",
    ),
)

/**
 * Supabase Edge Functions transport — fail closed when the build has no
 * Supabase config (same anti-prototype contract as the marketplace repos).
 * The OkHttp stack in core:network attaches the session Bearer, so a
 * logged-out call surfaces as AppError.Unauthorized for a login prompt.
 */
@Singleton
class SupabaseAiTransport @Inject constructor(
    private val config: AppConfig,
    private val api: AiApi,
    private val safeApi: SafeApi,
) : AiTransport {

    override suspend fun chat(request: AssistantWireRequest): AppResult<AssistantWireResponse> {
        val base = config.supabaseUrl?.trimEnd('/')
        val key = config.supabaseAnonKey?.takeIf { it.isNotBlank() }
        if (base.isNullOrBlank() || key == null) {
            return aiConfigError()
        }
        val result = safeApi.call {
            api.chat(
                url = "$base/functions/v1/assistant",
                apiKey = key,
                body = request,
            )
        }
        return when (result) {
            is AppResult.Success -> {
                if (result.data?.message == null) {
                    AppResult.Failure(
                        AppError.Client(code = 0, serverMessage = "assistant_empty_response"),
                    )
                } else {
                    AppResult.Success(result.data)
                }
            }
            AppResult.Empty -> AppResult.Failure(AppError.Unexpected)
            is AppResult.Failure -> result
        }
    }
}
