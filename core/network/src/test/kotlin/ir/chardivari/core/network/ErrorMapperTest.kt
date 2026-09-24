package ir.chardivari.core.network

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `unknown host maps to offline`() {
        assertEquals(AppError.Offline, ErrorMapper.fromThrowable(UnknownHostException("no net")))
    }

    @Test
    fun `io exception maps to offline`() {
        assertEquals(AppError.Offline, ErrorMapper.fromThrowable(IOException("reset")))
    }

    @Test
    fun `http 401 maps to unauthorized`() {
        assertEquals(AppError.Unauthorized, ErrorMapper.fromHttpCode(401))
    }

    @Test
    fun `http 429 maps to rate limited`() {
        assertEquals(AppError.RateLimited, ErrorMapper.fromHttpCode(429))
    }

    @Test
    fun `http 404 maps to client with code`() {
        val e = ErrorMapper.fromHttpCode(404)
        assertTrue(e is AppError.Client)
        assertEquals(404, (e as AppError.Client).code)
    }

    @Test
    fun `http 500 maps to server`() {
        assertEquals(AppError.Server, ErrorMapper.fromHttpCode(500))
    }

    @Test
    fun `unknown throwable maps to unexpected`() {
        assertEquals(AppError.Unexpected, ErrorMapper.fromThrowable(IllegalStateException()))
    }
}

class SafeApiShapeTest {
    // SafeApi requires Retrofit Response; full integration covered in Phase 2
    // when real endpoints exist. ErrorMapper (above) is the decision core.

    @Test
    fun `failure keeps error identity`() {
        val r: AppResult<String> = AppResult.Failure(AppError.Server)
        assertTrue(r.isFailure)
        assertEquals(AppError.Server, (r as AppResult.Failure).error)
    }
}
