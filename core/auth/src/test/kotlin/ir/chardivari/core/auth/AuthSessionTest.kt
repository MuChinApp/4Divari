package ir.chardivari.core.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthSessionTest {

    @Test
    fun `fromResponse uses expires_in when expires_at missing`() {
        val session = AuthSession.fromResponse(
            response = SessionResponse(
                accessToken = "a",
                refreshToken = "r",
                expiresIn = 3600,
                user = AuthUser(id = "u1", phone = "+989121111111"),
            ),
            nowEpochSeconds = 1_000_000L,
        )
        assertThat(session.userId).isEqualTo("u1")
        assertThat(session.accessToken).isEqualTo("a")
        assertThat(session.expiresAtEpochSeconds).isEqualTo(1_000_000L + 3600L)
        assertThat(session.phoneE164).isEqualTo("+989121111111")
    }

    @Test
    fun `fromResponse without user id fails closed`() {
        assertThrows(IllegalStateException::class.java) {
            AuthSession.fromResponse(
                response = SessionResponse(accessToken = "a", refreshToken = "r"),
                nowEpochSeconds = 0L,
            )
        }
    }

    @Test
    fun `isExpired respects skew`() {
        val session = AuthSession(
            userId = "u",
            accessToken = "a",
            refreshToken = "r",
            expiresAtEpochSeconds = 1000L,
        )
        assertThat(session.isExpired(nowEpochSeconds = 900L)).isFalse()
        assertThat(session.isExpired(nowEpochSeconds = 950L)).isTrue()
        assertThat(session.isExpired(nowEpochSeconds = 1001L)).isTrue()
    }

    @Test
    fun `expiresAt 0 means never auto-expire locally`() {
        val session = AuthSession(
            userId = "u",
            accessToken = "a",
            refreshToken = "r",
            expiresAtEpochSeconds = 0L,
        )
        assertThat(session.isExpired(nowEpochSeconds = 9_999_999L)).isFalse()
    }
}
