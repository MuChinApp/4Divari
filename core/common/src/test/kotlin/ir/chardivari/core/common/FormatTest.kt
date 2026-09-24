package ir.chardivari.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `persian digits conversion`() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", "1234567890".toPersianDigits())
        assertEquals("متراژ ۸۰", "متراژ 80".toPersianDigits())
    }

    @Test
    fun `price groups thousands with persian digits`() {
        assertEquals("۱۲٬۸۰۰٬۰۰۰٬۰۰۰", Format.price(12_800_000_000))
        assertEquals("۸۵۰٬۰۰۰٬۰۰۰", Format.price(850_000_000))
        assertEquals("۵", Format.price(5))
    }

    @Test
    fun `compact price in billions`() {
        assertEquals("۱۲٫۸ میلیارد", Format.priceCompact(12_800_000_000))
        assertEquals("۸ میلیارد", Format.priceCompact(8_000_000_000))
    }

    @Test
    fun `compact price in millions`() {
        assertEquals("۸۵۰ میلیون", Format.priceCompact(850_000_000))
    }

    @Test
    fun `price per sqm`() {
        assertEquals("۱۴۲٫۲ میلیون / متر", Format.pricePerSqm(12_800_000_000, 90))
        assertEquals(null, Format.pricePerSqm(12_800_000_000, 0))
    }

    @Test
    fun `bedroom labels`() {
        assertEquals("دو خواب", Format.bedrooms(2))
        assertEquals("بدون خواب", Format.bedrooms(0))
        assertEquals("۵ خواب", Format.bedrooms(5))
    }
}

class AppResultTest {

    @Test
    fun `map transforms success only`() {
        val r: AppResult<Int> = AppResult.Success(2)
        assertEquals(AppResult.Success(4), r.map { it * 2 })

        val f: AppResult<Int> = AppResult.Failure(AppError.Offline)
        assertEquals(f, f.map { it * 2 })
    }

    @Test
    fun `fold covers all branches`() {
        assertEquals("ok", AppResult.Success(1).fold({ "ok" }, { "empty" }, { "err" }))
        assertEquals("empty", AppResult.Empty.fold({ "ok" }, { "empty" }, { "err" }))
        assertEquals("err", AppResult.Failure(AppError.Server).fold({ "ok" }, { "empty" }, { "err" }))
    }

    @Test
    fun `result flags`() {
        assertTrue(AppResult.Success(1).isSuccess)
        assertTrue(AppResult.Failure(AppError.Unexpected).isFailure)
        assertFalse(AppResult.Empty.isSuccess)
    }
}

class UiStateTest {

    @Test
    fun `failure to ui state retryability`() {
        val offline = AppResult.Failure<Int>(AppError.Offline).toUiState()
        assertTrue((offline as UiState.Error).canRetry)

        val validation = AppResult.Failure<Int>(AppError.Validation()).toUiState()
        assertFalse((validation as UiState.Error).canRetry)

        val success = AppResult.Success(1).toUiState()
        assertTrue(success is UiState.Content)
    }
}
