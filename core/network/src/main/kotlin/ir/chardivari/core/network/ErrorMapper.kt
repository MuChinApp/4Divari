package ir.chardivari.core.network

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps transport-level failures into the domain [AppError] taxonomy.
 * Kept free of Android dependencies so it is unit-testable on the JVM.
 */
object ErrorMapper {

    fun fromThrowable(t: Throwable): AppError = when (t) {
        is UnknownHostException -> AppError.Offline
        is SocketTimeoutException -> AppError.Offline
        is IOException -> AppError.Offline
        is SerializationException -> AppError.Serialization
        is retrofit2.serialization.SerializationException -> AppError.Serialization
        is HttpException -> fromHttpCode(t.code(), t.message())
        else -> AppError.Unexpected
    }

    fun fromHttpCode(code: Int, serverMessage: String? = null): AppError = when (code) {
        401, 403 -> AppError.Unauthorized
        408, 429 -> AppError.RateLimited
        in 400..499 -> AppError.Client(code, serverMessage)
        in 500..599 -> AppError.Server
        else -> AppError.Unexpected
    }

    fun <T> responseToResult(response: Response<T>): AppResult<T> {
        if (response.isSuccessful) {
            val body = response.body()
            return if (body == null) {
                AppResult.Empty
            } else {
                AppResult.Success(body)
            }
        }
        return AppResult.Failure(
            error = fromHttpCode(response.code(), response.errorBody().safeMessage()),
        )
    }

    suspend fun <T> runCatchingApi(block: suspend () -> Response<T>): AppResult<T> =
        try {
            responseToResult(block())
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppResult.Failure(fromThrowable(t), t)
        }

    private fun ResponseBody?.safeMessage(): String? = try {
        this?.string()?.take(200)
    } catch (_: Exception) {
        null
    }
}
