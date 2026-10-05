package ir.chardivari.core.network

import ir.chardivari.core.common.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for API calls from repositories.
 * Applies dispatcher hop + uniform error mapping.
 */
@Singleton
class SafeApi @Inject constructor(
    private val dispatchers: ir.chardivari.core.common.AppDispatchers,
) {
    suspend fun <T> call(block: suspend () -> Response<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                ErrorMapper.responseToResult(block())
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                AppResult.Failure(ErrorMapper.fromThrowable(t), t)
            }
        }
}
